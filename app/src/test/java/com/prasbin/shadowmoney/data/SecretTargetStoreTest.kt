package com.prasbin.shadowmoney.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SecretTargetStoreTest {

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun createDataStore() {
        val tempDir = createTempDir("secret_target_test").absoluteFile
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Default)) {
            File(tempDir, "secret_target_test.preferences_pb")
        }
    }

    private fun store(): SecretTargetStore = SecretTargetStore(dataStore)

    @Test
    fun initiallyNull() = runBlocking {
        assertNull(store().getTarget())
    }

    @Test
    fun setTarget_persistsExactMinorUnits() = runBlocking {
        val store = store()
        store.setTarget(1_000_000L)
        assertEquals(1_000_000L, store.getTarget())
    }

    @Test
    fun updateTarget_multipleTimes_noMonthlyLock() = runBlocking {
        val store = store()
        store.setTarget(100_000L)
        assertEquals(100_000L, store.getTarget())
        store.setTarget(250_000L)
        assertEquals(250_000L, store.getTarget())
        store.setTarget(50_000L)
        assertEquals(50_000L, store.getTarget())
        store.setTarget(999_999L)
        assertEquals(999_999L, store.getTarget())
    }

    @Test
    fun negativeTarget_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store().setTarget(-1L) }
        }
        Unit
    }

    @Test
    fun zeroTarget_allowed() = runBlocking {
        store().setTarget(0L)
        assertEquals(0L, store().getTarget())
    }

    @Test
    fun clear_removesTarget() = runBlocking {
        val store = store()
        store.setTarget(500_000L)
        store.clear()
        assertNull(store.getTarget())
    }

    @Test
    fun observeTarget_emitsUpdates() = runBlocking {
        val store = store()
        store.setTarget(10_000L)
        assertEquals(10_000L, store.observeTarget().first())
        store.setTarget(20_000L)
        assertEquals(20_000L, store.observeTarget().first())
    }

    @Test
    fun storageSurvivesNewStoreInstance() = runBlocking {
        val first = store()
        first.setTarget(777_777L)

        val second = store()
        assertEquals(777_777L, second.getTarget())
    }

    @Test
    fun storedAsLong_notFloatingPoint() = runBlocking {
        val store = store()
        store.setTarget(123_456L)
        val preferences = dataStore.data.first()
        val value = preferences[longPreferencesKey("secret_target_minor")]
        assertTrue(value is Long)
        assertEquals(123_456L, value)
    }
}
