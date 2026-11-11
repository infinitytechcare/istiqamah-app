package com.istiqamah.app.data

import android.content.Context
import com.istiqamah.app.core.AzkarItem
import com.istiqamah.app.core.AzkarSet
import org.json.JSONObject

/** assets/azkar.json থেকে আযকার লোড (একবার, মেমরিতে) */
object AzkarRepo {
    @Volatile private var cache: List<AzkarItem>? = null
    @Volatile var attribution: String = ""
        private set

    fun all(context: Context): List<AzkarItem> =
        cache ?: synchronized(this) { cache ?: load(context).also { cache = it } }

    fun forSet(context: Context, set: AzkarSet): List<AzkarItem> = all(context).filter { it.set == set }.sortedBy { it.order }

    private fun load(context: Context): List<AzkarItem> {
        val text = context.assets.open("azkar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        attribution = root.optString("attribution", "")
        val arr = root.getJSONArray("items")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val set = runCatching { AzkarSet.valueOf(o.getString("set")) }.getOrNull() ?: return@mapNotNull null
            AzkarItem(
                id = o.getString("id"),
                set = set,
                order = o.optInt("order", i),
                titleBn = o.getString("title"),
                arabic = o.getString("arabic"),
                pronunciationBn = o.optString("pron", ""),
                meaningBn = o.optString("meaning", ""),
                count = o.optInt("count", 1).coerceAtLeast(1),
                refBn = o.getString("ref"),
                grading = o.optString("grading", ""),
                noteBn = o.optString("note", ""),
                reviewedBy = if (o.isNull("reviewed_by")) null else o.optString("reviewed_by").ifBlank { null },
            )
        }
    }
}
