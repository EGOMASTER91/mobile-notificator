package ro.pretradar.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import ro.pretradar.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import ro.pretradar.domain.*
import java.util.Locale

@Composable fun RadarLogo(modifier: Modifier = Modifier) {
    val green = LocalRadarPalette.current.green
    Canvas(modifier.sizeIn(minWidth = 36.dp, maxWidth = 36.dp, minHeight = 36.dp, maxHeight = 36.dp)) {
        val radius = size.minDimension * .42f
        drawCircle(green.copy(alpha = .09f), size.minDimension * .5f)
        drawCircle(green, radius, style = Stroke(1.8.dp.toPx()))
        drawCircle(green.copy(alpha = .45f), radius * .6f, style = Stroke(1.2.dp.toPx()))
        drawLine(green, center, Offset(center.x + radius * .66f, center.y - radius * .66f), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(green, 2.2.dp.toPx(), center)
        drawCircle(green, 2.4.dp.toPx(), Offset(center.x - radius * .4f, center.y + radius * .25f))
    }
}

@Composable fun Pill(text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalRadarPalette.current
    Text(text, color = if (warning) p.amber else p.green, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.background(if (warning) p.softAmber else p.softGreen, RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp))
}

@Composable fun SourceChip(store: Store, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalRadarPalette.current
    Surface(modifier = modifier.heightIn(min = 52.dp).semantics {
        this.selected = selected
        stateDescription = if (selected) "Activat" else "Dezactivat"
    }, onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (selected) p.card else p.softSurface,
        border = BorderStroke(1.dp, if (selected) p.green.copy(alpha = .4f) else p.border)) {
        BoxWithConstraints(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
            val labelWidth = textMeasurer.measure(AnnotatedString(store.label), style = labelStyle).size.width
            val rowWidth = labelWidth + with(density) { 30.dp.roundToPx() }
            val showMark = rowWidth <= constraints.maxWidth
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (showMark) {
                    StoreMark(store, Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(store.label, color = if (selected) p.text else p.muted, style = labelStyle)
            }
        }
    }
}

@Composable fun StoreMark(store: Store, modifier: Modifier = Modifier) {
    val p = LocalRadarPalette.current
    val color = when (store) { Store.ALTEX -> Color(0xFFFFD44E); Store.EMAG -> Color(0xFFD93243); else -> p.softGreen }
    Box(modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp).background(color, RoundedCornerShape(6.dp))
        .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(when (store) { Store.ALTEX -> "A"; Store.EMAG -> "e"; Store.COMPARI -> "≋"; else -> store.label.take(1) },
            color = when (store) { Store.ALTEX -> Color(0xFFA52E22); Store.EMAG -> Color.White; else -> p.green },
            fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp)
    }
}

@Composable fun Sparkline(values: List<Long>, modifier: Modifier = Modifier, warning: Boolean = false, neutral: Boolean = false) {
    val p = LocalRadarPalette.current
    val color = when { warning -> p.amber; neutral -> p.muted; else -> p.green }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull()!!.toDouble(); val max = values.maxOrNull()!!.toDouble()
        val range = (max - min).coerceAtLeast(max * .015).coerceAtLeast(1.0)
        val padding = 4.dp.toPx().coerceAtMost(size.minDimension / 4)
        val width = (size.width - 2 * padding).coerceAtLeast(0f)
        val height = (size.height - 2 * padding).coerceAtLeast(0f)
        fun point(i: Int): Offset = Offset(
            padding + width * if (values.size == 1) .5f else i.toFloat() / (values.size - 1),
            padding + height * if (max == min) .5f else (1 - (values[i] - min) / range).toFloat()
        )
        val path = Path().apply {
            values.indices.forEach { i -> val point = point(i); if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        if (values.size > 1) {
            val area = Path().apply {
                addPath(path); lineTo(point(values.lastIndex).x, size.height - padding)
                lineTo(point(0).x, size.height - padding); close()
            }
            drawPath(area, color.copy(alpha = if (neutral) .035f else .07f))
            drawLine(p.border.copy(alpha = .5f), Offset(padding, size.height - padding),
                Offset(size.width - padding, size.height - padding), .6.dp.toPx())
            drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(color, 2.8.dp.toPx(), point(values.lastIndex))
    }
}

@Composable fun ProductThumbnail(title: String, imageUrl: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.sizeIn(minWidth = 88.dp, maxWidth = 88.dp, minHeight = 88.dp, maxHeight = 88.dp).clip(shape)
        .background(Color(0xFFF8F9F8)).border(1.dp, Color(0xFFE8EDE9), shape), contentAlignment = Alignment.Center) {
        if (imageUrl.startsWith("demo:")) {
            val asset = when (imageUrl) { "demo:headphones" -> R.drawable.demo_headphones; "demo:phone" -> R.drawable.demo_phone; else -> R.drawable.demo_airfryer }
            Image(painterResource(asset), title, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit)
        } else if (ro.pretradar.domain.RemoteUrls.image(imageUrl).isNotBlank()) SubcomposeAsyncImage(imageUrl, title, Modifier.fillMaxSize().padding(9.dp), contentScale = ContentScale.Fit,
            loading = { ProductIllustration(title, Modifier.fillMaxSize().padding(5.dp)) },
            error = { ProductIllustration(title, Modifier.fillMaxSize().padding(5.dp)) })
        else ProductIllustration(title, Modifier.fillMaxSize().padding(10.dp))
    }
}

/** Native fallback illustrations for preview or missing catalog photographs. */
@Composable fun ProductIllustration(title: String, modifier: Modifier = Modifier) {
    val green = LightPalette.green
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when {
            title.contains("Sony", true) || title.contains("casti", true) -> {
                drawArc(Color(0xFF20252C), 180f, 180f, false, topLeft = Offset(w * .19f, h * .1f), size = androidx.compose.ui.geometry.Size(w * .62f, h * .78f), style = Stroke(w * .095f, cap = StrokeCap.Round))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF4C525A), Color(0xFF14191F))), Offset(w * .08f, h * .46f), androidx.compose.ui.geometry.Size(w * .27f, h * .4f), androidx.compose.ui.geometry.CornerRadius(w * .11f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF434A53), Color(0xFF0E141B))), Offset(w * .65f, h * .46f), androidx.compose.ui.geometry.Size(w * .27f, h * .4f), androidx.compose.ui.geometry.CornerRadius(w * .11f))
            }
            title.contains("Samsung", true) || title.contains("telefon", true) || title.contains("iphone", true) -> {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF575C69), Color(0xFF262A37))), Offset(w * .18f, h * .07f), androidx.compose.ui.geometry.Size(w * .42f, h * .84f), androidx.compose.ui.geometry.CornerRadius(w * .055f))
                for (i in 0..2) drawCircle(Color(0xFF11141A), w * .045f, Offset(w * .27f, h * (.18f + .14f * i)))
                drawRoundRect(Color(0xFF11141C), Offset(w * .42f, h * .1f), androidx.compose.ui.geometry.Size(w * .43f, h * .85f), androidx.compose.ui.geometry.CornerRadius(w * .05f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFAA9DD1), Color(0xFF514369), Color(0xFF161126))), Offset(w * .445f, h * .125f), androidx.compose.ui.geometry.Size(w * .38f, h * .795f), androidx.compose.ui.geometry.CornerRadius(w * .045f))
            }
            title.contains("Philips", true) || title.contains("airfryer", true) -> {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFF6F7F7), Color(0xFFB9BFC4), Color(0xFFEDEFF0))), Offset(w * .16f, h * .08f), androidx.compose.ui.geometry.Size(w * .68f, h * .84f), androidx.compose.ui.geometry.CornerRadius(w * .17f))
                drawCircle(Color(0xFF383C42), w * .07f, Offset(w * .5f, h * .28f))
                drawLine(Color(0xFF92989E), Offset(w * .22f, h * .47f), Offset(w * .78f, h * .47f), 1.dp.toPx())
                drawRoundRect(Color(0xFF30363E), Offset(w * .435f, h * .48f), androidx.compose.ui.geometry.Size(w * .13f, h * .29f), androidx.compose.ui.geometry.CornerRadius(w * .04f))
            }
            else -> {
                drawRoundRect(green.copy(alpha = .16f), Offset(w * .16f, h * .18f), androidx.compose.ui.geometry.Size(w * .68f, h * .64f), androidx.compose.ui.geometry.CornerRadius(w * .1f))
                drawLine(green, Offset(w * .3f, h * .5f), Offset(w * .7f, h * .5f), 3.dp.toPx())
                drawLine(green, Offset(w * .5f, h * .3f), Offset(w * .5f, h * .7f), 3.dp.toPx())
            }
        }
    }
}

