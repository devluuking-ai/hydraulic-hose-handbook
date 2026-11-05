package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.OfflineAmber
import com.example.ui.theme.OfflineBannerBg
import com.example.ui.theme.OnlineBannerBg
import com.example.ui.theme.OnlineGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusHeader(
    isOffline: Boolean,
    lastSyncTime: Long,
    isSyncing: Boolean,
    syncProgressText: String,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = if (lastSyncTime > 0) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        "Last synced: " + sdf.format(Date(lastSyncTime))
    } else {
        "Handbook: Built-in preloaded"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isOffline) {
            // Offline banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OfflineBannerBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("offline_status_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = OfflineAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.offline_reading_banner),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = OfflineAmber,
                            fontSize = 13.sp
                        )
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OfflineAmber.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        } else {
            // Online status strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnlineBannerBg)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("online_status_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Online",
                        tint = OnlineGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OnlineGreen,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                }

                if (isSyncing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                            color = OnlineGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Syncing…",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnlineGreen,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSyncClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("quick_sync_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = OnlineGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnlineGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Animated sync progress message bar if syncing
        AnimatedVisibility(visible = isSyncing && syncProgressText.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0F2FE))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                    color = Color(0xFF0369A1)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = syncProgressText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF0369A1),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
