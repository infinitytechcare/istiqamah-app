package com.istiqamah.app.data

import android.content.Context
import com.istiqamah.app.core.ContentItem
import org.json.JSONArray
import org.json.JSONObject

/** assets/content.json থেকে আয়াত ও হাদিস লোড করে (একবার, মেমরিতে রাখে) */
object ContentRepo {
    @Volatile private var cache: List<ContentItem>? = null

    fun all(context: Context): List<ContentItem> =
        cache ?: synchronized(this) { cache ?: load(context).also { cache = it } }

    fun byId(context: Context, id: String): ContentItem? = all(context).firstOrNull { it.id == id }

    /** কয়টা আইটেম এখনো আলেমের রিভিউ পায়নি */
    fun unreviewedCount(context: Context): Int = all(context).count { !it.isReviewed }

    private fun load(context: Context): List<ContentItem> {
        val text = context.assets.open("content.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(text).getJSONArray("items")
        return (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }

    private fun JSONObject.optStr(k: String): String? = if (isNull(k) || !has(k)) null else getString(k).ifBlank { null }
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)

    private fun parse(o: JSONObject): ContentItem {
        val tags = o.getJSONArray("tags").let { a: JSONArray -> (0 until a.length()).map { a.getString(it) } }
        val ref = o.optJSONObject("ref")
        return ContentItem(
            id = o.getString("id"),
            tags = tags,
            type = o.getString("type"),
            refBn = o.getString("ref_bn"),
            surah = ref?.optIntOrNull("surah"),
            ayahFrom = ref?.optIntOrNull("ayah_from"),
            ayahTo = ref?.optIntOrNull("ayah_to"),
            arabic = o.optString("arabic", ""),
            bn = o.getString("bn"),
            bnSource = o.getString("bn_source"),
            shortBn = o.getString("short_bn"),
            tone = o.getString("tone"),
            pairWith = o.optStr("pair_with"),
            grading = o.optStr("grading"),
            reviewedBy = o.optStr("reviewed_by"),
            link = o.optStr("link"),
        )
    }
}
