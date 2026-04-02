package com.agentstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentstudio.security.*

/**
 * VenCA Security Admin Panel
 * 
 * Enterprise security dashboard for:
 * - Security score monitoring
 * - Threat detection status
 * - Security configuration
 * - Integrity verification
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenCASecurityPanel(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Security state
    var securityScore by remember { mutableStateOf(0) }
    var securityReport by remember { mutableStateOf<SecurityReport?>(null) }
    var isNativeAvailable by remember { mutableStateOf(false) }
    
    // Initialize security check
    LaunchedEffect(Unit) {
        try {
            VenCA.initialize(context, SecurityConfig(
                checkRoot = true,
                checkDebug = true,
                checkEmulator = true,
                checkHooks = true,
                checkTamper = true,
                checkDebugger = true
            ))
            securityScore = VenCA.getSecurityScore()
            securityReport = VenCA.getSecurityReport()
            isNativeAvailable = VenCANative.isAvailable()
        } catch (e: Exception) {
            // Handle initialization error
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("VenCA Security")
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Score Card
            SecurityScoreCard(
                score = securityScore,
                isNativeAvailable = isNativeAvailable
            )
            
            // Threat Status Cards
            securityReport?.let { report ->
                ThreatStatusGrid(report = report)
                
                // Recommendations
                val recommendations = report.getRecommendations()
                if (recommendations.isNotEmpty()) {
                    RecommendationsCard(recommendations = recommendations)
                }
            }
            
            // Security Features
            SecurityFeaturesCard()
            
            // Native Layer Status
            NativeLayerCard(isAvailable = isNativeAvailable)
            
            // Actions
            SecurityActionsCard(
                onRefresh = {
                    securityScore = VenCA.getSecurityScore()
                    securityReport = VenCA.getSecurityReport()
                    isNativeAvailable = VenCANative.isAvailable()
                },
                onVerifyIntegrity = {
                    // Run integrity check
                }
            )
        }
    }
}

@Composable
private fun SecurityScoreCard(
    score: Int,
    isNativeAvailable: Boolean
) {
    val scoreColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFFA726)
        else -> Color(0xFFF44336)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            scoreColor.copy(alpha = 0.2f),
                            scoreColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Security Score",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = score / 100f,
                        color = scoreColor,
                        strokeWidth = 8.dp,
                        trackColor = scoreColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(120.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = score.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                        Text(
                            text = "/ 100",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (score >= 50) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = scoreColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (score >= 80) "Excellent Protection"
                               else if (score >= 50) "Adequate Protection"
                               else "Risk Detected",
                        style = MaterialTheme.typography.titleMedium,
                        color = scoreColor
                    )
                }
                
                if (isNativeAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Native Layer Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreatStatusGrid(report: SecurityReport) {
    Column {
        Text(
            text = "Threat Detection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThreatStatusItem(
                modifier = Modifier.weight(1f),
                title = "Root",
                isThreat = report.isRooted,
                icon = Icons.Default.AdminPanelSettings
            )
            ThreatStatusItem(
                modifier = Modifier.weight(1f),
                title = "Emulator",
                isThreat = report.isEmulator,
                icon = Icons.Default.PhoneAndroid
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThreatStatusItem(
                modifier = Modifier.weight(1f),
                title = "Hooks",
                isThreat = report.hasHooks,
                icon = Icons.Default.BugReport
            )
            ThreatStatusItem(
                modifier = Modifier.weight(1f),
                title = "Debugger",
                isThreat = report.isDebuggerConnected,
                icon = Icons.Default.Code
            )
        }
    }
}

@Composable
private fun ThreatStatusItem(
    modifier: Modifier = Modifier,
    title: String,
    isThreat: Boolean,
    icon: ImageVector
) {
    val backgroundColor = if (isThreat) 
        Color(0xFFF44336).copy(alpha = 0.1f)
    else 
        Color(0xFF4CAF50).copy(alpha = 0.1f)
    
    val contentColor = if (isThreat)
        Color(0xFFF44336)
    else
        Color(0xFF4CAF50)
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
                Text(
                    text = if (isThreat) "Detected" else "Clear",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun RecommendationsCard(recommendations: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFA726).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFFA726)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFA726)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            recommendations.forEach { recommendation ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowRight,
                        contentDescription = null,
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityFeaturesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "VenCA Security Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val features = listOf(
                "AES-256-GCM String Encryption" to true,
                "Certificate Pinning" to true,
                "Root Detection" to true,
                "Hook Detection (Frida, Xposed)" to true,
                "Emulator Detection" to true,
                "Anti-Tamper Protection" to true,
                "Debug Detection" to true,
                "Native NDK Layer" to true,
                "DEX Obfuscation" to true,
                "Secure Storage (EncryptedSharedPreferences)" to true
            )
            
            features.forEach { (feature, enabled) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (enabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeLayerCard(isAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable)
                Color(0xFF2196F3).copy(alpha = 0.1f)
            else
                Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = if (isAvailable) Color(0xFF2196F3) else Color(0xFFF44336),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Native Security Layer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAvailable) "Loaded and active" else "Not available",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAvailable) Color(0xFF2196F3) else Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
private fun SecurityActionsCard(
    onRefresh: () -> Unit,
    onVerifyIntegrity: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
                }
                
                Button(
                    onClick = onVerifyIntegrity,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify")
                }
            }
        }
    }
}
