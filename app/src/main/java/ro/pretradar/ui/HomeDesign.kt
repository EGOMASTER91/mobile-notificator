package ro.pretradar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun HomeHero(query: String, onQuery: (String) -> Unit, compact: Boolean = false, search: () -> Unit) {
    val p = LocalRadarPalette.current
    Surface(color = p.ink, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Box {
            Canvas(Modifier.align(Alignment.TopEnd).size(160.dp)) {
                val center = Offset(size.width * .96f, size.height * .15f)
                for (i in 1..4) drawCircle(p.onInk.copy(alpha = .065f), size.width * (.19f * i), center, style = Stroke(1.dp.toPx()))
                drawCircle(p.onInk.copy(alpha = .28f), 4.dp.toPx(), Offset(size.width * .61f, size.height * .49f))
            }
            Column(Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (compact) {
                    Text("Găsește produsul potrivit", color = p.onInk, fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-.4).sp)
                } else {
                    Text("LA MOMENTUL POTRIVIT", color = p.onInk.copy(alpha = .75f),
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, lineHeight = 15.sp)
                    Text("Plătește mai puțin.\nAlege mai bine.", color = p.onInk, fontWeight = FontWeight.Bold,
                        fontSize = 29.sp, lineHeight = 34.sp, letterSpacing = (-.8).sp)
                    Text("Compară magazinele și află când scade prețul produselor tale.", color = p.onInk.copy(alpha = .84f),
                        fontSize = 13.sp, lineHeight = 19.sp)
                }
                OutlinedTextField(query, onValueChange = onQuery, singleLine = true,
                    placeholder = { Text("Ce produs cauți?", color = p.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = p.muted, modifier = Modifier.size(21.dp)) },
                    trailingIcon = { IconButton(onClick = search, modifier = Modifier.testTag("submit-search")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Caută produsul", tint = p.green, modifier = Modifier.size(21.dp))
                    } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("search-input"),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = p.green, unfocusedContainerColor = p.card, focusedContainerColor = p.card,
                        focusedTextColor = p.text, unfocusedTextColor = p.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
            }
        }
    }
}

@Composable internal fun HomeOverview(products: Int, alerts: Int, onWatched: () -> Unit, onAlerts: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewTile(products, "produse urmărite", Icons.Outlined.FavoriteBorder, Modifier.weight(1f), onWatched)
        OverviewTile(alerts, "alerte noi", Icons.Outlined.NotificationsNone, Modifier.weight(1f), onAlerts)
    }
}

@Composable private fun OverviewTile(value: Int, label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalRadarPalette.current
    Surface(onClick = onClick, color = p.card, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, p.border), modifier = modifier) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value.toString().padStart(2, '0'), color = p.text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = p.green, modifier = Modifier.size(19.dp))
            }
            Text(label, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
        }
    }
}

@Composable internal fun HotDiscovery(onClick: () -> Unit) {
    val p = LocalRadarPalette.current
    Surface(onClick = onClick, color = p.softHot, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalFireDepartment, null, tint = p.hot, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text("Explorează Hot", fontWeight = FontWeight.SemiBold, color = p.text, fontSize = 14.sp)
                Text("Reducerile mari, pe categorii", color = p.muted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = p.hot, modifier = Modifier.size(20.dp))
        }
    }
}
