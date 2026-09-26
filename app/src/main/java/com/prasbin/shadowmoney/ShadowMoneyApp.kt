package com.prasbin.shadowmoney

import android.app.Application
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase

class ShadowMoneyApp : Application() {
    val database: ShadowMoneyDatabase by lazy {
        ShadowMoneyDatabase.getInstance(this)
    }
}
