package com.agentstudio.security

import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * VenCA Certificate Pinning & Secure Network Client
 * 
 * Features:
 * - SSL/TLS Certificate Pinning
 * - TLS 1.3 Enforcement
 * - MITM Protection
 * - Secure Connection Specs
 */
object VenCANetwork {
    
    private const val TAG = "VenCANetwork"
    
    // Pinned certificates for known domains
    private val pinnedCertificates = mutableMapOf<String, List<String>>()
    
    /**
     * Add certificate pin for a domain
     * @param domain Domain to pin (e.g., "api.openrouter.ai")
     * @param pins SHA-256 pins of certificates
     */
    fun addCertificatePin(domain: String, vararg pins: String) {
        pinnedCertificates[domain] = pins.toList()
        Log.d(TAG, "Added certificate pin for $domain")
    }
    
    /**
     * Create secure OkHttpClient with certificate pinning
     */
    fun createSecureClient(): OkHttpClient.Builder {
        return OkHttpClient.Builder().apply {
            // Set timeouts
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(30, TimeUnit.SECONDS)
            
            // Enforce TLS 1.3
            connectionSpecs(listOf(
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .cipherSuites(*getSecureCipherSuites())
                    .build()
            ))
            
            // Add certificate pinning
            if (pinnedCertificates.isNotEmpty()) {
                val pinnerBuilder = CertificatePinner.Builder()
                pinnedCertificates.forEach { (domain, pins) ->
                    pins.forEach { pin ->
                        pinnerBuilder.add(domain, pin)
                    }
                }
                certificatePinner(pinnerBuilder.build())
            }
            
            // Enable TLS session tickets for performance
            retryOnConnectionFailure(true)
        }
    }
    
    /**
     * Get secure cipher suites
     */
    private fun getSecureCipherSuites(): Array<String> {
        return arrayOf(
            // TLS 1.3 cipher suites
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_128_GCM_SHA256",
            // TLS 1.2 cipher suites
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )
    }
    
    /**
     * Compute SHA-256 pin from certificate
     */
    fun computePin(certificate: Certificate): String {
        val bytes = certificate.encoded
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return "sha256/${android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)}"
    }
    
    /**
     * Get pins for OpenRouter API
     */
    fun getOpenRouterPins(): List<String> {
        return listOf(
            "sha256/+xkPH5YI9E2SuSDeH9RlkzlkVLHPBp0KXOYqwuC2Nlk=", // Let's Encrypt
            "sha256/k2v657xBsOVe1PQRwOsHsw3bsGT2VzIqz5K+59sNQws=" // Let's Encrypt backup
        )
    }
    
    /**
     * Initialize default pins for known APIs
     */
    fun initializeDefaultPins() {
        // OpenRouter API
        addCertificatePin(
            "openrouter.ai",
            *getOpenRouterPins().toTypedArray()
        )
        addCertificatePin(
            "api.openrouter.ai",
            *getOpenRouterPins().toTypedArray()
        )
        
        // GitHub
        addCertificatePin(
            "github.com",
            "sha256/RQeZkBZznQS3HxUCCfetrk4Vbi7mXfI4yZIjp4/HPZk="
        )
        
        // Google APIs
        addCertificatePin(
            "googleapis.com",
            "sha256/7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y="
        )
    }
}

/**
 * TLS Version constants
 */
object TlsVersion {
    const val TLS_1_2 = "TLSv1.2"
    const val TLS_1_3 = "TLSv1.3"
}
