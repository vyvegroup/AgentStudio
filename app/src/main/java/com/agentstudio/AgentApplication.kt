package com.agentstudio

import android.app.Application

class AgentApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Application initialization
    }
    
    companion object {
        lateinit var instance: AgentApplication
            private set
    }
}
