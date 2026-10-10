package ro.pretradar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ro.pretradar.domain.*

@RunWith(AndroidJUnit4::class)
class HotUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = (compose.activity.application as RadarApplication).repository.preferences
    @Before fun reset() = runBlocking {
        preferences.setStores(Store.defaults); preferences.setHotCategory(HotCategory.HEADPHONES)
        preferences.setHotMinimum(30); preferences.setDemo(true)
    }
    @After fun restore() = runBlocking {
        preferences.setStores(Store.defaults); preferences.setHotCategory(HotCategory.PHONES)
        preferences.setHotMinimum(30); preferences.setDemo(false)
    }
    @Test fun hotThresholdFiltersLocallyAndPersists() {
        compose.onNodeWithTag("nav-HOT").performClick()
        reach("hot-deal-demo-sony").assertExists()
        reach("hot-preset-70").performClick()
        reach("hot-empty").assertExists()
        compose.onNodeWithTag("hot-deal-demo-sony").assertDoesNotExist()
        assertEquals(70, runBlocking { preferences.settings.first().hotMinimum })
        compose.onNodeWithTag("nav-SEARCH").performClick()
        compose.onNodeWithTag("nav-HOT").performClick()
        compose.onNodeWithTag("hot-minimum").assertTextEquals("70%")
    }
    @Test fun categorySelectionAndProductDetailsWork() {
        compose.onNodeWithTag("nav-HOT").performClick()
        reach("hot-choose-category").performClick()
        compose.onNodeWithTag("hot-category-PHONES").performClick()
        reach("hot-deal-demo-samsung").performClick()
        compose.onNodeWithTag("product-details").assertExists()
        compose.onNodeWithContentDescription("Înapoi").performClick()
        compose.onNodeWithTag("hot-list").assertExists()
        assertEquals(HotCategory.PHONES, runBlocking { preferences.settings.first().hotCategory })
    }
    @Test fun sourceAndMinimumFiltersCompose() {
        compose.onNodeWithTag("nav-HOT").performClick()
        reach("hot-preset-10").performClick()
        reach("hot-deal-demo-sony-second").assertExists()
        reach("hot-source-EMAG").performClick()
        compose.onNodeWithTag("hot-deal-demo-sony-second").assertDoesNotExist()
        reach("hot-deal-demo-sony").assertExists()
    }

    private fun reach(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("hot-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
}
