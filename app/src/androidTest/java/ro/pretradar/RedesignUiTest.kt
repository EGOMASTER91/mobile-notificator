package ro.pretradar

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ro.pretradar.data.Preferences
import ro.pretradar.data.RadarDatabase
import ro.pretradar.data.RadarRepository
import ro.pretradar.domain.HotCategory
import ro.pretradar.domain.Settings
import ro.pretradar.domain.Store
import ro.pretradar.domain.ThemeChoice
import ro.pretradar.ui.RadarApp
import ro.pretradar.ui.RadarViewModel

/** UI fixtures use a separate empty database and no merchant adapters. */
@RunWith(AndroidJUnit4::class)
class RedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val owner = ViewModelStore()
    private lateinit var database: RadarDatabase
    private lateinit var preferences: Preferences
    private lateinit var vm: RadarViewModel
    private var originalSettings: Settings? = null

    @Before fun prepareIsolatedUi() {
        preferences = (compose.activity.application as RadarApplication).repository.preferences
        runBlocking {
            originalSettings = preferences.settings.first()
            preferences.setTheme(ThemeChoice.LIGHT)
            preferences.setDemo(true)
            preferences.setStores(Store.defaults)
            preferences.setHotCategory(HotCategory.HEADPHONES)
            preferences.setHotMinimum(10)
            preferences.setInterval(6)
            preferences.setWifiOnly(false)
        }
        database = Room.inMemoryDatabaseBuilder(compose.activity, RadarDatabase::class.java).build()
        val repository = RadarRepository(database, preferences, emptyList()) { _, _ -> }
        compose.runOnIdle {
            vm = RadarViewModel(repository)
            owner.put("redesign", vm)
            compose.activity.setContent { RadarApp(vm, requestNotifications = {}, notificationsEnabled = { false }) }
        }
        runBlocking { withTimeout(10_000) { vm.settings.first { it.demo && it.hotCategory == HotCategory.HEADPHONES } } }
        visibleScreen("home")
    }

    @After fun restore() {
        compose.runOnIdle {
            compose.activity.setContent { }
            owner.clear()
        }
        if (::database.isInitialized) database.close()
        originalSettings?.let { original -> runBlocking {
            preferences.setTheme(original.theme)
            preferences.setDemo(original.demo)
            preferences.setStores(original.stores)
            preferences.setHotCategory(original.hotCategory)
            preferences.setHotMinimum(original.hotMinimum)
            preferences.setInterval(original.intervalHours)
            preferences.setWifiOnly(original.wifiOnly)
        } }
    }

    @Test fun allFiveTabsAndDemoProductBackNavigationRemainReachable() {
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).assertIsDisplayed()
        navigate("HOT", "hot-list")
        reach("hot-list", hasTestTag("hot-deal-demo-sony")).assertIsDisplayed()
        navigate("WATCHED", "watched-list")
        reach("watched-list", hasText("Sony WH-1000XM5") and !hasSetTextAction()).assertIsDisplayed()
        navigate("ALERTS", "alerts-list")
        reach("alerts-list", hasText("Sony WH-1000XM5") and !hasSetTextAction()).assertIsDisplayed()
        navigate("SETTINGS", "settings-list")
        reach("settings-list", hasTestTag("theme-LIGHT")).assertIsSelected()
        navigate("SEARCH", "home")
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).performClick()
        visibleScreen("product-details")
        reach("product-details", hasText("Cel mai mic preț verificat")).assertIsDisplayed()
        reach("product-details", hasContentDescription("Înapoi")).performClick()
        visibleScreen("home")
        listOf("SEARCH", "HOT", "WATCHED", "ALERTS", "SETTINGS").forEach {
            compose.onNodeWithTag("nav-$it").assertIsDisplayed()
        }
    }

    @Test fun themeChoicePersistsAcrossTheRedesignedScreens() {
        navigate("SETTINGS", "settings-list")
        select("settings-list", "theme-DARK")
        navigate("SEARCH", "home")
        navigate("HOT", "hot-list")
        navigate("SETTINGS", "settings-list")
        reach("settings-list", hasTestTag("theme-DARK")).assertIsSelected()
        select("settings-list", "theme-LIGHT")
        navigate("SEARCH", "home")
        navigate("SETTINGS", "settings-list")
        reach("settings-list", hasTestTag("theme-LIGHT")).assertIsSelected()
    }

    @Test fun emptyListsGuideTheUserWithoutRequiringDemoData() {
        runBlocking {
            preferences.setDemo(false)
            withTimeout(10_000) { vm.settings.first { !it.demo } }
        }
        navigate("WATCHED", "watched-list")
        reach("watched-list", hasText("Lista ta începe aici")).assertIsDisplayed()
        reach("watched-list", hasText("Caută un produs")).performClick()
        visibleScreen("home")
        navigate("ALERTS", "alerts-list")
        reach("alerts-list", hasText("Încă nu ai alerte")).assertIsDisplayed()
        assertTrue(runBlocking { vm.repository.products.first().isEmpty() })
        assertTrue(runBlocking { vm.repository.alerts.first().isEmpty() })
    }

    /** Also exercises overflow paths when the emulator uses larger text or a compact width. */
    @Test fun settingsAndHotControlsRemainReachableWhenContentNeedsToScroll() {
        navigate("SETTINGS", "settings-list")
        select("settings-list", "theme-DARK")
        select("settings-list", "theme-LIGHT")
        select("settings-list", "interval-24")
        navigate("HOT", "hot-list")
        select("hot-list", "hot-preset-70")
        reach("hot-list", hasTestTag("hot-choose-category")).performClick()
        reach("hot-categories", hasTestTag("hot-category-PHONES")).performClick()
        visibleScreen("hot-list")
        reach("hot-list", hasTestTag("hot-minimum")).assertTextEquals("70%")
        runBlocking { withTimeout(10_000) {
            vm.settings.first { it.intervalHours == 24 && it.hotCategory == HotCategory.PHONES && it.hotMinimum == 70 }
        } }
    }

    @Test fun captureDemoDesignForVisualReview() {
        assumeTrue("Design capture is opt-in", InstrumentationRegistry.getArguments().getString("designCapture") == "true")
        capture("search-light.png", "home")
        reach("home", hasTestTag("search-input")).performTextInput("Sony WH-1000XM5")
        compose.onNodeWithTag("submit-search").performClick()
        runBlocking { withTimeout(10_000) {
            vm.results.first { results -> results.flatMap { it.items }.any { it.id == "demo-sony" } }
        } }
        compose.runOnIdle {
            compose.activity.currentFocus?.clearFocus()
            (compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(10_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true
        }
        compose.onNodeWithTag("home").performScrollToIndex(0)
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).assertIsDisplayed()
        capture("search-results-light.png", "home")
        reach("home", hasText("Închide")).performClick()
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).assertIsDisplayed()
        capture("home-products-light.png", "home")
        navigate("HOT", "hot-list")
        runBlocking { withTimeout(10_000) { vm.hot.first { !it.loading && it.loadedAt > 0 } } }
        compose.onNodeWithTag("hot-list").performScrollToIndex(0)
        capture("hot-light.png", "hot-list")
        reach("hot-list", hasTestTag("hot-deal-demo-sony")).assertIsDisplayed()
        capture("hot-offers-light.png", "hot-list")
        compose.onNodeWithTag("hot-list").performScrollToIndex(0)
        navigate("WATCHED", "watched-list")
        capture("watched-light.png", "watched-list")
        navigate("ALERTS", "alerts-list")
        capture("alerts-light.png", "alerts-list")
        navigate("SETTINGS", "settings-list")
        capture("settings-light.png", "settings-list")
        navigate("SEARCH", "home")
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).performClick()
        visibleScreen("product-details")
        reach("product-details", hasText("Cel mai mic preț verificat")).assertIsDisplayed()
        capture("detail-light.png", "product-details")
        reach("product-details", hasContentDescription("Înapoi")).performClick()
        navigate("SETTINGS", "settings-list")
        reach("settings-list", hasTestTag("theme-DARK")).performClick().assertIsSelected()
        capture("settings-dark.png", "settings-list")
        navigate("SEARCH", "home")
        compose.onNodeWithTag("home").performScrollToIndex(0)
        capture("search-dark.png", "home")
        navigate("HOT", "hot-list")
        runBlocking { withTimeout(10_000) { vm.hot.first { !it.loading && it.loadedAt > 0 } } }
        compose.onNodeWithTag("hot-list").performScrollToIndex(0)
        capture("hot-dark.png", "hot-list")
        runBlocking {
            preferences.setTheme(ThemeChoice.LIGHT)
            preferences.setDemo(false)
            withTimeout(10_000) { vm.settings.first { !it.demo && it.theme == ThemeChoice.LIGHT } }
        }
        navigate("SEARCH", "home")
        reach("home", hasText("Urmărește primul produs")).assertIsDisplayed()
        capture("empty-home-light.png", "home")
        navigate("WATCHED", "watched-list")
        reach("watched-list", hasText("Lista ta începe aici")).assertIsDisplayed()
        capture("empty-watched-light.png", "watched-list")
        navigate("ALERTS", "alerts-list")
        reach("alerts-list", hasText("Încă nu ai alerte")).assertIsDisplayed()
        capture("empty-alerts-light.png", "alerts-list")
    }

    private fun navigate(page: String, screen: String) {
        compose.onNodeWithTag("nav-$page").performClick()
        visibleScreen(screen)
    }

    private fun reach(list: String, matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val container = compose.onNodeWithTag(list)
        container.performScrollToNode(matcher)
        val node = compose.onNode(matcher).performScrollTo()
        val viewport = container.fetchSemanticsNode().boundsInRoot
        val layout = node.fetchSemanticsNode()
        val height = layout.size.height.toFloat()
        val centerY = layout.positionInRoot.y + height / 2
        if (centerY > viewport.bottom - height || centerY < viewport.top + height) {
            container.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll ->
                scroll(0f, centerY - viewport.center.y)
            }
        }
        return node
    }

    private fun select(list: String, tag: String) {
        reach(list, hasTestTag(tag)).performClick()
        // DataStore persists asynchronously; Compose idleness does not imply the write is complete.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and isSelected()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(tag).assertIsSelected()
    }

    private fun visibleScreen(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun capture(name: String, screen: String) {
        visibleScreen(screen)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "design-capture")
        assertTrue("Cannot create capture directory", directory.isDirectory || directory.mkdirs())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            FileOutputStream(File(directory, name)).use { stream ->
                assertTrue("Cannot save $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally { bitmap.recycle() }
    }
}
