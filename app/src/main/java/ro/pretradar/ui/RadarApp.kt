package ro.pretradar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ro.pretradar.data.*
import ro.pretradar.domain.*

enum class Page(val label: String) { SEARCH("Caută"), HOT("Hot"), WATCHED("Urmărite"), ALERTS("Alerte"), SETTINGS("Setări") }

@Composable fun RadarApp(vm: RadarViewModel, requestNotifications: () -> Unit, notificationsEnabled: () -> Boolean) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.SEARCH) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    RadarTheme(settings.theme) {
        val p = LocalRadarPalette.current
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.context as? android.app.Activity)?.let { activity ->
                val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, view)
                val light = p.background == LightPalette.background
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }
        }
        Scaffold(containerColor = p.background, snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (detail == null) NavigationBar(containerColor = p.card, tonalElevation = 0.dp,
                    modifier = Modifier.border(BorderStroke(.5.dp, p.border)),
                    windowInsets = WindowInsets.navigationBars) {
                    Page.entries.forEach { destination ->
                        val icon = when (destination) { Page.SEARCH -> Icons.Outlined.Search; Page.HOT -> Icons.Outlined.LocalFireDepartment; Page.WATCHED -> Icons.Outlined.FavoriteBorder; Page.ALERTS -> Icons.Outlined.NotificationsNone; Page.SETTINGS -> Icons.Outlined.Tune }
                        NavigationBarItem(selected = page == destination, onClick = { page = destination },
                            icon = { if (destination == Page.ALERTS && alerts.any { !it.read }) BadgedBox(badge = { Badge() }) { Icon(icon, null) } else Icon(icon, null) },
                            label = { Text(destination.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (page == destination) FontWeight.SemiBold else FontWeight.Medium) }, modifier = Modifier.testTag("nav-${destination.name}"),
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = p.green, selectedTextColor = p.green,
                                unselectedIconColor = p.muted, unselectedTextColor = p.muted, indicatorColor = p.softGreen))
                    }
                }
            }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (detail != null) {
                    BackHandler { vm.closeDetail() }
                    DetailScreen(vm, detail!!, requestNotifications)
                } else when (page) {
                    Page.SEARCH -> SearchHome(vm, settings, onAlerts = { page = Page.ALERTS }, onWatched = { page = Page.WATCHED }, onHot = { page = Page.HOT })
                    Page.WATCHED -> WatchedScreen(vm, settings, onSearch = { page = Page.SEARCH })
                    Page.ALERTS -> AlertsScreen(vm, settings)
                    Page.HOT -> HotScreen(vm, settings)
                    Page.SETTINGS -> SettingsScreen(vm, settings, requestNotifications, notificationsEnabled)
                }
            }
        }
    }
}

@Composable private fun AppHeader(onAlerts: () -> Unit) {
    val p = LocalRadarPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadarLogo(Modifier.size(35.dp)); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PrețRadar", color = p.text, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-.6).sp)
            Text("ALEGERI INTELIGENTE", color = p.muted, fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp)
        }
        Surface(color = p.card, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, p.border)) {
            IconButton(onClick = onAlerts) { Icon(Icons.Outlined.NotificationsNone, "Vezi alertele", tint = p.text) }
        }
    }
}

@Composable private fun DemoBanner(vm: RadarViewModel) {
    val p = LocalRadarPalette.current
    Surface(shape = RoundedCornerShape(12.dp), color = p.softAmber, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mod demonstrativ · prețuri fictive", color = p.amber, fontSize = 12.sp, lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.setDemo(false) }, modifier = Modifier.testTag("exit-demo")) { Text("Date reale", color = p.amber, fontSize = 12.sp) }
        }
    }
}

