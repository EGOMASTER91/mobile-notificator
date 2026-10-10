package ro.pretradar.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class TrackedProduct(
    @PrimaryKey val id: String,
    val title: String,
    val imageUrl: String,
    val linksJson: String,
    val threshold: Long? = null,
    val enabled: Boolean = true,
    val lastChecked: Long = 0,
    val error: String = ""
)
@Entity(tableName = "snapshots", primaryKeys = ["productId", "merchantKey"])
data class Snapshot(
    val productId: String, val merchantKey: String, val source: String,
    val offerJson: String
)
@Entity(tableName = "history", indices = [Index("productId")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String, val store: String, val price: Long, val time: Long
)
@Entity(tableName = "alerts", indices = [Index("productId")])
data class SavedAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String, val title: String, val store: String,
    val oldPrice: Long?, val newPrice: Long, val promotionJson: String?,
    val createdAt: Long, val read: Boolean = false
)

@Dao
interface RadarDao {
    @Query("SELECT * FROM products ORDER BY title") fun observeProducts(): Flow<List<TrackedProduct>>
    @Query("SELECT * FROM products WHERE enabled = 1") suspend fun activeProducts(): List<TrackedProduct>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: String): TrackedProduct?
    @Upsert suspend fun saveProduct(product: TrackedProduct)
    @Query("SELECT * FROM snapshots") fun observeSnapshots(): Flow<List<Snapshot>>
    @Query("SELECT * FROM snapshots WHERE productId = :id") suspend fun snapshots(id: String): List<Snapshot>
    @Upsert suspend fun saveSnapshots(snapshots: List<Snapshot>)
    @Insert suspend fun addHistory(history: List<HistoryEntry>)
    @Query("SELECT * FROM history WHERE productId = :id ORDER BY time") fun history(id: String): Flow<List<HistoryEntry>>
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC") fun observeAlerts(): Flow<List<SavedAlert>>
    @Insert suspend fun addAlert(alert: SavedAlert): Long
    @Query("UPDATE alerts SET `read` = 1 WHERE id = :id") suspend fun markRead(id: Long)
    @Query("UPDATE alerts SET `read` = 1") suspend fun markAllRead()
    @Query("DELETE FROM products WHERE id = :id") suspend fun deleteProduct(id: String)
    @Query("DELETE FROM snapshots WHERE productId = :id") suspend fun deleteSnapshots(id: String)
    @Query("DELETE FROM history WHERE productId = :id") suspend fun deleteHistory(id: String)
    @Query("DELETE FROM alerts WHERE productId = :id") suspend fun deleteAlerts(id: String)
    @Query("DELETE FROM history WHERE time < :cutoff") suspend fun pruneHistory(cutoff: Long)
}

@Database(entities = [TrackedProduct::class, Snapshot::class, HistoryEntry::class, SavedAlert::class], version = 1, exportSchema = false)
abstract class RadarDatabase : RoomDatabase() { abstract fun dao(): RadarDao }
