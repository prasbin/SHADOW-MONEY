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
class CategoryDatabaseTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var categoryDao: CategoryDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        categoryDao = database.categoryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun seedCategories_haveCorrectCount() = runBlocking {
        val categories = SeedCategories.getSystemCategories()
        assertEquals(17, categories.size)
    }

    @Test
    fun seedCategories_haveIncomeAndOutflow() = runBlocking {
        val categories = SeedCategories.getSystemCategories()
        val incomeCount = categories.count { it.direction == CATEGORY_DIRECTION_INCOME }
        val outflowCount = categories.count { it.direction == CATEGORY_DIRECTION_OUTFLOW }
        assertTrue(incomeCount > 0)
        assertTrue(outflowCount > 0)
        assertEquals(incomeCount + outflowCount, categories.size)
    }

    @Test
    fun systemCategories_areMarkedAsSystem() = runBlocking {
        val categories = SeedCategories.getSystemCategories()
        assertTrue(categories.all { it.isSystem })
    }

    @Test
    fun insertAndGetCategory() = runBlocking {
        val category = Category(name = "Test Category", direction = CATEGORY_DIRECTION_OUTFLOW)
        val id = categoryDao.insert(category)
        assertTrue(id > 0)

        val fetched = categoryDao.getById(id)
        assertNotNull(fetched)
        assertEquals("Test Category", fetched?.name)
    }

    @Test
    fun archiveCategory_setsInactive() = runBlocking {
        val category = Category(name = "Old Category", direction = CATEGORY_DIRECTION_OUTFLOW)
        val id = categoryDao.insert(category)

        categoryDao.archive(id)
        val fetched = categoryDao.getById(id)
        assertNotNull(fetched)
        assertFalse(fetched!!.isActive)
    }
}