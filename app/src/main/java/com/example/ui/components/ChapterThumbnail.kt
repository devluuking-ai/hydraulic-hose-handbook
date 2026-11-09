package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import java.io.File

@Composable
fun ChapterThumbnail(
    imageName: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onRequestDownload: (() -> Unit)? = null
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

    LaunchedEffect(imageName, hasValidFile) {
        if (!imageName.isNullOrBlank() && !isBuiltIn && !hasValidFile) {
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
            isLoading || (!imageName.isNullOrBlank() && !isBuiltIn) -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
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
