package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object ImageCompressor {
    const val MAX_DIMENSION = 1280
    const val JPEG_QUALITY = 80

    /**
     * Compresses an image picked via Content Uri:
     * - Downscales so the longest dimension is at most maxDimension (1280 px)
     * - Corrects EXIF orientation so portrait/landscape photos stay oriented correctly
     * - Compresses to JPEG with quality 80
     * - Targets 150 - 450 KB per image (well under 1 MB)
     */
    fun compressFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION,
        quality: Int = JPEG_QUALITY
    ): ByteArray? {
        return try {
            // 1. Read dimensions only
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Compute inSampleSize for memory safety
            var inSampleSize = 1
            val maxOrig = maxOf(origWidth, origHeight)
            while (maxOrig / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            // 3. Decode sub-sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Read EXIF orientation to correct camera rotation
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            // 5. Downscale precisely so longest edge <= maxDimension
            val currentMax = maxOf(sampledBitmap.width, sampledBitmap.height)
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax.toFloat()
                matrix.postScale(scale, scale)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            // 6. Compress to JPEG quality 80
            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            finalBitmap.recycle()

            val compressedBytes = baos.toByteArray()
            Log.d("ImageCompressor", "Compressed Uri image from orig ${origWidth}x${origHeight} to ${compressedBytes.size} bytes (~${compressedBytes.size / 1024} KB)")
            compressedBytes
        } catch (e: Exception) {
            Log.e("ImageCompressor", "Failed to compress image from Uri $uri", e)
            null
        }
    }

    /**
     * Compresses raw image bytes (e.g. from local storage):
     * Downscales so longest dimension is maxDimension (1280 px) with JPEG quality 80.
     */
    fun compressBytes(
        bytes: ByteArray,
        maxDimension: Int = MAX_DIMENSION,
        quality: Int = JPEG_QUALITY
    ): ByteArray {
        if (bytes.size < 500 * 1024) return bytes // Already compact

        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return bytes

            var inSampleSize = 1
            val maxOrig = maxOf(origWidth, origHeight)
            while (maxOrig / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return bytes

            val currentMax = maxOf(sampledBitmap.width, sampledBitmap.height)
            val matrix = Matrix()
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax.toFloat()
                matrix.postScale(scale, scale)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            finalBitmap.recycle()

            val compressed = baos.toByteArray()
            Log.d("ImageCompressor", "Compressed byte array from ${bytes.size} to ${compressed.size} bytes (~${compressed.size / 1024} KB)")
            compressed
        } catch (e: Exception) {
            Log.e("ImageCompressor", "Error compressing raw byte array", e)
            bytes
        }
    }
}
