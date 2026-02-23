package edu.temple.convoy

import android.R.id.message
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MyFirebaseMessagingService: FirebaseMessagingService() {
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


        val payload = remoteMessage.data["payload"] ?: return

        try {
            val json = JSONObject(payload)
            val action = json.getString("action")
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: send token to backend / Firestore / API
    }
}