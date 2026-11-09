package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.ImageDownloadStatus
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import java.io.File
import java.text.DecimalFormat

@Composable
fun ChapterThumbnail(
    imageName: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    downloadStatus: ImageDownloadStatus? = null,
    onRequestDownload: (() -> Unit)? = null,
    onRetryDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val localFile = remember(imageName) {
        findLocalFile(context, imageName)
    }
    val drawableResId = remember(imageName) {
        findDrawableResId(context, imageName)
    }

    val isBuiltIn = drawableResId != 0
    val hasValidFile = localFile != null && localFile.exists() && isValidImageFile(localFile)
    val isError = downloadStatus?.isError == true

    LaunchedEffect(imageName, hasValidFile, isError) {
        if (!imageName.isNullOrBlank() && !isBuiltIn && !hasValidFile && !isError) {
            onRequestDownload?.invoke()
        }
    }

    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(NavyPrimary, NavyDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            hasValidFile -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(localFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isBuiltIn -> {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isError -> {
                // Clear error state with retry button so downloads never spin indefinitely
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadStatus?.errorMessage ?: "ပုံဆွဲ၍ မရပါ (Download failed)",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            onRetryDownload?.invoke() ?: onRequestDownload?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ပြန်ဆွဲပါ (Retry)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            isLoading || downloadStatus?.isDownloading == true || (!imageName.isNullOrBlank() && !isBuiltIn) -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    val percent = downloadStatus?.progressPercent ?: -1
                    val bytesRead = downloadStatus?.bytesDownloaded ?: 0L
                    val totalBytes = downloadStatus?.totalBytes ?: 0L

                    if (percent >= 0 && totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(4.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Downloading image... $percent% (${formatBytes(bytesRead)} / ${formatBytes(totalBytes)})",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    } else if (bytesRead > 0) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloading image... (${formatBytes(bytesRead)})",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloading image...",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            else -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = DecimalFormat("#.#")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
        bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
        else -> "$bytes B"
    }
}

fun isValidImageFile(file: File): Boolean {
    if (!file.exists() || file.length() < 12) return false
    return try {
        val bytes = ByteArray(12)
        file.inputStream().use { it.read(bytes) }
        val isJpeg = (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte())
        val isPng = (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte())
        val isWebp = (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte())
        val isGif = (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte())
        isJpeg || isPng || isWebp || isGif
    } catch (_: Exception) {
        false
    }
}

fun findLocalFile(context: Context, imageName: String?): File? {
    if (imageName.isNullOrBlank()) return null
    val contentDir = File(context.filesDir, "hydraulic_content")
    val direct = File(contentDir, imageName)
    if (direct.exists() && isValidImageFile(direct)) return direct

    // Check with common extensions
    for (ext in listOf("", ".jpg", ".png", ".webp")) {
        val f = File(contentDir, "$imageName$ext")
        if (f.exists() && isValidImageFile(f)) return f
    }
    return null
}

fun findDrawableResId(context: Context, imageName: String?): Int {
    if (imageName.isNullOrBlank()) return 0
    val cleanName = imageName.substringBeforeLast(".")
    return when (cleanName) {
        "img_hose_intro" -> R.drawable.img_hose_intro
        "img_hose_types" -> R.drawable.img_hose_types
        "img_hose_fittings" -> R.drawable.img_hose_fittings
        "img_hose_install" -> R.drawable.img_hose_install
        "img_hose_safety" -> R.drawable.img_hose_safety
        "ic_hydraulic_hose" -> R.drawable.ic_hydraulic_hose
        else -> {
            try {
                context.resources.getIdentifier(cleanName, "drawable", context.packageName)
            } catch (_: Exception) {
                0
            }
        }
    }
}
