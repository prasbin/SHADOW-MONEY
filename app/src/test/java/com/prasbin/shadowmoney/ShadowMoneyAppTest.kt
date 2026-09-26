package com.prasbin.shadowmoney

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class ShadowMoneyAppTest {

    @Test
    fun appContext_isNotNull() {
        val appContext = ApplicationProvider.getApplicationContext<ShadowMoneyApp>()
        assertNotNull(appContext)
    }

    @Test
    fun appContext_packageName_isCorrect() {
        val appContext = ApplicationProvider.getApplicationContext<ShadowMoneyApp>()
        assertEquals("com.prasbin.shadowmoney", appContext.packageName)
    }

    @Test
    fun database_instantiable() {
        val app = ApplicationProvider.getApplicationContext<ShadowMoneyApp>()
        val db = ShadowMoneyDatabase.getInstance(app)
        assertNotNull(db)
    }
}
