package edu.temple.convoy

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first


class ConvoyLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "convoy_location_channel"
        const val NOTIF_ID = 10001

        // Broadcast action + extras for updates to MainActivity
        const val ACTION_LOCATION = "edu.temple.convoy.ACTION_LOCATION"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
    }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var store: SessionStore


    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        fused = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                for (location in result.locations) {
                    Log.d(
                        "ConvoyLocation",
                        "lat=${location.latitude}, lng=${location.longitude}, speed=${location.speed}"
                    )
                }

                val loc = result.lastLocation ?: return


                // Send location to activity
                val i = Intent(ACTION_LOCATION).apply {
                    putExtra(EXTRA_LAT, loc.latitude)
                    putExtra(EXTRA_LNG, loc.longitude)
                    setPackage(packageName)
                }
                sendBroadcast(i)
                // Also report location to server
                serviceScope.launch {
                    try {
                        val username = store.username.first() ?: return@launch
                        val sessionKey = store.sessionKey.first() ?: return@launch
                        val convoyId = store.convoyId.first() ?: return@launch

                        ApiClient.api.convoy(
                            action = "UPDATE",
                            username = username,
                            sessionKey = sessionKey,
                            convoyId = convoyId,
                            latitude = loc.latitude,
                            longitude = loc.longitude
                        )
                    } catch (e: Exception) {
                        // Silently ignore - don't crash the service
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately
        startForeground(NOTIF_ID, buildNotification())
        Log.d("ConvoyLocation", "Service started")
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted -> stop service so it doesn't crash
            Log.d("ConvoyLocation", "No Permission")
            stopSelf()
            return
        }
        Log.d("ConvoyLocation", "Permissions")
        // Requirement: updates whenever moved 10 meters
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1500)
            .build()

        fused.requestLocationUpdates(request, callback, mainLooper)
    }

    override fun onDestroy() {
        super.onDestroy()
        fused.removeLocationUpdates(callback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Convoy active")
            .setContentText("Sharing location updates...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Convoy Location",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }
}
