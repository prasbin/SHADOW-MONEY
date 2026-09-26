package com.prasbin.shadowmoney

import androidx.room.Room
import com.prasbin.shadowmoney.data.*
import com.prasbin.shadowmoney.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoalDatabaseTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var goalDao: GoalDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        goalDao = database.goalDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetGoal() = runBlocking {
        val goal = Goal(name = "Save 100K", targetAmountMinor = 10000000L)
        val id = goalDao.insert(goal)
        assertTrue(id > 0)

        val fetched = goalDao.getById(id)
        assertNotNull(fetched)
        assertEquals("Save 100K", fetched?.name)
        assertEquals(10000000L, fetched?.targetAmountMinor)
    }

    @Test
    fun markComplete_setsCompleted() = runBlocking {
        val goal = Goal(name = "Goal", targetAmountMinor = 5000000L)
        val id = goalDao.insert(goal)

        goalDao.markComplete(id)
        val fetched = goalDao.getById(id)
        assertNotNull(fetched)
        assertTrue(fetched!!.isCompleted)
    }

    @Test
    fun archiveGoal_setsInactive() = runBlocking {
        val goal = Goal(name = "Old Goal", targetAmountMinor = 1000000L)
        val id = goalDao.insert(goal)

        goalDao.archive(id)
        val fetched = goalDao.getById(id)
        assertNotNull(fetched)
        assertFalse(fetched!!.isActive)
    }
}