@Composable private fun SearchHome(vm: RadarViewModel, settings: Settings, onAlerts: () -> Unit, onWatched: () -> Unit, onHot: () -> Unit) {
    val p = LocalRadarPalette.current
    val query by vm.query.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searched by vm.searched.collectAsStateWithLifecycle()
    val allResults by vm.results.collectAsStateWithLifecycle()
    val results = allResults.filter { it.source in settings.stores }
    val products by vm.products.collectAsStateWithLifecycle()
    val offers by vm.snapshots.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var choosingSources by remember { mutableStateOf(false) }
    if (choosingSources) AlertDialog(onDismissRequest = { choosingSources = false },
        title = { Text("Magazine și surse") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Magazine listate pe Compari.ro, verificate direct.", fontSize = 12.sp, color = p.muted)
            Store.entries.filter { it.directPartner || it == Store.COMPARI }.forEach { store ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(store.label)
                        if (store == Store.COMPARI) Text("Agregator opțional · acces limitat", fontSize = 11.sp, color = p.muted)
                    }
                    Switch(store in settings.stores, { vm.toggleStore(store) }, modifier = Modifier.testTag("source-toggle-${store.name}"))
                }
            }
        } },
        confirmButton = { TextButton(onClick = { choosingSources = false }) { Text("Gata") } })
    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeader(onAlerts) }
        if (settings.demo) item { DemoBanner(vm) }
        item { HomeHero(query, { vm.query.value = it }, compact = searched) { vm.search() } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CAUTĂ ÎN", fontSize = 10.sp, color = p.muted, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${settings.stores.size} surse selectate", fontSize = 12.sp, color = p.muted)
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Store.ALTEX, Store.EMAG).forEach { store -> SourceChip(store, store in settings.stores, { vm.toggleStore(store) }, Modifier.weight(1f)) }
            Surface(onClick = { choosingSources = true }, shape = RoundedCornerShape(14.dp), color = p.card,
                border = BorderStroke(1.dp, p.border), modifier = Modifier.weight(1f).testTag("choose-sources")) {
                val fontScale = LocalDensity.current.fontScale
                BoxWithConstraints(Modifier.heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    val showIcon = maxWidth >= (92f * fontScale).dp
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (showIcon) {
                            Icon(Icons.Outlined.Storefront, null, Modifier.size(22.dp), tint = p.green)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text("Magazine", color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } }
        if (searched) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rezultatele căutării", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.searched.value = false }) { Text("Închide") }
            } }
            if (searching) item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(26.dp)); Spacer(Modifier.height(12.dp)); Text("Caut în sursele selectate…", color = p.muted)
            } }
            items(results.flatMap { it.items }, key = { it.id }) { candidate -> CandidateCard(candidate) { vm.open(candidate) } }
            items(results.filter { it.error != null }, key = { "error-${it.source.name}" }) { result -> SourceIssue("${result.source.label}: ${result.error}") }
            if (!searching && results.flatMap { it.items }.isEmpty()) item {
                val allFailed = results.isNotEmpty() && results.all { it.error != null }
                EmptyPanel(if (allFailed) "Sursele nu au putut fi verificate" else "Niciun produs care corespunde căutării",
                    if (allFailed) "Verifică mesajele magazinelor de mai sus sau deschide căutarea în browser. Reîncearcă mai târziu."
                    else "Verifică denumirea sau codul modelului. Produsele similare sunt excluse. Sursele cu erori nu au putut fi verificate.")
            }
            if (!settings.demo && !searching) item { BrowserSearchButtons(query, settings.stores) }
        } else {
            item { HomeOverview(if (settings.demo) DemoData.candidates.size else products.size,
                if (settings.demo) 2 else alerts.count { !it.read }, onWatched, onAlerts) }
            item { HotDiscovery(onHot) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Produsele urmărite", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onWatched, contentPadding = PaddingValues(0.dp)) { Text("Vezi toate", fontSize = 13.sp) }
            } }
            if (settings.demo) items(DemoData.candidates, key = { it.id }) { candidate ->
                ProductCard(candidate.title, candidate.imageUrl, DemoData.offers(candidate.id).first(),
                    previous = if (candidate.id == "demo-sony") 129900 else null,
                    points = DemoData.history(candidate.id).map { it.price },
                    subtitle = if (candidate.id == "demo-samsung") "128 GB · Negru" else "", onClick = { vm.open(candidate) })
            } else items(products.take(6), key = { it.id }) { product ->
                val best = AlertEngine.minima(offers.filter { it.productId == product.id }).firstOrNull()
                val lastAlert = alerts.firstOrNull { it.productId == product.id && it.store == best?.store }
                ProductCard(product.title, product.imageUrl, best, lastAlert?.oldPrice,
                    points = lastAlert?.oldPrice?.let { listOf(it, lastAlert.newPrice) } ?: emptyList(),
                    error = product.error, onClick = { vm.openTracked(product) })
            }
            if (!settings.demo && products.isEmpty()) item {
                EmptyPanel("Urmărește primul produs", "Scrie denumirea în căutare, alege modelul exact și activează alertele.")
                TextButton(onClick = { vm.setDemo(true) }, modifier = Modifier.fillMaxWidth().testTag("enter-demo")) { Text("Vezi exemplele din machetă") }
            }
            item { Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = p.green)
                Column {
                    Text("Verificare automată: ${if (settings.intervalHours == 1) "la o oră" else "la ${settings.intervalHours} ore"}", fontSize = 12.sp, color = p.text)
                    Text("Alerte pentru produsele urmărite, chiar și cu aplicația închisă.", fontSize = 12.sp, lineHeight = 17.sp, color = p.muted)
                }
            } }
            if (settings.demo || products.isNotEmpty()) item { RefreshRow(refreshing, if (settings.demo) System.currentTimeMillis() - 12 * 60000 else products.maxOfOrNull { it.lastChecked } ?: 0, vm::refresh) }
        }
    }
}

