package ro.pretradar

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ro.pretradar.domain.*

class RadarNotifications(private val context: Context) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ieftiniri și promoții", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Schimbări de preț pentru produsele urmărite" })
    }
    fun enabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun post(id: Long, event: AlertEvent) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!enabled()) return
        val intent = Intent(context, MainActivity::class.java).putExtra("productId", event.productId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val price = event.oldPrice?.let { "${money(it)} → ${money(event.newPrice)}" } ?: money(event.newPrice)
        val text = if (event.promotion != null) "$price · ${event.promotion.title}" else price
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("${event.title} · ${event.store}").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pending)
            .setAutoCancel(true).setGroup("radar-price-alerts").build()
        try { NotificationManagerCompat.from(context).notify(id.toInt(), notification) }
        catch (_: SecurityException) { /* A revoked permission must not retry price checks; the alert is saved locally. */ }
    }
    companion object { const val CHANNEL = "price_changes" }
}
