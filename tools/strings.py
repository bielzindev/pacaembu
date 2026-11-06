#!/usr/bin/env python3
"""Writes values/strings.xml (en) and values-pt-rBR/strings.xml (pt) from one table.
usage: strings.py <res dir>"""
import sys, os
from xml.sax.saxutils import escape

S = {
 "app_name": ["Pacaembu", "Pacaembu"],
 "app_settings_launcher": ["Pacaembu Settings", "Ajustes do Pacaembu"],
 "app_messages": ["Messages", "Mensagens"], "app_calendar": ["Calendar", "Calendário"],
 "app_photos": ["Photos", "Fotos"], "app_camera": ["Camera", "Câmera"], "app_weather": ["Weather", "Tempo"],
 "app_clock": ["Clock", "Relógio"], "app_maps": ["Maps", "Mapas"], "app_files": ["Files", "Arquivos"],
 "app_notes": ["Notes", "Notas"], "app_contacts": ["Contacts", "Contatos"], "app_calculator": ["Calculator", "Calculadora"],
 "app_store": ["Store", "Loja"], "app_settings": ["Settings", "Ajustes"], "app_phone": ["Phone", "Telefone"],
 "app_mail": ["Mail", "Mail"], "app_browser": ["Browser", "Navegador"], "app_music": ["Music", "Música"],
 "back": ["Back", "Voltar"], "cancel": ["Cancel", "Cancelar"], "ok": ["OK", "OK"], "delete": ["Delete", "Apagar"],
 "reset": ["Reset", "Redefinir"], "retry": ["Try again", "Tentar de novo"], "search": ["Search", "Buscar"],
 "search_phone": ["Search Phone", "Buscar no Telefone"], "on": ["On", "Sim"], "off": ["Off", "Não"],
 "switch_on": ["ON", "I"], "switch_off": ["OFF", "O"], "now": ["Now", "Agora"],
 "today": ["Today", "Hoje"], "tomorrow": ["Tomorrow", "Amanhã"], "yesterday": ["Yesterday", "Ontem"],
 "folder_default": ["Folder", "Pasta"],
 "delete_app_title": ["Delete “%1$s”", "Apagar “%1$s”"],
 "delete_app_msg": ["Deleting this app will also delete all of its data.", "Apagar este app também apagará todos os seus dados."],
 "no_app_title": ["“%1$s” not available", "“%1$s” indisponível"],
 "no_app_msg": ["No app on this phone can open it yet. Install one from the Store.", "Nenhum app deste telefone abre isso ainda. Instale um pela Loja."],
 "nc_empty": ["No Notifications", "Nenhuma Notificação"],
 "nc_no_access": ["To show notifications here, allow Pacaembu to read them.", "Para mostrar notificações aqui, permita que o Pacaembu as leia."],
 "nc_allow": ["Allow Access", "Permitir Acesso"],
 "lock_channel": ["Lock screen", "Tela bloqueada"], "lock_running": ["Lock screen is on", "Tela bloqueada ativa"],
 "slide_to_unlock": ["slide to unlock", "deslize para desbloquear"],
 "set_airplane": ["Airplane Mode", "Modo Avião"], "set_wifi": ["Wi-Fi", "Wi-Fi"], "set_bluetooth": ["Bluetooth", "Bluetooth"],
 "set_notifications": ["Notifications", "Notificações"], "set_sounds": ["Sounds", "Sons"],
 "set_brightness": ["Brightness", "Brilho"], "set_wallpaper": ["Wallpaper", "Imagem de Fundo"],
 "set_home": ["Home Screen", "Tela de Início"], "set_icons": ["Icons", "Ícones"], "set_lock": ["Lock Screen", "Tela Bloqueada"],
 "set_default_launcher": ["Default Home App", "App de Início Padrão"], "set_battery": ["Battery", "Bateria"],
 "set_android": ["Android Settings", "Ajustes do Android"], "set_about": ["About", "Sobre"],
 "set_home_layout": ["Layout", "Organização"], "set_labels": ["Icon Names", "Nomes dos Ícones"],
 "set_hide_dup": ["Hide Duplicates", "Ocultar Duplicados"],
 "set_hide_dup_footer": ["Hides system apps already shown by a Pacaembu icon (for example the browser behind “Browser”).", "Oculta apps do sistema que já aparecem num ícone do Pacaembu (por exemplo o navegador por trás de “Navegador”)."],
 "set_reset_layout": ["Reset Home Screen Layout", "Redefinir Tela de Início"],
 "set_reset_msg": ["Your home screen will go back to the original layout. Folders will be removed.", "A tela de início volta à organização original. As pastas serão removidas."],
 "set_home_footer": ["Hold any icon to rearrange. Drop an icon on another to make a folder. Press Home to finish.", "Segure um ícone para reorganizar. Solte um ícone sobre outro para criar uma pasta. Pressione Início para terminar."],
 "set_icon_style": ["Style", "Estilo"], "set_gloss": ["Glossy Icons", "Ícones com Brilho"],
 "set_icon_pack": ["Icon Pack", "Pacote de Ícones"], "set_icon_pack_none": ["Pacaembu (built in)", "Pacaembu (embutido)"],
 "set_icon_pack_empty": ["No icon packs installed. Any pack made for ADW, Nova or similar launchers works here.", "Nenhum pacote de ícones instalado. Qualquer pacote feito para ADW, Nova ou launchers parecidos funciona aqui."],
 "set_icon_pack_footer": ["Apps the pack doesn't cover keep the iOS shape and shine.", "Apps que o pacote não cobre mantêm o formato e o brilho do iOS."],
 "set_find_packs": ["Find Icon Packs", "Procurar Pacotes de Ícones"],
 "set_icons_credit": ["Glyphs: Ionicons (MIT License) by Ionic.", "Glifos: Ionicons (Licença MIT), da Ionic."],
 "set_lock_enable": ["Pacaembu Lock Screen", "Tela Bloqueada Pacaembu"],
 "set_overlay": ["Display Over Apps", "Sobrepor a Outros Apps"],
 "set_lock_preview": ["Preview", "Pré-visualizar"],
 "set_lock_footer": ["Shown when the screen turns off, in front of the Android lock. Needs “Display over other apps”. Your PIN or fingerprint is still asked after sliding.", "Aparece quando a tela apaga, na frente do bloqueio do Android. Precisa de “Sobrepor a outros apps”. Seu PIN ou digital ainda é pedido depois de deslizar."],
 "about_name": ["Name", "Nome"], "about_version": ["Version", "Versão"], "about_android": ["Android", "Android"],
 "about_model": ["Model", "Modelo"], "about_crash": ["Error Report", "Relatório de Erros"],
 "about_no_crash": ["No errors", "Nenhum erro"], "about_clear": ["Clear Report", "Limpar Relatório"],
 "clock_world": ["World Clock", "Relógio Mundial"], "clock_alarm": ["Alarm", "Alarme"],
 "clock_stopwatch": ["Stopwatch", "Cronômetro"], "clock_timer": ["Timer", "Timer"],
 "clock_choose_city": ["Choose a City", "Escolha uma Cidade"],
 "clock_world_hint": ["Tap + to add a city. Hold a city to remove it.", "Toque em + para adicionar. Segure uma cidade para removê-la."],
 "alarm_next": ["Next alarm", "Próximo alarme"], "alarm_none": ["None", "Nenhum"], "alarm_show": ["All Alarms", "Todos os Alarmes"],
 "alarm_footer": ["Alarms are kept by the phone's alarm service so they ring even when Pacaembu is closed.", "Os alarmes ficam no serviço de alarmes do telefone, para tocarem mesmo com o Pacaembu fechado."],
 "sw_start": ["Start", "Iniciar"], "sw_stop": ["Stop", "Parar"], "sw_lap": ["Lap", "Volta"], "sw_reset": ["Reset", "Zerar"],
 "sw_lap_n": ["Lap %1$d", "Volta %1$d"],
 "timer_hours": ["hours", "horas"], "timer_min": ["min", "min"], "timer_cancel": ["Cancel", "Cancelar"],
 "timer_done": ["Timer Done", "Timer Concluído"],
 "timer_footer": ["The timer runs while Clock is open.", "O timer funciona enquanto o Relógio está aberto."],
 "weather_loading": ["Loading…", "Carregando…"], "weather_error": ["Couldn't load the weather.", "Não foi possível carregar o tempo."],
 "weather_hi": ["H", "Máx"], "weather_lo": ["L", "Mín"],
 "notes_title": ["Notes (%1$d)", "Notas (%1$d)"],
 "phone_recents": ["Recents", "Recentes"], "phone_contacts": ["Contacts", "Contatos"], "phone_keypad": ["Keypad", "Teclado"],
 "phone_call": ["Call", "Ligar"], "phone_need_perm": ["Pacaembu needs permission to show this.", "O Pacaembu precisa de permissão para mostrar isto."],
 "phone_no_recents": ["No Recents", "Nenhuma Recente"],
 "call_incoming": ["incoming", "recebida"], "call_outgoing": ["outgoing", "feita"], "call_missed": ["missed", "perdida"],
}

def esc(t):
    t = escape(t).replace("'", "\\'").replace('"', '\\"')
    return t

def write(path, idx):
    with open(path, "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for k, v in S.items():
            fmt = ' formatted="true"' if "%" in v[idx] else ""
            f.write(f'    <string name="{k}"{fmt}>{esc(v[idx])}</string>\n')
        f.write('</resources>\n')

res = sys.argv[1]
write(os.path.join(res, "values", "strings.xml"), 0)
write(os.path.join(res, "values-pt-rBR", "strings.xml"), 1)
