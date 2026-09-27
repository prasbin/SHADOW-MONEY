package com.prasbin.shadowmoney.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.secretTargetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secret_target_preferences"
)

class SecretTargetStore(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val SECRET_TARGET_MINOR = longPreferencesKey("secret_target_minor")

        fun create(context: Context): SecretTargetStore =
            SecretTargetStore(context.applicationContext.secretTargetDataStore)
    }

    fun observeTarget(): Flow<Long?> = dataStore.data.map { preferences ->
        preferences[SECRET_TARGET_MINOR]
    }

    suspend fun getTarget(): Long? = observeTarget().first()

    suspend fun setTarget(amountMinor: Long) {
        require(amountMinor >= 0L) { "Secret target cannot be negative" }
        dataStore.edit { preferences ->
            preferences[SECRET_TARGET_MINOR] = amountMinor
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(SECRET_TARGET_MINOR)
        }
    }
}
