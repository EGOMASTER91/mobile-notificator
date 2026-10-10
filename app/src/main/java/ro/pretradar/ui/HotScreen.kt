package ro.pretradar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ro.pretradar.domain.*
import kotlin.math.roundToInt

@Composable fun HotScreen(vm: RadarViewModel, settings: Settings) {
    val p = LocalRadarPalette.current
    val state by vm.hot.collectAsStateWithLifecycle()
    val stores = settings.stores.intersect(setOf(Store.ALTEX, Store.EMAG))
    LifecycleStartEffect(settings.hotCategory, stores, settings.demo) {
        vm.loadHot()
        onStopOrDispose { vm.cancelHot() }
    }
    var categoryDialog by remember { mutableStateOf(false) }
    var minimum by remember(settings.hotMinimum) { mutableFloatStateOf(settings.hotMinimum.toFloat()) }
    val results = if (state.category == settings.hotCategory) state.results.filter { it.source in stores } else emptyList()
    val deals = HotDeals.filter(results.flatMap { it.items }, minimum.roundToInt(), stores)
    val context = LocalContext.current
    if (categoryDialog) AlertDialog(onDismissRequest = { categoryDialog = false },
        title = { Text("Categorii") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp).testTag("hot-categories")) {
                HotCategory.entries.groupBy { it.group }.forEach { (group, categories) ->
                    item { Text(group, color = p.green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                    items(categories) { category ->
                        TextButton(onClick = { vm.setHotCategory(category); categoryDialog = false },
                            modifier = Modifier.fillMaxWidth().testTag("hot-category-${category.name}")) {
                            Text(category.label, color = p.text, modifier = Modifier.weight(1f))
                            if (category == settings.hotCategory) Icon(Icons.Outlined.Check, "Selectată", tint = p.green)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { categoryDialog = false }) { Text("Închide") } })
    LazyColumn(Modifier.fillMaxSize().testTag("hot-list"), contentPadding = PaddingValues(20.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PageHeading("Hot", "Oferte mari. Alegeri inspirate.", action = {
                Surface(color = p.card, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, p.border)) {
                    IconButton(onClick = { vm.loadHot(force = true) }, enabled = !state.loading,
                        modifier = Modifier.testTag("hot-refresh")) { Icon(Icons.Outlined.Refresh, "Actualizează ofertele", tint = p.text) }
                }
            })
        }
        if (settings.demo) item { Pill("Mod demonstrativ · prețuri fictive", warning = true) }
        item {
            Surface(onClick = { categoryDialog = true }, shape = RoundedCornerShape(16.dp), color = p.card,
                border = BorderStroke(1.dp, p.border),
                modifier = Modifier.fillMaxWidth().testTag("hot-choose-category")) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Category, null, Modifier.size(21.dp), tint = p.green)
                    Text(settings.hotCategory.label, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Icon(Icons.Outlined.ExpandMore, "Alege categoria", tint = p.muted)
                }
            }
        }
        item {
            Surface(color = p.card, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, p.border)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reducere minimă", color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Text("${minimum.roundToInt()}%", modifier = Modifier.testTag("hot-minimum"),
                            fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, color = p.green)
                    }
                    Slider(value = minimum, onValueChange = { minimum = it.roundToInt().toFloat() }, valueRange = 0f..99f,
                        onValueChangeFinished = { vm.setHotMinimum(minimum.roundToInt()) },
                        modifier = Modifier.testTag("hot-slider").semantics {
                            contentDescription = "Reducere minimă"; stateDescription = "${minimum.roundToInt()}%"
                        }, colors = SliderDefaults.colors(thumbColor = p.green, activeTrackColor = p.green, inactiveTrackColor = p.softGreen))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(10, 30, 50, 70).forEach { value ->
                            FilterChip(selected = minimum.roundToInt() == value,
                                onClick = { minimum = value.toFloat(); vm.setHotMinimum(value) }, label = { Text("$value%") },
                                modifier = Modifier.testTag("hot-preset-$value"), shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = p.softGreen, selectedLabelColor = p.green))
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Store.ALTEX, Store.EMAG).forEach { store ->
                    SourceChip(store, store in stores, { vm.toggleStore(store) },
                        modifier = Modifier.weight(1f).testTag("hot-source-${store.name}"))
                }
            }
        }
        if (state.loading || state.category != settings.hotCategory) item {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.green)
            Text("Încarc ofertele categoriei…", color = p.muted, fontSize = 12.sp)
        }
        results.filter { it.error != null }.forEach { result -> item {
            HotSourceIssue(result.source, result.error.orEmpty()) { openBrowser(context, settings.hotCategory.url(result.source)) }
        } }
        if (!state.loading && state.category == settings.hotCategory) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${deals.size} oferte pentru tine", color = p.text, fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.South, null, tint = p.green, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.height(5.dp))
                Text("Cele mai mari reduceri dintre ofertele încărcate", color = p.muted, fontSize = 12.sp)
                if (state.loadedAt > 0) Text("Actualizat ${relativeTime(state.loadedAt)}", color = p.muted, fontSize = 12.sp)
            }
            if (deals.isEmpty()) item {
                Surface(color = p.card, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (stores.isEmpty()) "Activează Altex sau eMAG" else "Nicio ofertă verificată peste pragul ales",
                            fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.testTag("hot-empty"))
                        Text("Poți schimba categoria, micșora pragul sau actualiza ofertele.", color = p.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        items(deals, key = { it.product.source.name + it.product.id }) { deal -> HotCard(deal) { vm.open(deal.product) } }
        item { Text("Reducerile folosesc prețul anterior afișat de magazin. PRP și cupoanele nu sunt incluse. Atinge un produs pentru detalii și urmărire.",
            color = p.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

@Composable private fun HotCard(deal: HotDeal, onClick: () -> Unit) {
    val p = LocalRadarPalette.current
    val thumbnailSize = if (LocalDensity.current.fontScale >= 1.3f) 72.dp else 92.dp
    Surface(onClick = onClick, color = p.card, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth().testTag("hot-deal-${deal.product.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreMark(deal.product.source, Modifier.size(22.dp))
                Text(deal.product.source.label, color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 7.dp))
                Surface(color = p.softHot, shape = RoundedCornerShape(9.dp)) {
                    Text(if (deal.discountPercent > 0) "−${deal.discountPercent}%" else
                        "−0,${deal.discountBasisPoints.toString().padStart(2, '0')}%", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = p.hot, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProductThumbnail(deal.product.title, deal.product.imageUrl, Modifier.size(thumbnailSize))
                Text(deal.product.title, color = p.text, fontWeight = FontWeight.Medium, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = p.border.copy(alpha = .65f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(money(deal.referencePrice), color = p.muted, textDecoration = TextDecoration.LineThrough, fontSize = 12.sp)
                    PriceFigure(deal.product.price!!, p.green, 25)
                }
                Surface(color = p.softGreen, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ChevronRight, null, tint = p.green, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable private fun HotSourceIssue(store: Store, message: String, openStore: () -> Unit) {
    val p = LocalRadarPalette.current
    var expanded by remember(store, message) { mutableStateOf(false) }
    Surface(color = p.card, border = BorderStroke(1.dp, p.border), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = p.amber, modifier = Modifier.size(18.dp))
                Text("${store.label} indisponibil momentan", color = p.muted, fontSize = 12.sp,
                    lineHeight = 17.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    "Detalii acces ${store.label}", tint = p.muted, modifier = Modifier.size(20.dp)) }
            }
            if (expanded) {
                Text(message, color = p.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(end = 8.dp))
                TextButton(onClick = openStore) { Text("Vezi în magazin", fontSize = 12.sp) }
            }
        }
    }
}
