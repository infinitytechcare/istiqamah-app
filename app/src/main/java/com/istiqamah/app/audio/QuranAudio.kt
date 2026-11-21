package com.istiqamah.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.istiqamah.app.core.QuranIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** তিলাওয়াতের কারি — everyayah.com (প্রতি আয়াত আলাদা ফাইল) */
enum class Reciter(val bn: String, val base: String, val dir: String) {
    ALAFASY("মিশারী রাশিদ আল-আফাসি", "https://everyayah.com/data/Alafasy_64kbps/", "qa_alafasy"),
    ABDUL_SAMAD("আব্দুল বাসিত আব্দুস সামাদ", "https://everyayah.com/data/AbdulSamad_64kbps_QuranExplorer.Com/", "qa_abdulsamad");

    companion object {
        fun fromCode(c: Int) = entries.getOrElse(c) { ALAFASY }
    }
}

/**
 * আয়াত ধরে ধরে তিলাওয়াত বাজানো। প্রতিটা আয়াত একবার নামিয়ে ফোনের ক্যাশে রাখে — পরেরবার ইন্টারনেট লাগে না।
 * মুখস্থের জন্য প্রতিটা আয়াত repeat বার বাজে। বাজার সময় পরের আয়াত আগেভাগে নামিয়ে রাখে।
 */
object QuranAudio {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: MediaPlayer? = null

    private val _playing = MutableStateFlow<Int?>(null)
    /** এখন যে আয়াত বাজছে (ক্রমিক), না বাজলে null */
    val playing: StateFlow<Int?> = _playing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun clearError() { _error.value = null }

    fun play(ctx: Context, index: QuranIndex, ids: List<Int>, times: Int, reciter: Reciter) {
        stop()
        val app = ctx.applicationContext
        job = scope.launch {
            try {
                for ((n, id) in ids.withIndex()) {
                    val file = withContext(Dispatchers.IO) { fetch(app, index, id, reciter) }
                    // পরেরটা আগেভাগে
                    ids.getOrNull(n + 1)?.let { next -> launch(Dispatchers.IO) { runCatching { fetch(app, index, next, reciter) } } }
                    _playing.value = id
                    repeat(times.coerceAtLeast(1)) { playFile(file) }
                }
            } catch (e: IOException) {
                _error.value = "তিলাওয়াত নামানো যায়নি — ইন্টারনেট সংযোগ দেখুন।"
            } finally {
                _playing.value = null
                releasePlayer()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        releasePlayer()
        _playing.value = null
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private suspend fun playFile(f: File) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
        mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
        cont.invokeOnCancellation { runCatching { mp.stop() }; mp.release(); if (player === mp) player = null }
        try {
            mp.setDataSource(f.path)
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            f.delete() // নষ্ট ফাইল — পরেরবার আবার নামাবে
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** ক্যাশে থাকলে সেটা, নইলে নামিয়ে রাখা (একই ফাইল একসাথে দুবার নয়) */
    private fun fetch(ctx: Context, index: QuranIndex, id: Int, reciter: Reciter): File {
        val key = "%03d%03d".format(index.surahOf(id), index.ayahNo(id))
        return synchronized(locks.getOrPut("${reciter.dir}/$key") { Any() }) { download(ctx, key, reciter) }
    }

    private fun download(ctx: Context, key: String, reciter: Reciter): File {
        val dir = File(ctx.cacheDir, reciter.dir).apply { mkdirs() }
        val target = File(dir, "$key.mp3")
        if (target.exists() && target.length() > 0) return target
        val tmp = File(dir, "$key.part")
        val conn = URL("${reciter.base}$key.mp3").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() == 0L || !tmp.renameTo(target)) throw IOException("empty")
            return target
        } catch (e: Exception) {
            tmp.delete()
            throw if (e is IOException) e else IOException(e)
        } finally {
            conn.disconnect()
        }
    }
}
