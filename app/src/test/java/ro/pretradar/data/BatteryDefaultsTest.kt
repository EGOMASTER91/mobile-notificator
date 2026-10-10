package ro.pretradar.data

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class BatteryDefaultsTest {
    private val interval = intPreferencesKey("interval_hours")
    @Test fun anUnconfiguredInstallationUsesSixHours() {
        val preferences = mutablePreferencesOf()
        migrateBatteryDefaults(preferences)
        assertEquals(6, preferences[interval])
    }
    @Test fun anExplicitIntervalSurvivesUpgradeAndFutureStarts() {
        val preferences = mutablePreferencesOf(interval to 1)
        migrateBatteryDefaults(preferences)
        assertEquals(1, preferences[interval])
        preferences[interval] = 24
        migrateBatteryDefaults(preferences)
        assertEquals(24, preferences[interval])
    }
}
