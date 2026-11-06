package com.pacaembu.home

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import com.pacaembu.core.AppRepository
import com.pacaembu.core.BuiltIn
import com.pacaembu.core.BuiltInResolver

object Launch {
    /** Opens [key] with the zoom-from-icon transition. Returns false if nothing can open it. */
    fun open(c: Context, view: View?, key: String, bounds: Rect?): Boolean {
        val opts = if (view != null && bounds != null)
            ActivityOptions.makeScaleUpAnimation(view, bounds.left, bounds.top, bounds.width(), bounds.height()).toBundle()
        else null
        BuiltIn.byKey[key]?.let { b ->
            val i = BuiltInResolver.intent(c, b) ?: return false
            return runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts); true }.getOrDefault(false)
        }
        if (key.startsWith("app:")) {
            val cn = ComponentName.unflattenFromString(key.removePrefix("app:")) ?: return false
            AppRepository.launch(cn, bounds, opts)
            return true
        }
        return false
    }
}
