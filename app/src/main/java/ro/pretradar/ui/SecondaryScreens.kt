package ro.pretradar.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ro.pretradar.data.*
import ro.pretradar.domain.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun DetailScreen(vm: RadarViewModel, detail: DetailState, requestNotifications: () -> Unit) {
    val p = LocalRadarPalette.current
    val context = LocalContext.current
    val watched by vm.products.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val tracked = watched.any { it.id == detail.product.id }
    val demo = detail.product.id.startsWith("demo-")
    val offers = detail.offers.groupBy { it.merchantKey }.values.map { group -> group.filter { it.inStock }.minByOrNull { it.price } ?: group.minBy { it.price } }
        .sortedWith(compareBy<Offer> { !it.inStock }.thenBy { it.price })
    val best = offers.firstOrNull { it.inStock }
    var showAssociation by remember { mutableStateOf(false) }
    var showThreshold by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.testTag("product-details")) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = vm::closeDetail, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = p.card, contentColor = p.text)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Înapoi", Modifier.size(20.dp))
                }
                Text("Compară ofertele", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (demo) Pill("DEMO", warning = true)
            }
        }
        item {
            Surface(color = p.card, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, p.border)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ProductThumbnail(detail.product.title, detail.product.imageUrl, Modifier.size(164.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(detail.product.title, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                        if (detail.product.model.isNotBlank()) Text("Model ${detail.product.model}", fontSize = 12.sp, lineHeight = 17.sp, color = p.muted)
                        if (detail.product.ean.isNotBlank()) Text("EAN ${detail.product.ean}", fontSize = 11.sp, lineHeight = 16.sp, color = p.muted)
                    }
                }
            }
        }
        if (best != null) item {
            Surface(color = p.ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.TrendingDown, null, Modifier.size(19.dp), tint = p.onInk.copy(alpha = .8f))
                        Text("Cel mai mic preț verificat", color = p.onInk.copy(alpha = .8f), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    PriceFigure(best.price, p.onInk, 32, FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(best.store, color = p.onInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(relativeTime(best.checkedAt), color = p.onInk.copy(alpha = .7f), fontSize = 12.sp)
                    }
                }
            }
        }
        if (!demo) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { vm.track(); requestNotifications() }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("track-product"), shape = RoundedCornerShape(14.dp), enabled = !detail.loading) {
                    Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (tracked) "Actualizează urmărirea" else "Urmărește produsul")
                }
                TextButton(onClick = { showThreshold = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(detail.threshold?.let { "Prag ales: ${money(it)}" } ?: "Adaugă un prag de preț (opțional)")
                }
                Text("Alerte la ieftiniri și promoții, inclusiv cu aplicația închisă.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        if (detail.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(detail.issues) { SourceIssue(it) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Magazine și prețuri", style = MaterialTheme.typography.titleLarge)
                Text("Transportul și cupoanele sunt afișate separat.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        items(offers, key = { it.merchantKey }) { offer ->
            Surface(color = p.card, border = BorderStroke(1.dp, if (offer == best) p.green.copy(alpha = .4f) else p.border), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = p.softGreen, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Outlined.Storefront, null, Modifier.padding(9.dp).size(19.dp), tint = p.green)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(offer.store, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
                            if (!offer.seller.equals(offer.store, ignoreCase = true)) Text("Vândut de ${offer.seller}", fontSize = 12.sp, lineHeight = 17.sp, color = p.muted)
                        }
                        if (offer == best) Pill("Minim")
                    }
                    PriceFigure(offer.price, p.text, 26)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(offer.stockLabel, color = if (offer.inStock) p.green else p.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                        Text(relativeTime(offer.checkedAt), fontSize = 11.sp, lineHeight = 16.sp, color = p.muted)
                    }
                    Text(offer.shipping?.let { "Transport: ${money(it)}" } ?: "Transport: verifică la magazin", fontSize = 12.sp, lineHeight = 17.sp, color = p.muted)
                    offer.promotion?.let { promo ->
                        Surface(color = p.softAmber, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(promo.title, color = p.amber, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                                if (promo.code.isNotBlank()) Text("Cod: ${promo.code}", fontWeight = FontWeight.SemiBold, color = p.amber, fontSize = 12.sp)
                                if (promo.conditions.isNotBlank()) Text(promo.conditions, fontSize = 12.sp, lineHeight = 18.sp, color = p.muted)
                            }
                        }
                    }
                    if (!demo) OutlinedButton(onClick = { openBrowser(context, offer.url) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Text("Deschide magazinul"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            }
        }
        if (offers.isEmpty() && !detail.loading) item { EmptyPanel("Preț momentan neverificat", "Poți salva produsul pentru verificări ulterioare sau deschide pagina magazinului.") }
        if (!demo) item {
            OutlinedButton(onClick = { openBrowser(context, detail.product.url) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Vezi pagina produsului") }
            TextButton(onClick = { showAssociation = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Asociază oferta din alt magazin") }
        }
        item {
            Text("Istoricul prețului", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) Text("Istoricul începe după primele verificări. Nu există date anterioare instalării.", fontSize = 13.sp, lineHeight = 19.sp, color = p.muted)
            else {
                val stores = history.map { it.store }.distinct()
                var selectedStore by remember(detail.product.id) { mutableStateOf(stores.first()) }
                val activeStore = selectedStore.takeIf { it in stores } ?: stores.first()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stores.forEach { store -> FilterChip(activeStore == store, onClick = { selectedStore = store }, label = { Text(store, fontSize = 12.sp) }) }
                }
                val points = history.filter { it.store == activeStore }
                Surface(color = p.card, border = BorderStroke(1.dp, p.border), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Minim observat", color = p.muted, fontSize = 12.sp, lineHeight = 17.sp)
                        PriceFigure(points.minOfOrNull { it.price } ?: 0, p.green, 22)
                        Spacer(Modifier.height(24.dp)); Sparkline(points.map { it.price }, Modifier.fillMaxWidth().height(112.dp))
                        Spacer(Modifier.height(12.dp))
                        val format = SimpleDateFormat("dd MMM", Locale.forLanguageTag("ro-RO"))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(points.firstOrNull()?.let { format.format(Date(it.time)) }.orEmpty(), color = p.muted, fontSize = 11.sp)
                            Text(points.lastOrNull()?.let { format.format(Date(it.time)) }.orEmpty(), color = p.muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (!demo && tracked) item { TextButton(onClick = { showRemove = true }, modifier = Modifier.fillMaxWidth()) { Text("Oprește urmărirea", color = p.muted) } }
        if (demo) item { SourceIssue("Acesta este un exemplu vizual. Prețurile, istoricul și cuponul nu sunt oferte reale.") }
    }
    if (showAssociation) AssociateDialog(vm, onDismiss = { showAssociation = false })
    if (showThreshold) ThresholdDialog(detail.threshold, { showThreshold = false }) { vm.threshold(it); showThreshold = false }
    if (showRemove) AlertDialog(onDismissRequest = { showRemove = false }, title = { Text("Oprești urmărirea?") },
        text = { Text("Produsul, istoricul său și alertele locale vor fi eliminate.") },
        confirmButton = { TextButton(onClick = { vm.remove(detail.product.id); showRemove = false }) { Text("Oprește") } },
        dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Anulează") } })
}

@Composable private fun AssociateDialog(vm: RadarViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf(vm.detail.value?.product?.model?.ifBlank { vm.detail.value?.product?.title.orEmpty() }.orEmpty()) }
    var results by remember { mutableStateOf<List<SourceResult<ProductCandidate>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<ProductCandidate?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Asociază același model") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Verifică modelul, capacitatea, culoarea și starea înainte de asociere.", fontSize = 12.sp)
            OutlinedTextField(query, { query = it }, label = { Text("Denumire sau cod model") }, singleLine = true)
            Button(onClick = { scope.launch { loading = true; try { results = vm.repository.search(query) } finally { loading = false } } }, enabled = !loading && query.length >= 2) { Text("Caută") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results.flatMap { it.items }.filter { candidate -> vm.detail.value?.links?.none { it.url == candidate.url } == true }) { candidate ->
                    Surface(onClick = { chosen = candidate }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (chosen == candidate) LocalRadarPalette.current.green else LocalRadarPalette.current.border)) {
                        Column(Modifier.padding(10.dp)) { Text(candidate.title, fontSize = 12.sp); Text(candidate.source.label, fontSize = 11.sp) }
                    }
                }
                items(results.filter { it.error != null }) { SourceIssue("${it.source.label}: ${it.error}") }
            }
            if (chosen != null) Text("Confirm că oferta selectată este pentru aceeași variantă.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }, confirmButton = { TextButton(onClick = { chosen?.let(vm::associate); onDismiss() }, enabled = chosen != null) { Text("Confirmă asocierea") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } })
}

@Composable private fun ThresholdDialog(current: Long?, dismiss: () -> Unit, save: (Long?) -> Unit) {
    var value by remember { mutableStateOf(current?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') }.orEmpty()) }
    val price = parsePrice(value)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Prag de preț") }, text = {
        Column { Text("Lasă gol pentru orice ieftinire și promoție nouă."); Spacer(Modifier.height(10.dp))
            OutlinedTextField(value, { value = it }, suffix = { Text("lei") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                isError = value.isNotBlank() && price == null) }
    }, confirmButton = { TextButton(onClick = { save(price) }, enabled = value.isBlank() || price != null) { Text("Salvează") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Anulează") } })
}

@Composable fun AlertsScreen(vm: RadarViewModel, settings: Settings) {
    val p = LocalRadarPalette.current
    val realAlerts by vm.alerts.collectAsStateWithLifecycle()
    val alerts = if (settings.demo) listOf(SavedAlert(-1, "demo-sony", "Sony WH-1000XM5", "Altex", 129900, 109900, null, System.currentTimeMillis() - 12 * 60000L),
        SavedAlert(-2, "demo-philips", "Philips Airfryer", "Magazin partener", 54900, 54900, "demo", System.currentTimeMillis() - 86400000L)) else realAlerts
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("alerts-list")) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alertele tale", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    if (!settings.demo && alerts.any { !it.read }) TextButton(onClick = vm::markAllRead) { Text("Citite", fontSize = 12.sp) }
                }
                Text(if (alerts.any { !it.read }) "${alerts.count { !it.read }} schimbări noi de preț și promoții." else "Ieftinirile și promoțiile, într-un singur loc.", color = p.muted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        if (settings.demo) item { SourceIssue("Mod demonstrativ · alerte de exemplu") }
        if (alerts.isEmpty()) item { EmptyPanel("Încă nu ai alerte", "Ieftinirile și promoțiile produselor urmărite vor apărea aici.", Icons.Outlined.NotificationsNone) }
        items(alerts, key = { it.id }) { alert ->
            val reduced = alert.oldPrice?.let { it > alert.newPrice } == true
            Surface(onClick = { if (settings.demo) DemoData.candidates.firstOrNull { it.id == alert.productId }?.let(vm::open) else vm.readAlert(alert.id, alert.productId) },
                color = p.card, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (!alert.read) p.green.copy(alpha = .24f) else p.border), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = if (reduced) p.softGreen else p.softAmber, shape = RoundedCornerShape(12.dp)) {
                            Icon(if (reduced) Icons.AutoMirrored.Outlined.TrendingDown else Icons.Outlined.LocalOffer, null, Modifier.padding(10.dp).size(20.dp), tint = if (reduced) p.green else p.amber)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(alert.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("${alert.store} · ${relativeTime(alert.createdAt)}", color = p.muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.muted, modifier = Modifier.size(18.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (reduced) Text(money(alert.oldPrice!!), color = p.muted, fontSize = 12.sp, lineHeight = 17.sp, textDecoration = TextDecoration.LineThrough)
                            PriceFigure(alert.newPrice, p.green, 25)
                        }
                        if (!alert.read) Pill("Nou")
                    }
                    if (reduced) Text("Prețul a scăzut cu ${money(alert.oldPrice!! - alert.newPrice)}", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    if (alert.promotionJson != null) Pill("Promoție nouă", warning = true)
                }
            }
        }
    }
}

@Composable fun SettingsScreen(vm: RadarViewModel, settings: Settings, requestNotifications: () -> Unit, notificationsEnabled: () -> Boolean) {
    val p = LocalRadarPalette.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(notificationsEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = notificationsEnabled() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("settings-list")) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Setări", style = MaterialTheme.typography.headlineLarge)
                Text("Alege cum arată aplicația și cum primești alertele.", color = p.muted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        item {
            SettingsSection(Icons.Outlined.Palette, "Aspect") {
                val fontScale = LocalDensity.current.fontScale
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val useRows = maxWidth < (74f * fontScale * 3f + 16f).dp
                    if (useRows) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChoice.entries.forEach { choice ->
                                ThemeOption(choice, settings.theme == choice, true, { vm.setTheme(choice) }, Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChoice.entries.forEach { choice ->
                                ThemeOption(choice, settings.theme == choice, false, { vm.setTheme(choice) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(Icons.Outlined.NotificationsNone, "Notificări") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(18.dp), tint = if (enabled) p.green else p.amber)
                    Text(if (enabled) "Notificările sunt activate" else "Notificările sunt dezactivate", color = if (enabled) p.green else p.amber, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                }
                Text("Primești alerte la ieftiniri și promoții și cu aplicația închisă.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                if (!enabled) Button(onClick = requestNotifications, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Activează notificările") }
                OutlinedButton(onClick = {
                    context.startActivity(android.content.Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Deschide setările Android", fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(15.dp))
                }
            }
        }
        item {
            SettingsSection(Icons.Outlined.Schedule, "Verificări automate") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Cât de des verificăm prețurile", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 6, 12, 24).forEach { hours -> FilterChip(settings.intervalHours == hours, { vm.setInterval(hours) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("interval-$hours"), label = { Text("$hours h", fontSize = 13.sp) }) }
                    }
                    Text("6 ore pentru consum redus. La 12–24 ore sunt mai puține verificări, iar alertele pot veni mai târziu.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                HorizontalDivider(color = p.border)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Doar rețele necontorizate", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        Text("De obicei Wi-Fi. Pe date mobile, așteaptă reconectarea.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Switch(settings.wifiOnly, vm::setWifiOnly, modifier = Modifier.testTag("wifi-only"))
                }
                SettingsNote(Icons.Outlined.BatteryFull, "Verificările se opresc dacă nu urmărești produse și sunt amânate când bateria este scăzută. Poți verifica manual oricând.")
                Text("Android poate întârzia verificările. După «Oprire forțată», redeschide aplicația.", color = p.muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        item {
            SettingsSection(Icons.Outlined.Storefront, "Surse de produse") {
                Text("Alege magazinele din care primești oferte.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Store.entries.forEachIndexed { index, store ->
                        if (index != 0) HorizontalDivider(color = p.border.copy(alpha = .7f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(store.label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                                Text(when {
                                    store == Store.COMPARI -> "Agregator opțional · acces limitat"
                                    store.directPartner -> "Magazin listat pe Compari.ro · acces direct"
                                    else -> "Oferte și pagini de produse"
                                }, fontSize = 11.sp, lineHeight = 16.sp, color = p.muted)
                            }
                            Switch(store in settings.stores, { vm.toggleStore(store) })
                        }
                    }
                }
                Text("Dacă un magazin limitează accesul, verificările lui intră în pauză. Ultimul preț valid se păstrează.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        item {
            SettingsSection(Icons.Outlined.AutoAwesome, "Mod demonstrativ") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Exemple vizuale, cu prețuri fictive", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    Switch(settings.demo, { vm.setDemo(it) }, modifier = Modifier.testTag("demo-switch"))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadarLogo(Modifier.size(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PrețRadar ${ro.pretradar.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
                    Text("Fără cont sau abonament. Datele rămân pe telefon, cu până la 90 de zile de istoric local.", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable private fun ThemeOption(choice: ThemeChoice, selected: Boolean, horizontal: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalRadarPalette.current
    val icon = when (choice) { ThemeChoice.SYSTEM -> Icons.Outlined.BrightnessAuto; ThemeChoice.LIGHT -> Icons.Outlined.LightMode; ThemeChoice.DARK -> Icons.Outlined.DarkMode }
    val label = when (choice) { ThemeChoice.SYSTEM -> "Automat"; ThemeChoice.LIGHT -> "Luminos"; ThemeChoice.DARK -> "Întunecat" }
    val tint = if (selected) p.green else p.muted
    Surface(color = if (selected) p.softGreen else p.background, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) p.green.copy(alpha = .45f) else p.border),
        modifier = modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).testTag("theme-${choice.name}")) {
        if (horizontal) {
            Row(Modifier.heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(22.dp), tint = tint)
                Text(label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) p.green else p.text, modifier = Modifier.weight(1f))
                Icon(if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(20.dp), tint = tint)
            }
        } else {
            Column(Modifier.heightIn(min = 94.dp).padding(horizontal = 5.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, Modifier.size(22.dp), tint = tint)
                Text(label, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) p.green else p.text)
            }
        }
    }
}

@Composable private fun SettingsSection(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalRadarPalette.current
    Surface(color = p.card, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = p.green)
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
            }
            content()
        }
    }
}

@Composable private fun SettingsNote(icon: ImageVector, text: String) {
    val p = LocalRadarPalette.current
    Surface(color = p.softGreen, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = p.green)
            Text(text, color = p.text, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        }
    }
}
