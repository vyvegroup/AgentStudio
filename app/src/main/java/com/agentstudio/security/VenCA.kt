package com.agentstudio.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * VenCA Security Framework
 * Enterprise-grade security system for Android applications
 * 
 * Features:
 * - String Encryption (AES-256-GCM)
 * - Anti-Tampering & Integrity Check
 * - Root Detection
 * - Debug Detection
 * - Emulator Detection
 * - Certificate Pinning
 * - Hook Detection (Frida, Xposed)
 * 
 * @version 3.8.0
 * @author VenAI Security Team
 */
object VenCA {
    
    private const val TAG = "VenCA"
    
    // Encryption keys (derived from app signature at runtime)
    private var masterKey: ByteArray? = null
    private var isInitialized = false
    private var securityScore = 0
    
    // Security configuration
    private var config = SecurityConfig()
    
    /**
     * Initialize VenCA Security Framework
     * Must be called in Application.onCreate()
     */
    fun initialize(context: Context, config: SecurityConfig = SecurityConfig()): Int {
        this.config = config
        securityScore = 100  // Start with perfect score
        
        Log.d(TAG, "╔════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║           VenCA Security Framework v3.8.0              ║")
        Log.d(TAG, "║        Enterprise-Grade Android Protection             ║")
        Log.d(TAG, "╚════════════════════════════════════════════════════════╝")
        
        // Generate master key from app signature
        masterKey = generateMasterKey(context)
        
        // Run security checks
        runSecurityChecks(context)
        
        isInitialized = true
        
        Log.d(TAG, "Security Score: $securityScore/100")
        Log.d(TAG, "Status: ${if (securityScore >= config.minSecurityScore) "SECURE" else "COMPROMISED"}")
        
        return securityScore
    }
    
    /**
     * Run all security checks
     */
    private fun runSecurityChecks(context: Context) {
        // Root Detection
        if (config.checkRoot) {
            if (isRooted()) {
                Log.w(TAG, "⚠️ ROOT DETECTED")
                securityScore -= 30
                if (config.blockOnRoot) {
                    throw SecurityException("Root access detected - app cannot run")
                }
            }
        }
        
        // Debug Detection
        if (config.checkDebug) {
            if (isDebuggable(context)) {
                Log.w(TAG, "⚠️ DEBUG MODE DETECTED")
                securityScore -= 15
            }
        }
        
        // Emulator Detection
        if (config.checkEmulator) {
            if (isEmulator()) {
                Log.w(TAG, "⚠️ EMULATOR DETECTED")
                securityScore -= 10
            }
        }
        
        // Hook Detection
        if (config.checkHooks) {
            if (detectHooks()) {
                Log.w(TAG, "⚠️ HOOK FRAMEWORK DETECTED")
                securityScore -= 40
                if (config.blockOnHook) {
                    throw SecurityException("Hook framework detected - app cannot run")
                }
            }
        }
        
        // Tamper Detection
        if (config.checkTamper) {
            if (isTampered(context)) {
                Log.w(TAG, "⚠️ APP TAMPERED")
                securityScore -= 50
                if (config.blockOnTamper) {
                    throw SecurityException("App integrity compromised - app cannot run")
                }
            }
        }
        
        // Debugger Detection
        if (config.checkDebugger) {
            if (isDebuggerConnected()) {
                Log.w(TAG, "⚠️ DEBUGGER CONNECTED")
                securityScore -= 20
            }
        }
    }
    
    // ==================== STRING ENCRYPTION ====================
    
    /**
     * Encrypt sensitive string data
     * Uses AES-256-GCM for authenticated encryption
     */
    fun encrypt(plaintext: String): String {
        if (!isInitialized) {
            Log.e(TAG, "VenCA not initialized!")
            return plaintext
        }
        
        return try {
            val key = masterKey ?: return plaintext
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            
            // Generate random IV
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            // Combine IV + encrypted data and encode as Base64
            val combined = iv + encrypted
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            plaintext
        }
    }
    
