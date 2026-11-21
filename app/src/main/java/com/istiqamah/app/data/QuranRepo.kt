package com.istiqamah.app.data

import android.content.Context
import com.istiqamah.app.core.QuranIndex
import org.json.JSONArray
import org.json.JSONObject

/** একটি সূরার তথ্য */
data class SurahInfo(val no: Int, val start: Int, val count: Int, val nameBn: String, val nameAr: String, val meaning: String, val meccan: Boolean)

/**
 * assets/quran.json — পুরো কুরআন (আরবি + মুহিউদ্দীন খানের বাংলা অনুবাদ) আর মাদানি মুসহাফের পৃষ্ঠা-পারা-রুকু।
 * প্রায় ৩.৪ MB, তাই প্রথমবার IO থ্রেডে লোড করে মেমরিতে রাখা হয়।
 */
class QuranData(
    val index: QuranIndex,
    val surahs: List<SurahInfo>,
    private val ar: Array<String>,
    private val bn: Array<String>,
    private val bnIssues: Set<Int>,
    val attribution: String,
) {
    fun ar(id: Int): String = ar[id - 1]
    fun bn(id: Int): String = bn[id - 1]
    fun hasBnIssue(id: Int): Boolean = id in bnIssues
    fun surah(id: Int): SurahInfo = surahs[index.surahOf(id) - 1]

    /** "আল-বাকারা ২:১৪২ · পৃষ্ঠা ২২" */
    fun label(id: Int): String {
        val s = surah(id)
        return "${s.nameBn} ${com.istiqamah.app.core.Bn.num(s.no)}:${com.istiqamah.app.core.Bn.num(index.ayahNo(id))} · পৃষ্ঠা ${com.istiqamah.app.core.Bn.num(index.pageOf(id))}"
    }
}

object QuranRepo {
    @Volatile private var cache: QuranData? = null

    fun loaded(): QuranData? = cache

    /** IO থ্রেড থেকে ডাকুন */
    fun get(context: Context): QuranData =
        cache ?: synchronized(this) { cache ?: load(context).also { cache = it } }

    private fun JSONArray.ints(): IntArray = IntArray(length()) { getInt(it) }

    private fun load(context: Context): QuranData {
        val text = context.assets.open("quran.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = JSONObject(text)
        val sArr = o.getJSONArray("surahs")
        val surahs = (0 until sArr.length()).map { i ->
            val r = sArr.getJSONArray(i)
            SurahInfo(i + 1, r.getInt(0), r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5) == 1)
        }
        val index = QuranIndex(
            surahs.map { it.start }.toIntArray(),
            o.getJSONArray("pages").ints(), o.getJSONArray("juz").ints(), o.getJSONArray("ruku").ints(),
            o.getJSONArray("sajda").ints().toSet(),
        )
        val arA = o.getJSONArray("ar")
        val bnA = o.getJSONArray("bn")
        return QuranData(
            index, surahs,
            Array(arA.length()) { com.istiqamah.app.core.QuranScript.forFont(arA.getString(it)) },
            Array(bnA.length()) { bnA.getString(it) },
            o.getJSONArray("bn_issues").ints().toSet(),
            o.getString("attribution"),
        )
    }
}
