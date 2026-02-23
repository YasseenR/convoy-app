package edu.temple.convoy

import android.R.id.message
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MyFirebaseMessagingService: FirebaseMessagingService() {

    companion object {
        const val ACTION_CONVOY_UPDATE = "edu.temple.convoy.CONVOY_UPDATE"
        const val EXTRA_PAYLOAD = "payload"
    }
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val store = SessionStore(applicationContext)



        CoroutineScope(Dispatchers.IO).launch {
            store.saveFCMToken(token)
        }

        Log.d("FCM", "Refreshed token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message Received")

        remoteMessage.data["payload"]?.let { payload ->
            val intent = Intent(ACTION_CONVOY_UPDATE).apply {
                putExtra(EXTRA_PAYLOAD, payload)
                setPackage(packageName)
            }

            Log.d("FCM", "Refreshed token: $payload")
            sendBroadcast(intent)
        }


    }
}