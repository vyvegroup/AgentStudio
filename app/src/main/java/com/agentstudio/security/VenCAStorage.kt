package com.agentstudio.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

/**
 * VenCA Secure Storage
 * 
 * Encrypted key-value storage for sensitive data
 * - API Keys
 * - Endpoints
 * - User credentials
 * - Configuration
 */
object VenCAStorage {
    
    private const val TAG = "VenCAStorage"
    private const val PREFS_FILE_NAME = "venca_secure_prefs"
    
    private var encryptedPrefs: SharedPreferences? = null
    private var masterKey: MasterKey? = null
    private var isInitialized = false
    
    /**
     * Initialize secure storage
     */
    fun initialize(context: Context): Boolean {
        return try {
            // Create or get master key
            masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // Create encrypted shared preferences
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey!!,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            isInitialized = true
            Log.d(TAG, "VenCA Secure Storage initialized successfully")
            true
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Failed to initialize secure storage", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secure storage", e)
            false
        }
    }
    
    /**
     * Store API key securely
     */
    fun storeApiKey(context: Context, provider: String, apiKey: String): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.edit()?.putString("api_key_$provider", apiKey)?.commit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store API key for $provider", e)
            false
        }
    }
    
    /**
     * Retrieve API key
     */
    fun getApiKey(context: Context, provider: String): String? {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.getString("api_key_$provider", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key for $provider", e)
            null
        }
    }
    
    /**
     * Store endpoint securely
     */
    fun storeEndpoint(context: Context, name: String, endpoint: String): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.edit()?.putString("endpoint_$name", endpoint)?.commit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store endpoint $name", e)
            false
        }
    }
    
    /**
     * Retrieve endpoint
     */
    fun getEndpoint(context: Context, name: String): String? {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.getString("endpoint_$name", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get endpoint $name", e)
            null
        }
    }
    
    /**
     * Store any secure string
     */
    fun putString(context: Context, key: String, value: String): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.edit()?.putString(key, value)?.commit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store $key", e)
            false
        }
    }
    
    /**
     * Retrieve secure string
     */
    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.getString(key, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get $key", e)
            defaultValue
        }
    }
    
    /**
     * Store secure boolean
     */
    fun putBoolean(context: Context, key: String, value: Boolean): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.edit()?.putBoolean(key, value)?.commit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store $key", e)
            false
        }
    }
    
    /**
     * Retrieve secure boolean
     */
    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.getBoolean(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get $key", e)
            defaultValue
        }
    }
    
    /**
     * Delete a key
     */
    fun delete(context: Context, key: String): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.edit()?.remove(key)?.commit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete $key", e)
            false
        }
    }
    
    /**
     * Clear all secure data
     */
    fun clearAll(context: Context): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.edit()?.clear()?.commit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all", e)
            false
        }
    }
    
    /**
     * Check if key exists
     */
    fun contains(context: Context, key: String): Boolean {
        return try {
            ensureInitialized(context)
            encryptedPrefs?.contains(key) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            initialize(context)
        }
    }
}
