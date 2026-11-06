package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.WarningRed
import com.example.ui.viewmodel.EbookViewModel

@Composable
fun PinLockDialog(
    viewModel: EbookViewModel,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val pinError by viewModel.pinError.collectAsState()
    val lockoutSec by viewModel.lockoutRemainingSeconds.collectAsState()
    val isLocked = lockoutSec > 0

    var pinInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("pin_lock_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isLocked) Color(0xFFFEE2E2) else Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.LockClock else Icons.Default.Lock,
                        contentDescription = "Admin Security",
                        tint = if (isLocked) WarningRed else NavyPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Admin Access",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = NavyDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isLocked) "Security lockout in effect" else "Enter 4-digit PIN (default: 1234)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isLocked) WarningRed else Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // PIN Digits Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isLocked -> Color(0xFFFCA5A5)
                                        isFilled -> NavyPrimary
                                        else -> Color(0xFFE2E8F0)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFilled) NavyPrimary else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error / Lockout message
                AnimatedVisibility(visible = pinError != null) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = pinError ?: "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WarningRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Numeric Keypad
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLEAR", "0", "DEL")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (row in keys) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (key in row) {
                                KeypadButton(
                                    text = key,
                                    enabled = !isLocked,
                                    onClick = {
                                        when (key) {
                                            "CLEAR" -> pinInput = ""
                                            "DEL" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                            else -> {
                                                if (pinInput.length < 4) {
                                                    val newPin = pinInput + key
                                                    pinInput = newPin
                                                    if (newPin.length == 4) {
                                                        val ok = viewModel.verifyPin(newPin)
                                                        if (ok) {
                                                            onSuccess()
                                                        } else {
                                                            pinInput = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("pin_cancel_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isSpecial = text == "CLEAR" || text == "DEL"
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(if (isSpecial) Color(0xFFF1F5F9) else Color(0xFFF8FAFC))
            .clickable(enabled = enabled) { onClick() }
            .border(1.dp, OutlineBorder, CircleShape)
            .testTag("pin_key_$text"),
        contentAlignment = Alignment.Center
    ) {
        if (text == "DEL") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = if (enabled) NavyPrimary else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (enabled) (if (isSpecial) Color(0xFF64748B) else NavyDark) else Color(0xFFCBD5E1),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isSpecial) 11.sp else 20.sp
                )
            )
        }
    }
}