@Composable fun ProductCard(
    title: String, image: String, offer: Offer?, previous: Long? = null,
    points: List<Long> = emptyList(), subtitle: String = "", error: String = "", onClick: () -> Unit
) {
    val p = LocalRadarPalette.current
    val decreased = previous != null && offer != null && offer.price < previous
    val currentPrice = offer?.let { money(it.price) } ?: "—"
    val badge = when {
        decreased -> "−${String.format(Locale.forLanguageTag("ro-RO"), "%.1f", (previous!! - offer!!.price) * 100.0 / previous)}%"
        offer?.promotion != null -> if (offer.promotion.code.isNotBlank()) "Cupon" else "Promoție"
        else -> ""
    }
    val priceStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-.6).sp)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val status = when {
        error.isNotBlank() -> "Actualizare indisponibilă"
        decreased -> "Prețul a scăzut"
        offer?.promotion != null -> "Promoție disponibilă"
        else -> "Vezi ofertele"
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = p.card,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProductThumbnail(title, image, Modifier.size(if (density.fontScale >= 1.3f) 72.dp else 88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = p.text)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
                    Text(offer?.store ?: "În așteptarea verificării", style = MaterialTheme.typography.bodySmall,
                        color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (offer?.inStock == true) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(p.green, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(offer.stockLabel, color = p.green, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val priceWidth = textMeasurer.measure(AnnotatedString(currentPrice), style = priceStyle).size.width
                val badgeWidth = if (badge.isBlank()) 0 else textMeasurer.measure(
                    AnnotatedString(badge), style = MaterialTheme.typography.labelMedium
                ).size.width + with(density) { 30.dp.roundToPx() }
                val stacked = maxWidth < 280.dp || density.fontScale >= 1.3f || priceWidth + badgeWidth > constraints.maxWidth
                val priceContent: @Composable (Modifier) -> Unit = { priceModifier ->
                    Column(priceModifier) {
                        if (decreased) Text(money(previous!!), color = p.muted, style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough)
                        if (offer != null) PriceFigure(offer.price, p.text, 26, FontWeight.Bold)
                        else Text(currentPrice, color = p.text, style = priceStyle)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    priceContent(Modifier.fillMaxWidth())
                    if (badge.isNotBlank()) Pill(badge, warning = !decreased)
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    priceContent(Modifier.weight(1f))
                    if (badge.isNotBlank()) Pill(badge, warning = !decreased)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = p.border.copy(alpha = .75f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status, color = when { error.isNotBlank() -> p.amber; decreased -> p.green; else -> p.muted },
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (points.isNotEmpty()) Sparkline(points, Modifier.width(72.dp).height(23.dp),
                    warning = offer?.promotion != null, neutral = !decreased && offer?.promotion == null)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp), tint = p.muted)
            }
        }
    }
}

/** Keeps the complete amount readable when display width or Android text size changes. */
@Composable internal fun PriceFigure(cents: Long, color: Color, preferredSize: Int, weight: FontWeight = FontWeight.SemiBold) {
    val text = money(cents)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.titleLarge.copy(fontWeight = weight, letterSpacing = (-.6).sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = with(density) { maxWidth.roundToPx() }
        val fittedSize = remember(text, availableWidth, density.fontScale, style, preferredSize, measurer) {
            (preferredSize downTo 16).firstOrNull { size ->
                measurer.measure(text, style = style.copy(fontSize = size.sp), softWrap = false, maxLines = 1).size.width <= availableWidth
            } ?: 16
        }
        Text(text, color = color, style = style.copy(fontSize = fittedSize.sp, lineHeight = (fittedSize + 7).sp))
    }
}

@Composable fun PageHeading(
    title: String, subtitle: String, modifier: Modifier = Modifier,
    eyebrow: String = "", action: (@Composable () -> Unit)? = null
) {
    val p = LocalRadarPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (eyebrow.isNotBlank()) Text(eyebrow, color = p.green, style = MaterialTheme.typography.labelMedium)
            Text(title, color = p.text, style = MaterialTheme.typography.headlineLarge)
            if (subtitle.isNotBlank()) Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodyMedium)
        }
        action?.invoke()
    }
}

@Composable fun SectionHeading(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val p = LocalRadarPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = p.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

fun relativeTime(time: Long): String {
    if (time <= 0) return "încă neverificat"
    val minutes = ((System.currentTimeMillis() - time) / 60000).coerceAtLeast(0)
    return when { minutes < 1 -> "acum"; minutes < 60 -> "acum $minutes min"; minutes < 1440 -> "acum ${minutes / 60} h"; else -> "acum ${minutes / 1440} zile" }
}
