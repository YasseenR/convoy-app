package edu.temple.convoy

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session_store")

class SessionStore(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val FIRSTNAME = stringPreferencesKey("firstname")
        val LASTNAME = stringPreferencesKey("lastname")
        val SESSION_KEY = stringPreferencesKey("session_key")
        val CONVOY_ID = stringPreferencesKey("convoy_id")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")

        val LAST_CONVOY_CREATED = stringPreferencesKey("last_convoy_created")
    }

    val sessionKey: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_KEY] }
    val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }
    val convoyId: Flow<String?> = context.dataStore.data.map { it[Keys.CONVOY_ID] }

    val fcmToken: Flow<String?> = context.dataStore.data.map { it[Keys.FCM_TOKEN] }

    val lastConvoyCreated: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONVOY_CREATED] }
    suspend fun saveLogin(username: String, first: String?, last: String?, sessionKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            if (first != null) prefs[Keys.FIRSTNAME] = first
            if (last != null) prefs[Keys.LASTNAME] = last
            prefs[Keys.SESSION_KEY] = sessionKey
        }
    }

    suspend fun saveConvoyId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) prefs[Keys.CONVOY_ID] = id
            else prefs.remove(Keys.CONVOY_ID)
        }
    }

    suspend fun saveFCMToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FCM_TOKEN] = token
        }
    }

    suspend fun saveCreatedConvoyId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) prefs[Keys.LAST_CONVOY_CREATED] = id
            else prefs.remove(Keys.LAST_CONVOY_CREATED)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
