package com.pacaembu.apps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pacaembu.R
import com.pacaembu.core.Prefs
import com.pacaembu.ui.IosStatusBar
import com.pacaembu.ui.ShadowDarkUp
import com.pacaembu.ui.T
import com.pacaembu.ui.iosText
import com.pacaembu.ui.iosWindow
import com.pacaembu.ui.linen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherActivity : ComponentActivity() {
    val permSeq = mutableIntStateOf(0)
    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permSeq.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        setContent { WeatherApp(permSeq.intValue) }
    }
}

private data class Day(val date: String, val code: Int, val hi: Double, val lo: Double)
private data class Hour(val time: String, val code: Int, val temp: Double)
private data class Forecast(val city: String, val temp: Double, val code: Int, val isDay: Boolean, val days: List<Day>, val hours: List<Hour>)

private suspend fun fetch(c: Context): Forecast = withContext(Dispatchers.IO) {
    var lat = -23.5505; var lon = -46.6333; var city = "São Paulo"
    if (ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        val lm = c.getSystemService(LocationManager::class.java)
        val loc = lm.getProviders(true).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (loc != null) {
            lat = loc.latitude; lon = loc.longitude
            @Suppress("DEPRECATION")
            city = runCatching { Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } }.getOrNull() ?: "%.2f, %.2f".format(lat, lon)
        }
    }
    val unit = if (Prefs.fahrenheit.value) "&temperature_unit=fahrenheit" else ""
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day" +
        "&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=6$unit")
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 10000; conn.readTimeout = 10000
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val o = JSONObject(body)
    val cur = o.getJSONObject("current")
    val d = o.getJSONObject("daily")
    val days = (0 until d.getJSONArray("time").length()).map {
        Day(d.getJSONArray("time").getString(it), d.getJSONArray("weather_code").getInt(it),
            d.getJSONArray("temperature_2m_max").getDouble(it), d.getJSONArray("temperature_2m_min").getDouble(it))
    }
    val h = o.getJSONObject("hourly")
    val nowIso = cur.getString("time").take(13)
    val times = h.getJSONArray("time")
    val start = (0 until times.length()).firstOrNull { times.getString(it).take(13) >= nowIso } ?: 0
    val hours = (start until minOf(start + 12, times.length())).map {
        Hour(times.getString(it).substring(11, 13), h.getJSONArray("weather_code").getInt(it), h.getJSONArray("temperature_2m").getDouble(it))
    }
    Forecast(city, cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1, days, hours)
}

private fun glyphFor(code: Int, day: Boolean = true) = when (code) {
    0 -> if (day) R.drawable.ion_sunny else R.drawable.ion_moon
    1, 2 -> R.drawable.ion_partly_sunny
    3, 45, 48 -> R.drawable.ion_cloudy
    in 51..67, in 80..82 -> R.drawable.ion_rainy
    in 71..77, 85, 86 -> R.drawable.ion_snow
    in 95..99 -> R.drawable.ion_thunderstorm
    else -> R.drawable.ion_cloudy
}

private fun glyphTint(code: Int, day: Boolean) = if (code == 0 && day) Color(0xFFFFD43B) else Color.White

@Composable
private fun WeatherApp(permSeq: Int) {
    val c = LocalContext.current
    var data by remember { mutableStateOf<Forecast?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(permSeq, reload) {
        error = false
        runCatching { fetch(c) }.onSuccess {
            data = it
            Prefs.lastTemp.value = "${Math.round(it.temp)}°"
        }.onFailure { error = true }
    }
    Column(Modifier.fillMaxSize().linen()) {
        IosStatusBar()
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            val f = data
            val shape = RoundedCornerShape(12.dp)
            val sky = if (f?.isDay != false) listOf(Color(0xFF4FA3E6), Color(0xFF1C4FA0)) else listOf(Color(0xFF4A4A86), Color(0xFF171734))
            Column(
                Modifier.fillMaxSize().clip(shape).background(Brush.verticalGradient(sky)).border(1.dp, Color(0x66FFFFFF), shape)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (f == null) {
                    Spacer(Modifier.height(120.dp))
                    T(stringResource(if (error) R.string.weather_error else R.string.weather_loading), iosText(17, Color.White, FontWeight.Bold, ShadowDarkUp, TextAlign.Center))
                    if (error) T(stringResource(R.string.retry), iosText(17, Color(0xFFBFE0FF), FontWeight.Bold), Modifier.padding(16.dp).clickable { reload++ })
                } else {
                T(f.city, iosText(26, Color.White, FontWeight.Bold, ShadowDarkUp, TextAlign.Center), maxLines = 1)
                val today = f.days.firstOrNull()
                if (today != null) T("${stringResource(R.string.weather_hi)} ${Math.round(today.hi)}°  ${stringResource(R.string.weather_lo)} ${Math.round(today.lo)}°", iosText(15, Color.White, FontWeight.Bold, ShadowDarkUp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                    Image(painterResource(glyphFor(f.code, f.isDay)), null, Modifier.size(80.dp), colorFilter = ColorFilter.tint(glyphTint(f.code, f.isDay)))
                    Spacer(Modifier.width(12.dp))
                    T("${Math.round(f.temp)}°", iosText(76, Color.White, FontWeight.Light, ShadowDarkUp))
                }
                // hourly strip
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x33000000)).horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    f.hours.forEachIndexed { i, h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T(if (i == 0) stringResource(R.string.now) else h.time, iosText(13, Color.White, FontWeight.Bold))
                            Image(painterResource(glyphFor(h.code)), null, Modifier.padding(vertical = 4.dp).size(24.dp), colorFilter = ColorFilter.tint(glyphTint(h.code, true)))
                            T("${Math.round(h.temp)}°", iosText(15, Color.White, FontWeight.Bold))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayFmt = SimpleDateFormat("EEEE", Locale.getDefault())
                f.days.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val name = runCatching { dayFmt.format(inFmt.parse(d.date)!!) }.getOrDefault(d.date).replaceFirstChar { it.titlecase() }
                        T(name, iosText(17, Color.White, FontWeight.Bold, ShadowDarkUp), Modifier.weight(1f), maxLines = 1)
                        Image(painterResource(glyphFor(d.code)), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(glyphTint(d.code, true)))
                        T("${Math.round(d.hi)}°", iosText(17, Color.White, FontWeight.Bold, align = TextAlign.End), Modifier.width(52.dp))
                        T("${Math.round(d.lo)}°", iosText(17, Color(0xFFB7CCEA), FontWeight.Bold, align = TextAlign.End), Modifier.width(48.dp))
                    }
                }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("Open-Meteo", iosText(12, Color(0xFF9A9A9A)))
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable {
                    Prefs.fahrenheit.value = !Prefs.fahrenheit.value; reload++
                },
                contentAlignment = Alignment.Center,
            ) { T(if (Prefs.fahrenheit.value) "°F" else "°C", iosText(12, Color.White, FontWeight.Bold)) }
        }
    }
}
