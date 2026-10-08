package org.hander.novelreader.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hander.novelreader.ui.theme.HanderBorder
import org.hander.novelreader.ui.theme.HanderGold
import org.hander.novelreader.ui.theme.HanderIvory
import org.hander.novelreader.ui.theme.HanderMuted
import org.hander.novelreader.ui.theme.HanderOnGold
import org.hander.novelreader.ui.theme.HanderPanel
import org.hander.novelreader.ui.theme.HanderPanelRaised
import org.hander.novelreader.ui.theme.HanderTeal
import org.hander.novelreader.ui.theme.HanderTealDeep
import org.hander.novelreader.ui.theme.HanderTealDim

/** Small, widely-spaced teal heading used above each section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = HanderTeal,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        modifier = modifier,
    )
}

/** A rounded dark panel with a thin border. Clickable when [onClick] is given. */
@Composable
fun HanderCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color = HanderPanel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(BorderStroke(1.dp, HanderBorder), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HanderGold, contentColor = HanderOnGold),
    ) {
        Text(text.uppercase(), letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun TealButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HanderTealDim),
        colors = ButtonDefaults.buttonColors(containerColor = HanderTealDeep, contentColor = HanderIvory),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HanderBorder),
        colors = ButtonDefaults.buttonColors(containerColor = HanderPanelRaised, contentColor = HanderIvory),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Segmented picker, like the typeface selector in Settings. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.dp, HanderBorder), shape),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) HanderTealDeep else HanderBackgroundDark)
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) HanderIvory else HanderMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private val HanderBackgroundDark = androidx.compose.ui.graphics.Color(0xFF070E11)

/** Book cover loaded from a file off the main thread, with a quiet placeholder. */
@Composable
fun CoverImage(path: String?, modifier: Modifier = Modifier, iconSize: Dp = 28.dp) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HanderPanelRaised)
            .border(BorderStroke(1.dp, HanderBorder), RoundedCornerShape(10.dp)),
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Book,
                contentDescription = null,
                tint = HanderTealDim,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconSize),
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFF0A1D21), androidx.compose.ui.graphics.Color(0xFF060B0E)),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(title, color = HanderIvory, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(subtitle, color = HanderMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HanderBorder),
    )
}
