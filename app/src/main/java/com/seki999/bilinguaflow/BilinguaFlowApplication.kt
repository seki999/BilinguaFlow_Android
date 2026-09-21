package com.seki999.bilinguaflow

import android.app.Application
import com.seki999.bilinguaflow.util.Logger

class BilinguaFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("BilinguaFlow application started")
    }
}
