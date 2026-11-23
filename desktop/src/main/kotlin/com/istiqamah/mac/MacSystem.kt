package com.istiqamah.mac

import java.io.File

// =====================================================================
// ম্যাকের সাথে কথা বলা: নোটিফিকেশন, শব্দ, লগইনে চালু হওয়া
// কোনো শেল নেই — সব আর্গুমেন্ট আলাদা করে দেওয়া, তাই লেখার ভেতরের চিহ্নে কিছু ভাঙে না।
// =====================================================================

object MacSystem {
    val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

    private fun run(vararg cmd: String) {
        runCatching { ProcessBuilder(*cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start() }
    }

    /** ম্যাকের নোটিফিকেশন সেন্টারে ছোট ব্যানার */
    fun banner(title: String, body: String, sound: Boolean) {
        if (!isMac) return
        val script = if (sound) "display notification (item 2 of argv) with title \"ইস্তিকামাহ\" subtitle (item 1 of argv) sound name \"Glass\""
        else "display notification (item 2 of argv) with title \"ইস্তিকামাহ\" subtitle (item 1 of argv)"
        run("/usr/bin/osascript", "-e", "on run argv", "-e", script, "-e", "end run", title, body)
    }

    /** কতক্ষণ কিবোর্ড-মাউস ছোঁয়া হয়নি (সেকেন্ড) — macOS-এর ioreg থেকে; না পারলে 0 */
    fun idleSeconds(): Long {
        if (!isMac) return 0
        return runCatching {
            val p = ProcessBuilder("/usr/sbin/ioreg", "-c", "IOHIDSystem", "-d", "4").redirectErrorStream(true).start()
            val line = p.inputStream.bufferedReader().useLines { seq -> seq.firstOrNull { it.contains("\"HIDIdleTime\"") } }
            p.waitFor()
            val ns = line?.substringAfter('=')?.trim()?.toLongOrNull() ?: 0L
            ns / 1_000_000_000L
        }.getOrDefault(0L)
    }

    fun chime(strong: Boolean = false) {
        if (!isMac) return
        val f = if (strong) "/System/Library/Sounds/Submarine.aiff" else "/System/Library/Sounds/Glass.aiff"
        run("/usr/bin/afplay", f)
    }

    // ---------- লগইনে চালু (LaunchAgent) ----------
    private const val LABEL = "com.istiqamah.mac.login"
    private val agent = File(System.getProperty("user.home"), "Library/LaunchAgents/$LABEL.plist")

    /** চলমান অ্যাপের .app ফোল্ডার — jpackage-এর অ্যাপে "Istiqamah.app/Contents/app/..." থেকে খুঁজে বের করি */
    private fun appBundle(): File? {
        val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
        var f: File? = File(cmd)
        while (f != null && !f.name.endsWith(".app")) f = f.parentFile
        return f
    }

    fun setLaunchAtLogin(on: Boolean) {
        if (!isMac) return
        if (!on) { agent.delete(); return }
        val app = appBundle() ?: return // ডেভেলপমেন্টে (gradle run) কিছু করি না
        val path = app.absolutePath.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key><string>$LABEL</string>
                <key>ProgramArguments</key>
                <array><string>/usr/bin/open</string><string>-g</string><string>$path</string></array>
                <key>RunAtLoad</key><true/>
            </dict>
            </plist>
        """.trimIndent()
        runCatching {
            agent.parentFile.mkdirs()
            if (!agent.exists() || agent.readText() != xml) agent.writeText(xml)
        }
    }
}
