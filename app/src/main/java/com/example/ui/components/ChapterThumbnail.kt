package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localFile = remember(imageName) {
        findLocalFile(context, imageName)
    }
    val drawableResId = remember(imageName) {
        findDrawableResId(context, imageName)
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
            localFile != null && localFile.exists() -> {
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
            drawableResId != 0 -> {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

fun findLocalFile(context: Context, imageName: String?): File? {
    if (imageName.isNullOrBlank()) return null
    val contentDir = File(context.filesDir, "hydraulic_content")
    val direct = File(contentDir, imageName)
    if (direct.exists()) return direct

    // Check with common extensions
    for (ext in listOf("", ".jpg", ".png", ".webp")) {
        val f = File(contentDir, "$imageName$ext")
        if (f.exists()) return f
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