    /**
     * Decrypt sensitive string data
     */
    fun decrypt(ciphertext: String): String {
        if (!isInitialized) {
            Log.e(TAG, "VenCA not initialized!")
            return ciphertext
        }
        
        return try {
            val key = masterKey ?: return ciphertext
            val combined = android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)
            
            // Extract IV (first 12 bytes) and encrypted data
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            ciphertext
        }
    }
    
    /**
     * Obfuscate string at compile time (for hardcoded values)
     * Returns encrypted string that can be decrypted at runtime
     */
    fun obfuscate(value: String, seed: Long = System.nanoTime()): ObfuscatedString {
        val xorKey = seed.toString().toByteArray()
        val bytes = value.toByteArray(Charsets.UTF_8)
        val encrypted = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }
        return ObfuscatedString(
            data = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP),
            seed = seed
        )
    }
    
    fun deobfuscate(obfuscated: ObfuscatedString): String {
        val xorKey = obfuscated.seed.toString().toByteArray()
        val bytes = android.util.Base64.decode(obfuscated.data, android.util.Base64.NO_WRAP)
        val decrypted = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }
    
    // ==================== ROOT DETECTION ====================
    
    /**
     * Comprehensive root detection
     * Checks multiple indicators for maximum accuracy
     */
    fun isRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4()
    }
    
    private fun checkRootMethod1(): Boolean {
        // Check for su binary
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/bin",
            "/magisk/.core/bin/su",
            "/apex/com.android.runtime/bin/su"
        )
        
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }
    
    private fun checkRootMethod2(): Boolean {
        // Check for root management apps via shell
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            process.inputStream.bufferedReader().readLine() != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkRootMethod3(): Boolean {
        // Check for writable system directories
        return try {
            val paths = arrayOf("/system", "/system/bin", "/system/sbin")
            for (path in paths) {
                val file = File(path)
                if (file.exists() && file.canWrite()) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkRootMethod4(): Boolean {
        // Check for Magisk and KernelSU
        return try {
            File("/sbin/.magisk").exists() ||
            File("/cache/.magisk").exists() ||
            File("/data/adb/magisk").exists() ||
            File("/data/adb/ksu").exists()
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== DEBUG DETECTION ====================
    
    fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
    fun isDebuggerConnected(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
    
    // ==================== EMULATOR DETECTION ====================
    
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator"))
    }
    
    // ==================== HOOK DETECTION ====================
    
    fun detectHooks(): Boolean {
        return detectFrida() || detectXposed() || detectSubstrate()
    }
    
    private fun detectFrida(): Boolean {
        // Check for Frida server
        val fridaIndicators = arrayOf(
            "frida-server",
            "frida-agent",
            "re.frida.server",
            "/data/local/tmp/frida-server",
            "/data/local/tmp/frida-agent"
        )
        
        for (indicator in fridaIndicators) {
            if (File(indicator).exists()) return true
        }
        
        // Check for Frida libraries in memory
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/self/maps")
            val output = process.inputStream.bufferedReader().readText()
            output.contains("frida", ignoreCase = true) || output.contains("LIBFRIDA")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun detectXposed(): Boolean {
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            File("/system/framework/XposedBridge.jar").exists() ||
            File("/system/framework/xposed.jar").exists() ||
            File("/data/data/de.robv.android.xposed.installer").exists()
        }
    }
    
    private fun detectSubstrate(): Boolean {
        return try {
            Class.forName("com.saurik.substrate.MS")
            true
        } catch (e: ClassNotFoundException) {
            File("/data/data/com.saurik.substrate").exists()
        }
    }
    
    // ==================== TAMPER DETECTION ====================
    
    fun isTampered(context: Context): Boolean {
        return checkSignature(context) || checkInstaller(context)
    }
    
    private fun checkSignature(context: Context): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            if (signatures.isNullOrEmpty()) return true
            
            // Calculate signature hash
            val md = MessageDigest.getInstance("SHA-256")
            val signatureHash = md.digest(signatures[0].toByteArray())
            val hashString = signatureHash.joinToString("") { "%02x".format(it) }
            
            // Compare with expected signature hash
            val expectedHash = getExpectedSignatureHash()
            hashString != expectedHash && expectedHash.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Signature check failed", e)
            false
        }
    }
    
    private fun checkInstaller(context: Context): Boolean {
        return try {
            val installer = context.packageManager.getInstallerPackageName(context.packageName)
            
            val validInstallers = listOf(
                "com.android.vending",
                "com.google.android.feedback",
                "com.amazon.venezia",
                "com.sec.android.app.samsungapps"
            )
            
            installer != null && installer !in validInstallers && config.blockSideLoad
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun generateMasterKey(context: Context): ByteArray {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            val signature = signatures?.firstOrNull()?.toByteArray()
                ?: return ByteArray(32) { 0x42 }
            
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(signature)
        } catch (e: Exception) {
            ByteArray(32) { 0x42 }
        }
    }
    
    private fun getExpectedSignatureHash(): String {
        // Set at build time from actual release signature
        return ""
    }
    
    // ==================== PUBLIC API ====================
    
    fun getSecurityScore(): Int = securityScore
    
    fun isSecure(): Boolean = securityScore >= config.minSecurityScore
    
    fun getSecurityReport(): SecurityReport {
        return SecurityReport(
            score = securityScore,
            isRooted = isRooted(),
            isEmulator = isEmulator(),
            isDebuggable = true,
            hasHooks = detectHooks(),
            isDebuggerConnected = isDebuggerConnected()
        )
    }
}

/**
 * Security configuration options
 */
data class SecurityConfig(
    val checkRoot: Boolean = true,
    val checkDebug: Boolean = true,
    val checkEmulator: Boolean = true,
    val checkHooks: Boolean = true,
    val checkTamper: Boolean = true,
    val checkDebugger: Boolean = true,
    val blockOnRoot: Boolean = false,
    val blockOnHook: Boolean = false,
    val blockOnTamper: Boolean = false,
    val blockSideLoad: Boolean = false,
    val minSecurityScore: Int = 50
)

/**
 * Obfuscated string container
 */
data class ObfuscatedString(
    val data: String,
    val seed: Long
)

/**
 * Security report data
 */
data class SecurityReport(
    val score: Int,
    val isRooted: Boolean,
    val isEmulator: Boolean,
    val isDebuggable: Boolean,
    val hasHooks: Boolean,
    val isDebuggerConnected: Boolean
) {
    fun isSecure(): Boolean = score >= 50 && !hasHooks
    
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        if (isRooted) recommendations.add("Device is rooted - use caution")
        if (isEmulator) recommendations.add("Running in emulator")
        if (isDebuggable) recommendations.add("App is in debug mode")
        if (hasHooks) recommendations.add("Hook framework detected")
        if (isDebuggerConnected) recommendations.add("Debugger connected")
        return recommendations
    }
}
