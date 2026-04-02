package com.agentstudio

import android.app.Application
import android.util.Log
import com.agentstudio.security.VenCA
import com.agentstudio.security.VenCANetwork
import com.agentstudio.security.VenCAStorage
import com.agentstudio.security.SecurityConfig

class AgentApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize VenCA Security Framework
        initializeVenCASecurity()
    }
    
    private fun initializeVenCASecurity() {
        try {
            Log.d(TAG, "Initializing VenCA Security Framework...")
            
            // Initialize security checks
            val config = SecurityConfig(
                checkRoot = true,
                checkDebug = true,
                checkEmulator = true,
                checkHooks = true,
                checkTamper = true,
                checkDebugger = true,
                blockOnRoot = false,     // Don't block, just warn
                blockOnHook = false,     // Don't block, just warn
                blockOnTamper = false,   // Don't block, just warn
                blockSideLoad = false,   // Allow side-loading
                minSecurityScore = 30    // Minimum acceptable score
            )
            
            val securityScore = VenCA.initialize(this, config)
            Log.d(TAG, "VenCA Security Score: $securityScore")
            
            // Initialize secure storage
            val storageReady = VenCAStorage.initialize(this)
            Log.d(TAG, "Secure Storage: ${if (storageReady) "Ready" else "Failed"}")
            
            // Initialize certificate pinning
            VenCANetwork.initializeDefaultPins()
            Log.d(TAG, "Certificate Pinning: Configured")
            
            // Log security status
            val report = VenCA.getSecurityReport()
            Log.d(TAG, "Security Report:")
            Log.d(TAG, "  - Root: ${if (report.isRooted) "Detected" else "Clean"}")
            Log.d(TAG, "  - Emulator: ${if (report.isEmulator) "Detected" else "Clean"}")
            Log.d(TAG, "  - Hooks: ${if (report.hasHooks) "Detected" else "Clean"}")
            Log.d(TAG, "  - Debugger: ${if (report.isDebuggerConnected) "Connected" else "None"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VenCA Security", e)
        }
    }
    
    companion object {
        private const val TAG = "AgentApplication"
        
        lateinit var instance: AgentApplication
            private set
    }
}
