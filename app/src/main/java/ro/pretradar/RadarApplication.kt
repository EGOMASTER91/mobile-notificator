package ro.pretradar

import android.app.Application
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ro.pretradar.data.*

class RadarApplication : Application() {
    lateinit var repository: RadarRepository; private set
    lateinit var notifications: RadarNotifications; private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onCreate() {
        super.onCreate()
        notifications = RadarNotifications(this)
        repository = RadarRepository(Room.databaseBuilder(this, RadarDatabase::class.java, "radar.db").build(),
            Preferences(this), createSources(this)) { id, event -> notifications.post(id, event) }
        scope.launch {
            repository.preferences.upgradeSources()
            repository.preferences.optimizeDefaults()
            val scheduler = PriceCheckScheduler(WorkManager.getInstance(this@RadarApplication))
            kotlinx.coroutines.flow.combine(repository.preferences.settings, repository.products) { settings, products ->
                MonitoringPlan(settings.intervalHours, settings.wifiOnly, products.any { product ->
                    product.enabled && repository.links(product).any { it.source in settings.stores }
                })
            }.distinctUntilChanged().collect(scheduler::update)
        }
    }
}