@Composable private fun WatchedScreen(vm: RadarViewModel, settings: Settings, onSearch: () -> Unit) {
    val products by vm.products.collectAsStateWithLifecycle()
    val offers by vm.snapshots.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val p = LocalRadarPalette.current
    LazyColumn(Modifier.fillMaxSize().testTag("watched-list"), contentPadding = PaddingValues(20.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("COLECȚIA TA", color = p.green, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(7.dp))
            Text("Produsele urmărite", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("Toate prețurile care te interesează, la un loc.", color = p.muted, fontSize = 13.sp)
        }
        if (settings.demo) item { DemoBanner(vm) }
        if (settings.demo) items(DemoData.candidates) { product -> ProductCard(product.title, product.imageUrl, DemoData.offers(product.id).first(), points = DemoData.history(product.id).map { it.price }) { vm.open(product) } }
        else items(products, key = { it.id }) { product ->
            ProductCard(product.title, product.imageUrl, AlertEngine.minima(offers.filter { it.productId == product.id }).firstOrNull(), error = product.error) { vm.openTracked(product) }
        }
        if (!settings.demo && products.isEmpty()) item {
            EmptyPanel("Lista ta începe aici", "Adaugă produsele pentru care vrei să afli când apare o ofertă mai bună.")
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("Caută un produs") }
        }
        if (settings.demo || products.isNotEmpty()) item { RefreshRow(refreshing, if (settings.demo) System.currentTimeMillis() - 12 * 60000 else products.maxOfOrNull { it.lastChecked } ?: 0, vm::refresh) }
    }
}

@Composable fun CandidateCard(candidate: ProductCandidate, onClick: () -> Unit) {
    val p = LocalRadarPalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = p.card, border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductThumbnail(candidate.title, candidate.imageUrl, Modifier.size(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(candidate.source.label, color = p.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(candidate.title, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    candidate.price?.let { PriceFigure(it, p.text, 23) }
                        ?: Text("Vezi prețul la magazin", color = p.muted, fontSize = 12.sp)
                }
                Surface(color = p.softGreen, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ChevronRight, null, tint = p.green, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable fun SourceIssue(text: String) {
    val p = LocalRadarPalette.current
    Surface(color = p.softAmber, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = p.amber); Spacer(Modifier.width(8.dp)); Text(text, color = p.amber, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
    }
}

@Composable fun EmptyPanel(title: String, body: String, icon: ImageVector = Icons.Outlined.BookmarkBorder) {
    val p = LocalRadarPalette.current
    Surface(color = p.card, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = p.softGreen, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = p.green, modifier = Modifier.size(27.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = p.text, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp)); Text(body, color = p.muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable private fun RefreshRow(loading: Boolean, time: Long, refresh: () -> Unit) {
    val p = LocalRadarPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(17.dp), tint = p.muted)
            Spacer(Modifier.width(6.dp)); Text("Ultima verificare: ${relativeTime(time)}", color = p.muted, fontSize = 11.sp)
        }
        OutlinedButton(onClick = refresh, enabled = !loading, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Verifică acum", fontSize = 12.sp)
        }
    }
}

@Composable fun BrowserSearchButtons(query: String, stores: Set<Store>) {
    val context = LocalContext.current
    Column {
        stores.forEach { store -> TextButton(onClick = {
            openBrowser(context, store.searchUrl(query))
        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(15.dp)); Spacer(Modifier.width(8.dp)); Text("Caută pe ${store.label} în browser") } }
    }
}

fun openBrowser(context: android.content.Context, url: String) {
    if (RemoteUrls.httpsUri(url) == null) return
    val uri = android.net.Uri.parse(url)
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
}
