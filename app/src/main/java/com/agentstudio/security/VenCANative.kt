package com.agentstudio.security

/**
 * VenCA Native Security Layer
 * 
 * JNI interface to native C++ security functions
 * Provides additional protection for critical operations
 */
object VenCANative {
    
    var isNativeLoaded = false
        private set
    
    init {
        try {
            System.loadLibrary("venca_security")
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }
    
    /**
     * Encrypt string using native XOR encryption
     */
    external fun encryptString(input: String): String
    
    /**
     * Decrypt string using native XOR encryption
     */
    external fun decryptString(input: String): String
    
    /**
     * Generate cryptographically secure random string
     */
    external fun generateSecureRandom(length: Int): String
    
    /**
     * Get device security fingerprint
     */
    external fun getSecurityFingerprint(): String
    
    /**
     * Perform native security check
     * Returns security score (0-100)
     */
    external fun performSecurityCheck(): Int
    
    /**
     * Native hash function
     */
    external fun hash(input: String): String
    
    /**
     * Verify app integrity
     */
    external fun verifyIntegrity(): Boolean
    
    /**
     * Check if native layer is available
     */
    fun isAvailable(): Boolean = isNativeLoaded
    
    /**
     * Get combined security score from native and Kotlin layers
     */
    fun getCombinedSecurityScore(): Int {
        if (!isNativeLoaded) return 50 // Default score if native not loaded
        
        val nativeScore = performSecurityCheck()
        val kotlinScore = VenCA.getSecurityScore()
        
        return (nativeScore + kotlinScore) / 2
    }
}
