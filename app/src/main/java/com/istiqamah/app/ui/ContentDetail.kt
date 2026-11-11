package com.istiqamah.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.ContentItem

@Composable
fun ContentDetail(item: ContentItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "ফিরে যান", tint = Emerald800) }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (item.type == "hadith") "হাদিস" else "আয়াত", color = Gold, fontWeight = FontWeight.Bold)
            Text(item.refBn, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            if (item.arabic.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    item.arabic,
                    fontFamily = Amiri, fontSize = 28.sp, lineHeight = 52.sp, color = Emerald900,
                    textAlign = TextAlign.Right,
                    style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
                    modifier = Modifier.fillMaxWidth()
                        .background(Emerald50, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(item.bn, fontSize = 19.sp, lineHeight = 30.sp, color = Ink)
            Spacer(Modifier.height(16.dp))
            Text("অনুবাদ: ${item.bnSource}", fontSize = 13.sp, color = Muted)
            item.grading?.let { Text("হাদিসের মান: $it", fontSize = 13.sp, color = Muted) }
            Text(
                if (item.isReviewed) "আলেমের রিভিউ: ${item.reviewedBy}" else "আলেমের রিভিউ: এখনো বাকি",
                fontSize = 13.sp, color = if (item.isReviewed) Emerald700 else StatusQaza,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item.link?.let { url ->
                    OutlinedButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }) { Text("সূত্র যাচাই করুন") }
                }
                OutlinedButton(onClick = {
                    val text = buildString {
                        if (item.arabic.isNotBlank()) appendLine(item.arabic).appendLine()
                        appendLine(item.bn)
                        append("— ${item.refBn}")
                    }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "শেয়ার"))
                }) { Text("শেয়ার") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
