package com.prasbin.shadowmoney

import android.app.Application
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ShadowMoneyApp : Application() {
    val database: ShadowMoneyDatabase by lazy {
        ShadowMoneyDatabase.getInstance(this)
    }
}
