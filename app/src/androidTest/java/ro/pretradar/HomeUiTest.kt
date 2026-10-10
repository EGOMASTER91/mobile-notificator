package ro.pretradar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import ro.pretradar.domain.ThemeChoice

@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun reach(screen: String, matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val list = compose.onNodeWithTag(screen)
        list.performScrollToNode(matcher)
        val node = compose.onNode(matcher).performScrollTo()
        // A nested horizontal row may be composed below the vertical viewport.
        // Centre it vertically before injecting a touch, so the bottom navigation cannot receive it.
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val layout = node.fetchSemanticsNode()
        val height = layout.size.height.toFloat()
        val centerY = layout.positionInRoot.y + height / 2
        if (centerY > viewport.bottom - height || centerY < viewport.top + height) {
            list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll ->
                scroll(0f, centerY - viewport.center.y)
            }
        }
        return node
    }
    @Before fun reset() = runBlocking {
        val repository = (compose.activity.application as RadarApplication).repository
        repository.preferences.setDemo(false); repository.preferences.setTheme(ThemeChoice.LIGHT)
    }
    @Test fun previewHasProductsAndNavigatesToComparison() {
        reach("home", hasTestTag("enter-demo")).performClick()
        reach("home", hasText("Mod demonstrativ", substring = true)).assertExists()
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).performClick()
        compose.onNodeWithTag("product-details").assertExists()
        compose.onNodeWithText("Cel mai mic preț verificat").assertExists()
        compose.onNodeWithContentDescription("Înapoi").performClick()
        compose.onNodeWithTag("home").assertExists()
    }
    @Test fun themeAndPreviewCanBeChangedFromSettings() {
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("theme-DARK").performClick()
        compose.onNodeWithTag("theme-DARK").assertIsSelected()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("demo-switch"))
        compose.onNodeWithTag("demo-switch").performClick()
        compose.onNodeWithTag("nav-SEARCH").performClick()
        compose.onNodeWithText("Mod demonstrativ", substring = true).assertExists()
        compose.onNodeWithTag("exit-demo").performClick()
        reach("home", hasText("Urmărește primul produs")).assertExists()
    }
    @Test fun partnerSourcesCanBeSelectedWithoutCrowdingTheHomePage() {
        compose.onNodeWithTag("choose-sources").performClick()
        compose.onNodeWithText("ForIT").assertExists()
        compose.onNodeWithText("ITGalaxy").assertExists()
        compose.onNodeWithText("evoMAG").assertExists()
        val repository = (compose.activity.application as RadarApplication).repository
        runBlocking { repository.preferences.setStores(ro.pretradar.domain.Store.defaults) }
        compose.onNodeWithTag("source-toggle-FORIT").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Gata").performClick()
        compose.onNodeWithTag("home").assertExists()
        runBlocking { repository.preferences.setStores(ro.pretradar.domain.Store.defaults) }
    }
    @Test fun searchShowsOnlyTheRequestedDemoProductAndNeverFallsBackToRecommendations() {
        reach("home", hasTestTag("enter-demo")).performClick()
        reach("home", hasTestTag("search-input")).performTextInput("WH1000XM5")
        compose.onNodeWithTag("submit-search").performClick()
        reach("home", hasText("Sony WH-1000XM5") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("Samsung Galaxy S24").assertDoesNotExist()
        compose.onNodeWithText("Philips Airfryer").assertDoesNotExist()
        reach("home", hasTestTag("search-input")).performTextReplacement("WH1000XM4")
        compose.onNodeWithTag("submit-search").performClick()
        reach("home", hasText("Niciun produs care corespunde căutării")).assertExists()
        compose.onNodeWithText("Sony WH-1000XM5").assertDoesNotExist()
    }
    @Test fun batterySettingsCanBeConfigured() {
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        val repository = (compose.activity.application as RadarApplication).repository
        runBlocking { repository.preferences.setWifiOnly(false); repository.preferences.setInterval(6) }
        reach("settings-list", hasTestTag("interval-24")).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("interval-24") and isSelected()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("interval-24").assertIsSelected()
        reach("settings-list", hasTestTag("wifi-only")).assertIsOff().performClick().assertIsOn()
        runBlocking { repository.preferences.setWifiOnly(false); repository.preferences.setInterval(6) }
    }
}
