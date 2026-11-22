package com.istiqamah.mac

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.istiqamah.app.core.Bn
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.time.ZonedDateTime

// =====================================================================
// ইস্তিকামাহ — ম্যাকের মেনু বার অ্যাপ
// মেনু বারে চাঁদ; ক্লিকে আজকের ওয়াক্ত, চিহ্ন দেওয়া, সেটিংস।
// ওয়াক্ত/জামাতে কোণে ছোট জানালা; শেষ ২০ মিনিটে পুরো পর্দা।
// =====================================================================

private fun FrameWindowScope.bringToFront() {
    runCatching {
        val d = Desktop.getDesktop()
        if (d.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) d.requestForeground(true)
    }
    window.toFront()
    window.requestFocus()
}

fun main() {
    System.setProperty("apple.awt.application.name", "Istiqamah")
    // ডকে আইকন নয় — শুধু মেনু বার (প্যাকেজে LSUIElement; ডেভেলপমেন্টে এটা)
    System.setProperty("apple.awt.UIElement", "true")
    application(exitProcessOnExit = true) { IstiqamahMac() }
}

@Composable
private fun ApplicationScope.IstiqamahMac() {
    val app = remember { MacApp() }

    LaunchedEffect(Unit) {
        while (true) {
            app.tick(ZonedDateTime.now())
            delay(15_000)
        }
    }

    val today = app.now.toLocalDate()
    Tray(
        icon = remember(app.pending) { CrescentPainter(dot = app.pending) },
        tooltip = "ইস্তিকামাহ — ${app.summary}",
        onAction = { app.showToday = true },
        menu = {
            if (app.summary.isNotBlank()) Item(app.summary, enabled = false, onClick = {})
            Separator()
            for (w in app.windows[today].orEmpty()) {
                val st = app.status(today, w.prayer)
                val mark = when {
                    st != null -> "✓ "
                    w.contains(app.now) -> "● "
                    else -> "   "
                }
                val label = "$mark${w.prayer.bn}  ${Bn.time(w.start.toLocalTime())}" + (st?.let { " · ${statusLabel(it, app.settings.gender)}" } ?: "")
                val started = !app.now.isBefore(w.start)
                if (started) {
                    Menu(label) {
                        for (c in statusChoices(app.settings.gender)) {
                            Item(statusLabel(c, app.settings.gender), onClick = { app.mark(today, w.prayer, c) })
                        }
                        if (st != null) {
                            Separator()
                            Item("চিহ্ন মুছে দিন", onClick = { app.mark(today, w.prayer, null) })
                        }
                    }
                } else {
                    Item(label, enabled = false, onClick = {})
                }
            }
            Separator()
            Item("আজকের নামাজ…", onClick = { app.showToday = true })
            Item("সেটিংস…", onClick = { app.showSettings = true })
            Separator()
            Item("বন্ধ করুন", onClick = { exitApplication() })
        },
    )

    if (app.showToday) {
        Window(
            onCloseRequest = { app.showToday = false }, title = "আজকের নামাজ — ইস্তিকামাহ",
            state = rememberWindowState(size = DpSize(440.dp, 720.dp), position = WindowPosition(Alignment.Center)),
        ) {
            LaunchedEffect(Unit) { bringToFront() }
            MacTheme { TodayScreen(app) }
        }
    }

    if (app.showSettings) {
        Window(
            onCloseRequest = { app.showSettings = false }, title = "সেটিংস — ইস্তিকামাহ",
            state = rememberWindowState(size = DpSize(520.dp, 760.dp), position = WindowPosition(Alignment.Center)),
        ) {
            LaunchedEffect(Unit) { bringToFront() }
            MacTheme { SettingsScreen(app) }
        }
    }

    // কোণের ডাক — একসাথে সর্বোচ্চ ২টা, একটার নিচে আরেকটা
    app.calls.takeLast(2).forEachIndexed { i, call ->
        key(call.id) {
            CallWindow(app, call, index = i)
        }
    }

    app.fullScreen?.let { call ->
        key(call.id) {
            val b = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
            Window(
                onCloseRequest = { app.dismiss(call) }, title = "ইস্তিকামাহ",
                undecorated = true, alwaysOnTop = true, resizable = false,
                state = remember {
                    WindowState(position = WindowPosition(b.x.dp, b.y.dp), size = DpSize(b.width.dp, b.height.dp))
                },
            ) {
                LaunchedEffect(Unit) { bringToFront() }
                MacTheme { FullScreenCall(app, call) }
            }
        }
    }
}

@Composable
private fun CallWindow(app: MacApp, call: ActiveCall, index: Int) {
    val b = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val w = 400
    val h = 360
    Window(
        onCloseRequest = { app.dismiss(call) },
        title = call.reminder.title,
        undecorated = true, transparent = true, alwaysOnTop = true, resizable = false,
        state = remember(index) {
            WindowState(
                position = WindowPosition((b.x + b.width - w - 16).dp, (b.y + 12 + index * (h + 10)).dp),
                size = DpSize(w.dp, h.dp),
            )
        },
    ) {
        LaunchedEffect(Unit) { window.toFront() }
        MacTheme {
            Box(Modifier.fillMaxSize().padding(4.dp)) { CallCard(app, call) }
        }
    }
}
