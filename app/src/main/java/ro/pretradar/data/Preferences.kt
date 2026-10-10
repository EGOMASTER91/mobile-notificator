package ro.pretradar.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import ro.pretradar.domain.*
import java.io.IOException

private val Context.dataStore by preferencesDataStore("radar_settings")
internal fun migrateBatteryDefaults(p: MutablePreferences) {
    val powerVersion = intPreferencesKey("power_version")
    val interval = intPreferencesKey("interval_hours")
    if ((p[powerVersion] ?: 0) < 1) {
        if (p[interval] == null) p[interval] = 6
        p[powerVersion] = 1
    }
}
class Preferences(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val interval = intPreferencesKey("interval_hours")
    private val stores = stringSetPreferencesKey("stores")
    private val demo = booleanPreferencesKey("demo")
    private val sourceVersion = intPreferencesKey("source_version")
    private val wifiOnly = booleanPreferencesKey("wifi_only")
    private val cursor = stringPreferencesKey("background_cursor")
    private val hotMinimum = intPreferencesKey("hot_minimum")
    private val hotCategory = stringPreferencesKey("hot_category")
    suspend fun optimizeDefaults() { context.dataStore.edit(::migrateBatteryDefaults) }
    suspend fun upgradeSources() { context.dataStore.edit { p ->
        if ((p[sourceVersion] ?: 0) < 2) {
            p[stores] = p[stores]?.plus(Store.entries.filter { it.directPartner }.map { it.name })
                ?: Store.defaults.map { it.name }.toSet()
            p[sourceVersion] = 2
        }
    } }
    val settings: Flow<Settings> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p -> Settings(
        theme = runCatching { ThemeChoice.valueOf(p[theme] ?: "SYSTEM") }.getOrDefault(ThemeChoice.SYSTEM),
        intervalHours = (p[interval] ?: 6).coerceIn(1, 24),
        stores = p[stores]?.mapNotNull { runCatching { Store.valueOf(it) }.getOrNull() }?.toSet() ?: Store.defaults,
        demo = p[demo] ?: false,
        wifiOnly = p[wifiOnly] ?: false,
        hotMinimum = (p[hotMinimum] ?: 30).coerceIn(0, 99),
        hotCategory = runCatching { HotCategory.valueOf(p[hotCategory] ?: "PHONES") }.getOrDefault(HotCategory.PHONES)
    ) }
    suspend fun setTheme(value: ThemeChoice) { context.dataStore.edit { it[theme] = value.name } }
    suspend fun setInterval(value: Int) { context.dataStore.edit { it[interval] = value.coerceIn(1, 24) } }
    suspend fun setStores(value: Set<Store>) { context.dataStore.edit { it[stores] = value.map { s -> s.name }.toSet() } }
    suspend fun setDemo(value: Boolean) { context.dataStore.edit { it[demo] = value } }
    suspend fun setWifiOnly(value: Boolean) { context.dataStore.edit { it[wifiOnly] = value } }
    suspend fun setHotMinimum(value: Int) { context.dataStore.edit { it[hotMinimum] = value.coerceIn(0, 99) } }
    suspend fun setHotCategory(value: HotCategory) { context.dataStore.edit { it[hotCategory] = value.name } }
    suspend fun backgroundCursor(): String? = context.dataStore.data.first()[cursor]
    suspend fun setBackgroundCursor(id: String) { context.dataStore.edit { it[cursor] = id } }
}
