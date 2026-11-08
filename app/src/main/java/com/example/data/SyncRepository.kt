package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.model.CalloutType
import com.example.model.Chapter
import com.example.model.ChapterSection
import com.example.model.PendingUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class SyncResult {
    data class Success(val message: String, val fileCount: Int) : SyncResult()
    data class Failure(val error: String) : SyncResult()
    object Offline : SyncResult()
}

class SyncRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val contentDir: File
        get() = File(context.filesDir, "hydraulic_content").apply {
            if (!exists()) mkdirs()
        }

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getLocalContentDir(): File = contentDir

    fun loadChapters(): List<Chapter> {
        migrateDemoContentIfNeeded()
        val contentJsonFile = File(contentDir, "content.json")
        val bookmarks = MMKVStorage.getBookmarks()

        if (contentJsonFile.exists() && contentJsonFile.length() > 0) {
            try {
                val jsonString = contentJsonFile.readText()
                val parsed = parseChaptersJson(jsonString, defaultSource = "drive")
                if (parsed.isNotEmpty()) {
                    return parsed.map { ch ->
                        ch.copy(isBookmarked = bookmarks.contains(ch.id))
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncRepository", "Failed to parse local content.json: ${e.message}", e)
            }
        }

        // Fallback to default built-in chapters
        return DefaultChapters.get().map { ch ->
            ch.copy(isBookmarked = bookmarks.contains(ch.id))
        }
    }

    /**
     * One-time MMKV migration (flag demo_cleanup_done) that runs on first launch
     * of the new APK: drops demo chapters 6-7 (chapter_6_troubleshooting, chapter_7_crimper)
     * and any placeholder/sample articles, restores chapters 1-5 from DefaultChapters,
     * while KEEPING any admin-added articles (source 'admin' or id starting with custom_).
     */
    private fun migrateDemoContentIfNeeded() {
        if (MMKVStorage.getBoolean("demo_cleanup_done", false)) return

        val contentJsonFile = File(contentDir, "content.json")
        val builtIn = DefaultChapters.get()
        val builtInIds = builtIn.map { it.id }.toSet()

        val currentList: List<Chapter> = if (contentJsonFile.exists() && contentJsonFile.length() > 0) {
            try {
                parseChaptersJson(contentJsonFile.readText(), "drive")
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Keep real admin created articles, drop demo/sample chapters
        val adminArticles = currentList.filter { ch ->
            (ch.source == "admin" || ch.id.startsWith("custom_")) &&
                    ch.id != "chapter_6_troubleshooting" && ch.id != "chapter_7_crimper" &&
                    !ch.id.startsWith("sample_") && !ch.id.startsWith("demo_") &&
                    !ch.title.contains("[Sample]") && !ch.title.contains("[Demo]") &&
                    ch.id !in builtInIds
        }

        val merged = (builtIn + adminArticles).sortedBy { it.order }
        try {
            contentJsonFile.writeText(serializeChaptersToJson(merged))
            MMKVStorage.putBoolean("demo_cleanup_done", true)
        } catch (e: Exception) {
            Log.e("SyncRepository", "Demo content cleanup failed", e)
        }
    }

    private fun parseChaptersJson(jsonString: String, defaultSource: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val trimmed = jsonString.trim()

        val jsonArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            when {
                root.has("chapters") -> root.getJSONArray("chapters")
                root.has("articles") -> root.getJSONArray("articles")
                root.has("items") -> root.getJSONArray("items")
                else -> JSONArray()
            }
        }

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val id = item.optString("id", "chapter_$i")

            // Skip removed demo/sample chapters 6, 7 or sample markers
            if (id == "chapter_6_troubleshooting" || id == "chapter_7_crimper" ||
                id.startsWith("sample_") || id.startsWith("demo_")
            ) {
                continue
            }

            val title = item.optString("title", "Untitled Chapter")
            val subtitle = item.optString("subtitle", "")
            val category = item.optString("category", "General")
            val order = item.optInt("order", i + 1)
            val readingTime = item.optInt("readingTimeMinutes", 5)
            val thumbnail = item.optString("thumbnail").ifBlank { item.optString("image") }.takeIf { it.isNotBlank() }
            val secondImage = item.optString("secondImage").takeIf { it.isNotBlank() }
            val summary = item.optString("summary", "")
            val source = item.optString("source", defaultSource)
            val uploadStatus = item.optString("uploadStatus", "SAVED_TO_DRIVE")

            val sections = mutableListOf<ChapterSection>()
            if (item.has("sections")) {
                val sectionsArray = item.getJSONArray("sections")
                for (j in 0 until sectionsArray.length()) {
                    val secObj = sectionsArray.getJSONObject(j)
                    val calloutStr = secObj.optString("calloutType", "NONE").uppercase()
                    val calloutType = try {
                        CalloutType.valueOf(calloutStr)
                    } catch (_: Exception) {
                        CalloutType.NONE
                    }

                    sections.add(
                        ChapterSection(
                            heading = secObj.optString("heading", ""),
                            text = secObj.optString("text", ""),
                            image = secObj.optString("image").takeIf { it.isNotBlank() },
                            caption = secObj.optString("caption").takeIf { it.isNotBlank() },
                            calloutType = calloutType,
                            calloutTitle = secObj.optString("calloutTitle").takeIf { it.isNotBlank() },
                            calloutText = secObj.optString("calloutText").takeIf { it.isNotBlank() }
                        )
                    )
                }
            } else if (item.has("text")) {
                sections.add(
                    ChapterSection(
                        heading = title,
                        text = item.optString("text", ""),
                        image = thumbnail,
                        caption = null
                    )
                )
            }

            chapters.add(
                Chapter(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    category = category,
                    order = order,
                    readingTimeMinutes = readingTime,
                    thumbnail = thumbnail,
                    secondImage = secondImage,
                    summary = summary,
                    sections = sections,
                    source = source,
                    uploadStatus = uploadStatus
                )
            )
        }
        return chapters.sortedBy { it.order }
    }

    private fun serializeChaptersToJson(chapters: List<Chapter>): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("lastUpdated", System.currentTimeMillis())

        val array = JSONArray()
        for (ch in chapters) {
            val obj = JSONObject()
            obj.put("id", ch.id)
            obj.put("title", ch.title)
            obj.put("subtitle", ch.subtitle)
            obj.put("category", ch.category)
            obj.put("order", ch.order)
            obj.put("readingTimeMinutes", ch.readingTimeMinutes)
            obj.put("thumbnail", ch.thumbnail ?: "")
            obj.put("secondImage", ch.secondImage ?: "")
            obj.put("summary", ch.summary)
            obj.put("source", ch.source)
            obj.put("uploadStatus", ch.uploadStatus)

            val secArray = JSONArray()
            for (sec in ch.sections) {
                val secObj = JSONObject()
                secObj.put("heading", sec.heading)
                secObj.put("text", sec.text)
                secObj.put("image", sec.image ?: "")
                secObj.put("caption", sec.caption ?: "")
                secObj.put("calloutType", sec.calloutType.name)
                secObj.put("calloutTitle", sec.calloutTitle ?: "")
                secObj.put("calloutText", sec.calloutText ?: "")
                secArray.put(secObj)
            }
            obj.put("sections", secArray)
            array.put(obj)
        }
        root.put("chapters", array)
        return root.toString(2)
    }

    /**
     * Admin: Save or update an article from inside APK.
     * Writes images & content.json to internal filesDir, updates MMKV,
     * attempts Drive upload, or queues for offline upload.
     * Supports 0, 1, or 2 images per article.
     */
    suspend fun saveOrUpdateArticle(
        article: Chapter,
        imageBytes1: ByteArray?,
        imageExtension1: String = "jpg",
        imageBytes2: ByteArray? = null,
        imageExtension2: String = "jpg"
    ): Pair<Chapter, String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val safeTitle = article.title.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(15)

        // 1. Process slot 1 image
        var updatedThumbnail = article.thumbnail
        if (imageBytes1 != null && imageBytes1.isNotEmpty()) {
            val imgFileName1 = "art_${safeTitle}_1_${now}.$imageExtension1"
            val imgFile1 = File(contentDir, imgFileName1)
            imgFile1.writeBytes(imageBytes1)
            updatedThumbnail = imgFileName1
            MMKVStorage.addToFileList(imgFileName1)
        }

        // 2. Process slot 2 image
        var updatedSecondImage = article.secondImage
        if (imageBytes2 != null && imageBytes2.isNotEmpty()) {
            val imgFileName2 = "art_${safeTitle}_2_${now}.$imageExtension2"
            val imgFile2 = File(contentDir, imgFileName2)
            imgFile2.writeBytes(imageBytes2)
            updatedSecondImage = imgFileName2
            MMKVStorage.addToFileList(imgFileName2)
        }

        // 3. Load existing chapters, replace or add
        val currentChapters = loadChapters().toMutableList()
        val existingIndex = currentChapters.indexOfFirst { it.id == article.id }

        val determinedOrder = if (existingIndex >= 0) {
            article.order
        } else {
            (currentChapters.maxOfOrNull { it.order } ?: 0) + 1
        }

        val articleToSave = article.copy(
            thumbnail = updatedThumbnail,
            secondImage = updatedSecondImage,
            order = determinedOrder,
            source = if (article.source == "builtin") "admin" else article.source
        )

        if (existingIndex >= 0) {
            currentChapters[existingIndex] = articleToSave
        } else {
            currentChapters.add(articleToSave)
        }

        // 4. Save updated content.json to filesDir
        val contentJsonFile = File(contentDir, "content.json")
        val jsonString = serializeChaptersToJson(currentChapters)
        contentJsonFile.writeText(jsonString)

        // 5. Update MMKV
        MMKVStorage.addToFileList("content.json")
        MMKVStorage.saveLastSyncTime(now)
        val syncIndex = JSONObject(MMKVStorage.getSyncIndex()).apply {
            put("content.json", now.toString())
            if (updatedThumbnail != null) put(updatedThumbnail, now.toString())
            if (updatedSecondImage != null) put(updatedSecondImage, now.toString())
        }
        MMKVStorage.saveSyncIndex(syncIndex.toString())

        // 6. Check Drive upload readiness
        val isNetAvailable = isOnline()
        val folderId = extractFolderId(MMKVStorage.getFolderId())
        val oauthToken = MMKVStorage.getDriveOAuthToken()
        val uploadEndpoint = MMKVStorage.getDriveUploadEndpoint()

        var statusMessage = ""
        var finalUploadStatus = "SAVED_OFFLINE"

        if (!isNetAvailable) {
            finalUploadStatus = "SAVED_OFFLINE"
            statusMessage = "Saved offline, will upload when online"
            MMKVStorage.addPendingUpload(
                PendingUpload(
                    id = UUID.randomUUID().toString(),
                    action = "UPSERT",
                    articleId = articleToSave.id,
                    title = articleToSave.title,
                    localImageName = updatedThumbnail,
                    localSecondImageName = updatedSecondImage
                )
            )
        } else if (folderId.isNotEmpty() && (oauthToken.isNotEmpty() || uploadEndpoint.isNotEmpty())) {
            // Attempt upload to Google Drive
            val uploadSuccess = uploadToDrive(
                article = articleToSave,
                imageFileName1 = updatedThumbnail,
                imageFileName2 = updatedSecondImage,
                contentJsonString = jsonString,
                folderId = folderId,
                oauthToken = oauthToken,
                uploadEndpoint = uploadEndpoint
            )
            if (uploadSuccess) {
                finalUploadStatus = "SAVED_TO_DRIVE"
                statusMessage = "Saved to Drive"
            } else {
                finalUploadStatus = "SAVED_OFFLINE"
                statusMessage = "Drive upload queued (will auto-retry)"
                MMKVStorage.addPendingUpload(
                    PendingUpload(
                        id = UUID.randomUUID().toString(),
                        action = "UPSERT",
                        articleId = articleToSave.id,
                        title = articleToSave.title,
                        localImageName = updatedThumbnail,
                        localSecondImageName = updatedSecondImage
                    )
                )
            }
        } else {
            // No Drive write credentials configured yet
            finalUploadStatus = "SAVED_OFFLINE"
            statusMessage = "Saved offline (configure Drive write credentials to sync to cloud)"
            MMKVStorage.addPendingUpload(
                PendingUpload(
                    id = UUID.randomUUID().toString(),
                    action = "UPSERT",
                    articleId = articleToSave.id,
                    title = articleToSave.title,
                    localImageName = updatedThumbnail,
                    localSecondImageName = updatedSecondImage
                )
            )
        }

        // Update the article's uploadStatus in the local file
        val finalArticle = articleToSave.copy(uploadStatus = finalUploadStatus)
        val reUpdatedChapters = currentChapters.map { if (it.id == finalArticle.id) finalArticle else it }
        contentJsonFile.writeText(serializeChaptersToJson(reUpdatedChapters))

        return@withContext Pair(finalArticle, statusMessage)
    }

    suspend fun saveOrUpdateArticle(
        article: Chapter,
        imageBytes: ByteArray?,
        imageExtension: String = "jpg"
    ): Pair<Chapter, String> = saveOrUpdateArticle(article, imageBytes, imageExtension, null, "jpg")

    /**
     * Delete an article locally ONLY.
     * Removes from local content.json and filesDir cache.
     * Does NOT delete it from Google Drive permanently (leaves Drive copy content.json and images untouched).
     * No Drive upload of post-delete content.json and no DELETE pending-upload queueing.
     */
    suspend fun deleteArticle(articleId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val currentChapters = loadChapters().toMutableList()
        val target = currentChapters.find { it.id == articleId } ?: return@withContext Pair(false, "Article not found")

        // If local custom images exist, remove locally
        if (!target.thumbnail.isNullOrBlank()) {
            val imgFile = File(contentDir, target.thumbnail)
            if (imgFile.exists()) {
                imgFile.delete()
                MMKVStorage.removeFromFileList(target.thumbnail)
            }
        }
        if (!target.secondImage.isNullOrBlank()) {
            val imgFile2 = File(contentDir, target.secondImage)
            if (imgFile2.exists()) {
                imgFile2.delete()
                MMKVStorage.removeFromFileList(target.secondImage)
            }
        }

        currentChapters.removeAll { it.id == articleId }

        // Update content.json locally
        val contentJsonFile = File(contentDir, "content.json")
        val jsonString = serializeChaptersToJson(currentChapters)
        contentJsonFile.writeText(jsonString)

        val now = System.currentTimeMillis()
        MMKVStorage.saveLastSyncTime(now)

        return@withContext Pair(true, "Deleted locally. Drive copy preserved.")
    }

    /**
     * Upload an article and content.json to Google Drive.
     */
    private fun uploadToDrive(
        article: Chapter,
        imageFileName1: String?,
        imageFileName2: String?,
        contentJsonString: String,
        folderId: String,
        oauthToken: String,
        uploadEndpoint: String
    ): Boolean {
        try {
            // Upload image 1 first if present
            if (!imageFileName1.isNullOrBlank()) {
                val imgFile1 = File(contentDir, imageFileName1)
                if (imgFile1.exists()) {
                    uploadFileToDrive(imgFile1, folderId, oauthToken, uploadEndpoint)
                }
            }
            // Upload image 2 if present
            if (!imageFileName2.isNullOrBlank()) {
                val imgFile2 = File(contentDir, imageFileName2)
                if (imgFile2.exists()) {
                    uploadFileToDrive(imgFile2, folderId, oauthToken, uploadEndpoint)
                }
            }

            // Upload updated content.json
            return uploadContentJsonToDrive(contentJsonString, folderId, oauthToken, uploadEndpoint)
        } catch (e: Exception) {
            Log.e("SyncRepository", "Drive upload error: ${e.message}", e)
            return false
        }
    }

    private fun uploadFileToDrive(
        file: File,
        folderId: String,
        oauthToken: String,
        uploadEndpoint: String
    ): Boolean {
        try {
            if (uploadEndpoint.isNotEmpty()) {
                // Post BASE64 JSON {folderId, fileName, mimeType, base64} to Apps Script Web App
                val fileBytes = file.readBytes()
                val base64Str = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
                val mimeType = when {
                    file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    else -> "image/jpeg"
                }
                val payload = JSONObject().apply {
                    put("folderId", folderId)
                    put("fileName", file.name)
                    put("filename", file.name)
                    put("mimeType", mimeType)
                    put("base64", base64Str)
                }
                val reqBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url(uploadEndpoint)
                    .post(reqBody)
                    .build()
                val resp = client.newCall(req).execute()
                return resp.isSuccessful
            }

            if (oauthToken.isNotEmpty()) {
                // Drive v3 multipart upload
                val boundary = "=====" + System.currentTimeMillis() + "====="
                val metadata = JSONObject().apply {
                    put("name", file.name)
                    put("parents", JSONArray().put(folderId))
                }

                val bodyStr = "--$boundary\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                        metadata.toString() + "\r\n" +
                        "--$boundary\r\n" +
                        "Content-Type: image/jpeg\r\n\r\n"

                val bodyEnd = "\r\n--$boundary--\r\n"
                val bodyBytes = bodyStr.toByteArray() + file.readBytes() + bodyEnd.toByteArray()

                val reqBody = bodyBytes.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $oauthToken")
                    .post(reqBody)
                    .build()

                val resp = client.newCall(req).execute()
                return resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error uploading file ${file.name}", e)
        }
        return false
    }

    private fun uploadContentJsonToDrive(
        contentJsonString: String,
        folderId: String,
        oauthToken: String,
        uploadEndpoint: String
    ): Boolean {
        try {
            if (uploadEndpoint.isNotEmpty()) {
                val jsonBytes = contentJsonString.toByteArray(Charsets.UTF_8)
                val base64Str = android.util.Base64.encodeToString(jsonBytes, android.util.Base64.NO_WRAP)
                val payload = JSONObject().apply {
                    put("folderId", folderId)
                    put("fileName", "content.json")
                    put("filename", "content.json")
                    put("mimeType", "application/json")
                    put("base64", base64Str)
                }
                val reqBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url(uploadEndpoint)
                    .post(reqBody)
                    .build()
                val resp = client.newCall(req).execute()
                return resp.isSuccessful
            }

            if (oauthToken.isNotEmpty()) {
                val boundary = "=====" + System.currentTimeMillis() + "====="
                val metadata = JSONObject().apply {
                    put("name", "content.json")
                    put("parents", JSONArray().put(folderId))
                }

                val bodyStr = "--$boundary\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                        metadata.toString() + "\r\n" +
                        "--$boundary\r\n" +
                        "Content-Type: application/json\r\n\r\n" +
                        contentJsonString + "\r\n" +
                        "--$boundary--\r\n"

                val reqBody = bodyStr.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $oauthToken")
                    .post(reqBody)
                    .build()

                val resp = client.newCall(req).execute()
                return resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error uploading content.json", e)
        }
        return false
    }

    /**
     * Process pending offline upload queue
     */
    suspend fun processPendingUploads(): Int = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext 0

        val queue = MMKVStorage.getPendingUploads()
        if (queue.isEmpty()) return@withContext 0

        val folderId = extractFolderId(MMKVStorage.getFolderId())
        val oauthToken = MMKVStorage.getDriveOAuthToken()
        val uploadEndpoint = MMKVStorage.getDriveUploadEndpoint()

        if (folderId.isEmpty() || (oauthToken.isEmpty() && uploadEndpoint.isEmpty())) {
            return@withContext 0
        }

        var successCount = 0
        val contentJsonFile = File(contentDir, "content.json")
        val currentJson = if (contentJsonFile.exists()) contentJsonFile.readText() else ""

        for (item in queue) {
            try {
                if (item.action == "UPSERT") {
                    if (!item.localImageName.isNullOrBlank()) {
                        val imgFile = File(contentDir, item.localImageName)
                        if (imgFile.exists()) {
                            uploadFileToDrive(imgFile, folderId, oauthToken, uploadEndpoint)
                        }
                    }
                    if (!item.localSecondImageName.isNullOrBlank()) {
                        val imgFile2 = File(contentDir, item.localSecondImageName)
                        if (imgFile2.exists()) {
                            uploadFileToDrive(imgFile2, folderId, oauthToken, uploadEndpoint)
                        }
                    }
                    if (currentJson.isNotEmpty()) {
                        uploadContentJsonToDrive(currentJson, folderId, oauthToken, uploadEndpoint)
                    }
                    MMKVStorage.removePendingUpload(item.id)
                    successCount++
                } else if (item.action == "DELETE") {
                    // Delete is strictly local; drop any stale DELETE items from older queues
                    MMKVStorage.removePendingUpload(item.id)
                    successCount++
                }
            } catch (e: Exception) {
                Log.e("SyncRepository", "Failed to process queue item ${item.id}", e)
            }
        }

        if (successCount > 0) {
            // Update chapters uploadStatus to SAVED_TO_DRIVE
            val chapters = loadChapters().map { ch ->
                if (ch.uploadStatus == "SAVED_OFFLINE") ch.copy(uploadStatus = "SAVED_TO_DRIVE") else ch
            }
            contentJsonFile.writeText(serializeChaptersToJson(chapters))
            MMKVStorage.saveLastSyncTime(System.currentTimeMillis())
        }

        return@withContext successCount
    }

    suspend fun syncFromDrive(
        onProgress: (String) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext SyncResult.Offline
        }

        val rawFolderId = MMKVStorage.getFolderId()
        val uploadEndpoint = MMKVStorage.getDriveUploadEndpoint().trim()
        val customUrl = MMKVStorage.getCustomSyncUrl().trim()
        val apiKey = MMKVStorage.getDriveApiKey().trim()
        val folderId = extractFolderId(rawFolderId)

        val builtInDrawables = setOf(
            "img_hose_intro", "img_hose_types", "img_hose_fittings",
            "img_hose_install", "img_hose_safety", "ic_hydraulic_hose"
        )

        try {
            // BUG 3: Reader sync needs no API key.
            // Check Google Drive via Apps Script download action (GET <exec>?action=download&filename=content.json)
            if (uploadEndpoint.isNotEmpty() || customUrl.isNotEmpty()) {
                onProgress("Checking Google Drive for handbook updates...")
                val driveJson = downloadContentJsonFromAppsScript(uploadEndpoint, customUrl, folderId)
                if (!driveJson.isNullOrBlank()) {
                    val driveChapters = try {
                        parseChaptersJson(driveJson, "drive")
                    } catch (e: Exception) {
                        emptyList()
                    }

                    if (driveChapters.isNotEmpty()) {
                        onProgress("Downloading images for ${driveChapters.size} chapters...")

                        // Collect all image filenames from all chapters (both slots and sections)
                        val imagesToDownload = mutableSetOf<String>()
                        for (ch in driveChapters) {
                            ch.thumbnail?.takeIf { it.isNotBlank() && !it.startsWith("http") && it !in builtInDrawables }?.let { imagesToDownload.add(it) }
                            ch.secondImage?.takeIf { it.isNotBlank() && !it.startsWith("http") && it !in builtInDrawables }?.let { imagesToDownload.add(it) }
                            for (sec in ch.sections) {
                                sec.image?.takeIf { it.isNotBlank() && !it.startsWith("http") && it !in builtInDrawables }?.let { imagesToDownload.add(it) }
                            }
                        }

                        var downloadedCount = 0
                        for (imgName in imagesToDownload) {
                            val targetFile = File(contentDir, imgName)
                            if (!targetFile.exists() || !isValidImageFile(targetFile)) {
                                val ok = downloadFileFromAppsScript(uploadEndpoint, customUrl, folderId, imgName, targetFile)
                                if (ok) downloadedCount++
                            }
                        }

                        // Validate images on chapters: if an image is corrupt or missing, do NOT mark the article as having a valid image
                        val verifiedChapters = driveChapters.map { ch ->
                            val validThumb = if (ch.thumbnail in builtInDrawables) {
                                ch.thumbnail
                            } else if (!ch.thumbnail.isNullOrBlank()) {
                                val f = File(contentDir, ch.thumbnail)
                                if (f.exists() && isValidImageFile(f)) ch.thumbnail else null
                            } else null

                            val validSecond = if (ch.secondImage in builtInDrawables) {
                                ch.secondImage
                            } else if (!ch.secondImage.isNullOrBlank()) {
                                val f = File(contentDir, ch.secondImage)
                                if (f.exists() && isValidImageFile(f)) ch.secondImage else null
                            } else null

                            ch.copy(
                                thumbnail = validThumb,
                                secondImage = validSecond
                            )
                        }.sortedBy { it.order }

                        // Save updated content.json to local storage
                        val contentJsonFile = File(contentDir, "content.json")
                        contentJsonFile.writeText(serializeChaptersToJson(verifiedChapters))

                        val now = System.currentTimeMillis()
                        MMKVStorage.saveLastSyncTime(now)
                        val fileList = MMKVStorage.getFileList().toMutableSet()
                        fileList.add("content.json")
                        for (ch in verifiedChapters) {
                            ch.thumbnail?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
                            ch.secondImage?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
                            for (s in ch.sections) {
                                s.image?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
                            }
                        }
                        MMKVStorage.saveFileList(fileList)

                        val msg = "Handbook updated from Google Drive (${verifiedChapters.size} chapters)"
                        MMKVStorage.saveSyncResult("success", msg)
                        return@withContext SyncResult.Success(msg, verifiedChapters.size + downloadedCount)
                    }
                }
            }

            // Fallback: If an API key is provided, query Drive v3
            if (apiKey.isNotEmpty()) {
                onProgress("Querying Google Drive API...")
                return@withContext syncViaDriveApi(folderId, apiKey, onProgress)
            }

            // Fallback: Read local cached chapters (or built-in chapters if no cache)
            val local = loadChapters()
            val msg = "Reading saved data (${local.size} chapters)"
            MMKVStorage.saveSyncResult("success", msg)
            return@withContext SyncResult.Success(msg, local.size)
        } catch (e: Exception) {
            Log.e("SyncRepository", "Sync failed", e)
            val local = loadChapters()
            if (local.isNotEmpty()) {
                return@withContext SyncResult.Success("Offline - reading saved data (${local.size} chapters)", local.size)
            }
            val msg = e.localizedMessage ?: "Unknown network error during sync"
            MMKVStorage.saveSyncResult("error", msg)
            SyncResult.Failure(msg)
        }
    }

    private fun syncViaDriveApi(
        folderId: String,
        apiKey: String,
        onProgress: (String) -> Unit
    ): SyncResult {
        val queryUrl = "https://www.googleapis.com/drive/v3/files?q='%27$folderId%27+in+parents+and+trashed=false'&fields=files(id,name,mimeType,modifiedTime,size)&key=$apiKey"

        val request = Request.Builder().url(queryUrl).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val errorMsg = "Drive API returned code ${response.code}: ${response.message}. Check API key and folder permissions."
            Log.e("SyncRepository", "$errorMsg - Body: $errorBody")
            MMKVStorage.saveSyncResult("error", errorMsg)
            return SyncResult.Failure(errorMsg)
        }

        val bodyString = response.body?.string() ?: "{}"
        val root = JSONObject(bodyString)
        val filesArray = root.optJSONArray("files") ?: JSONArray()

        if (filesArray.length() == 0) {
            val msg = "Drive folder is empty or not accessible with current credentials."
            MMKVStorage.saveSyncResult("warning", msg)
            return SyncResult.Failure(msg)
        }

        val downloadedFiles = mutableSetOf<String>()
        val fileIndexObj = JSONObject()

        onProgress("Found ${filesArray.length()} files. Downloading content & images...")

        for (i in 0 until filesArray.length()) {
            val f = filesArray.getJSONObject(i)
            val fileId = f.getString("id")
            val fileName = f.getString("name")
            val modifiedTime = f.optString("modifiedTime", "")

            onProgress("Downloading $fileName (${i + 1}/${filesArray.length()})...")

            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=$apiKey"
            val fileReq = Request.Builder().url(downloadUrl).build()
            val fileResp = client.newCall(fileReq).execute()

            if (fileResp.isSuccessful) {
                val targetFile = File(contentDir, fileName)
                fileResp.body?.byteStream()?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                downloadedFiles.add(fileName)
                fileIndexObj.put(fileName, modifiedTime)
            }
        }

        val now = System.currentTimeMillis()
        MMKVStorage.saveLastSyncTime(now)
        MMKVStorage.saveFileList(downloadedFiles)
        MMKVStorage.saveSyncIndex(fileIndexObj.toString())
        MMKVStorage.saveSyncResult("success", "Successfully synced ${downloadedFiles.size} files from Google Drive.")

        return SyncResult.Success("Synced ${downloadedFiles.size} files from Google Drive", downloadedFiles.size)
    }

    private fun syncFromCustomUrl(
        url: String,
        onProgress: (String) -> Unit
    ): SyncResult {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val err = "HTTP ${response.code}: ${response.message}"
            MMKVStorage.saveSyncResult("error", err)
            return SyncResult.Failure(err)
        }

        val body = response.body?.string() ?: ""
        val target = File(contentDir, "content.json")
        target.writeText(body)

        val downloadedFiles = mutableSetOf("content.json")
        val now = System.currentTimeMillis()
        MMKVStorage.saveLastSyncTime(now)
        MMKVStorage.saveFileList(downloadedFiles)
        MMKVStorage.saveSyncIndex("{\"content.json\": \"$now\"}")
        MMKVStorage.saveSyncResult("success", "Updated content.json from sync endpoint.")

        return SyncResult.Success("Synced content.json successfully", 1)
    }

    /**
     * Restore articles from Google Drive via the Apps Script endpoint
     * (GET <exec>?action=download&filename=content.json, and download listed images by filename)
     * and merges into local content.json every chapter whose id is missing locally,
     * downloading all referenced images (including secondImage and section images) into filesDir/hydraulic_content.
     */
    suspend fun restoreFromDrive(onProgress: (String) -> Unit): SyncResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext SyncResult.Offline

        val uploadEndpoint = MMKVStorage.getDriveUploadEndpoint().trim()
        val customUrl = MMKVStorage.getCustomSyncUrl().trim()
        val folderId = extractFolderId(MMKVStorage.getFolderId())

        if (uploadEndpoint.isEmpty() && customUrl.isEmpty()) {
            val msg = "Restore failed: No Apps Script endpoint or Sync URL configured in Admin Settings."
            MMKVStorage.saveSyncResult("error", msg)
            return@withContext SyncResult.Failure(msg)
        }

        onProgress("Downloading content.json from Google Drive via Apps Script...")

        val driveJson: String? = downloadContentJsonFromAppsScript(uploadEndpoint, customUrl, folderId)
        if (driveJson.isNullOrBlank()) {
            val msg = "Restore failed: Could not download content.json from Drive (endpoint returned empty or error). Verify the Apps Script URL in Admin Settings."
            MMKVStorage.saveSyncResult("error", msg)
            return@withContext SyncResult.Failure(msg)
        }

        val driveChapters = try {
            parseChaptersJson(driveJson, "drive")
        } catch (e: Exception) {
            val msg = "Restore failed: Invalid content.json format from Drive: ${e.localizedMessage}"
            MMKVStorage.saveSyncResult("error", msg)
            return@withContext SyncResult.Failure(msg)
        }

        if (driveChapters.isEmpty()) {
            val msg = "Drive folder content.json contains no articles."
            MMKVStorage.saveSyncResult("warning", msg)
            return@withContext SyncResult.Failure(msg)
        }

        val local = loadChapters()
        val localIds = local.map { it.id }.toSet()
        val missing = driveChapters.filter { it.id !in localIds }

        onProgress("Restoring articles and downloading images...")

        // Collect all images from all drive chapters (both missing and existing articles with missing local image files)
        val imagesToDownload = mutableSetOf<String>()
        val builtInDrawables = setOf(
            "img_hose_intro", "img_hose_types", "img_hose_fittings",
            "img_hose_install", "img_hose_safety", "ic_hydraulic_hose"
        )

        for (ch in driveChapters) {
            ch.thumbnail?.takeIf { it.isNotBlank() && !it.startsWith("http") && it !in builtInDrawables }?.let { imagesToDownload.add(it) }
            ch.secondImage?.takeIf { it.isNotBlank() && !it.startsWith("http") && it !in builtInDrawables }?.let { imagesToDownload.add(it) }
            for (sec in ch.sections) {
                sec.image?.takeIf { it.isNotBlank() && !it.startsWith("http") && it !in builtInDrawables }?.let { imagesToDownload.add(it) }
            }
        }

        var downloadedImagesCount = 0
        for (imgName in imagesToDownload) {
            val targetFile = File(contentDir, imgName)
            if (!targetFile.exists() || !isValidImageFile(targetFile)) {
                onProgress("Downloading image $imgName...")
                try {
                    val success = downloadFileFromAppsScript(uploadEndpoint, customUrl, folderId, imgName, targetFile)
                    if (success) {
                        downloadedImagesCount++
                    }
                } catch (e: Exception) {
                    Log.w("SyncRepository", "Skipping missing image $imgName: ${e.message}")
                }
            }
        }

        if (missing.isEmpty() && downloadedImagesCount == 0) {
            val msg = "All ${driveChapters.size} Drive articles and their images already exist locally."
            MMKVStorage.saveSyncResult("success", msg)
            return@withContext SyncResult.Success(msg, 0)
        }

        // Validate images for missing chapters before merging
        val verifiedMissing = missing.map { ch ->
            val validThumb = if (ch.thumbnail in builtInDrawables) {
                ch.thumbnail
            } else if (!ch.thumbnail.isNullOrBlank()) {
                val f = File(contentDir, ch.thumbnail)
                if (f.exists() && isValidImageFile(f)) ch.thumbnail else null
            } else null

            val validSecond = if (ch.secondImage in builtInDrawables) {
                ch.secondImage
            } else if (!ch.secondImage.isNullOrBlank()) {
                val f = File(contentDir, ch.secondImage)
                if (f.exists() && isValidImageFile(f)) ch.secondImage else null
            } else null

            ch.copy(
                thumbnail = validThumb,
                secondImage = validSecond
            )
        }

        // Merge into local content.json
        val merged = (local + verifiedMissing).sortedBy { it.order }
        val contentJsonFile = File(contentDir, "content.json")
        contentJsonFile.writeText(serializeChaptersToJson(merged))

        val now = System.currentTimeMillis()
        MMKVStorage.saveLastSyncTime(now)
        val fileList = MMKVStorage.getFileList().toMutableSet()
        fileList.add("content.json")
        for (ch in driveChapters) {
            ch.thumbnail?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
            ch.secondImage?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
            for (s in ch.sections) {
                s.image?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
            }
        }
        MMKVStorage.saveFileList(fileList)

        val successMsg = if (missing.isNotEmpty()) {
            "Successfully restored ${missing.size} article(s) and $downloadedImagesCount image(s) from Google Drive."
        } else {
            "Successfully restored $downloadedImagesCount missing image(s) from Google Drive."
        }
        MMKVStorage.saveSyncResult("success", successMsg)
        return@withContext SyncResult.Success(successMsg, missing.size + downloadedImagesCount)
    }

    fun isRealImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val isJpeg = (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte())
        val isPng = (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte())
        val isWebp = (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte())
        val isGif = (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte())
        return isJpeg || isPng || isWebp || isGif
    }

    fun isValidImageFile(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            val header = ByteArray(12)
            file.inputStream().use { it.read(header) }
            isRealImageBytes(header)
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadContentJsonFromAppsScript(
        uploadEndpoint: String,
        customUrl: String,
        folderId: String
    ): String? {
        // 1. Try Apps Script GET: <exec>?action=download&filename=content.json&folderId=...
        if (uploadEndpoint.isNotEmpty()) {
            val sep = if (uploadEndpoint.contains("?")) "&" else "?"
            val folderParam = if (folderId.isNotEmpty()) "&folderId=$folderId" else ""
            val url = "$uploadEndpoint${sep}action=download&filename=content.json$folderParam"
            try {
                val req = Request.Builder().url(url).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank() && (body.contains("chapters") || body.contains("articles") || body.startsWith("["))) {
                        return body
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncRepository", "Apps Script content.json download failed: ${e.message}")
            }
        }

        // 2. Try customUrl if present
        if (customUrl.isNotEmpty()) {
            try {
                val req = Request.Builder().url(customUrl).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank() && (body.contains("chapters") || body.contains("articles") || body.startsWith("["))) {
                        return body
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncRepository", "CustomUrl content.json download failed: ${e.message}")
            }
        }

        return null
    }

    private fun downloadFileFromAppsScript(
        uploadEndpoint: String,
        customUrl: String,
        folderId: String,
        filename: String,
        targetFile: File
    ): Boolean {
        val endpoint = uploadEndpoint.ifBlank { customUrl }
        if (endpoint.isNotEmpty()) {
            val sep = if (endpoint.contains("?")) "&" else "?"
            val folderParam = if (folderId.isNotEmpty()) "&folderId=$folderId" else ""
            val encFilename = try {
                java.net.URLEncoder.encode(filename, "UTF-8")
            } catch (_: Exception) {
                filename
            }
            val url = "$endpoint${sep}action=download&filename=$encFilename$folderParam"
            try {
                val req = Request.Builder().url(url).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val rawBytes = resp.body?.bytes()
                    if (rawBytes != null && rawBytes.size >= 12) {
                        // 1. Skip HTML error or login pages
                        if (rawBytes[0] == '<'.code.toByte()) {
                            Log.w("SyncRepository", "Apps Script download returned HTML page instead of image for $filename")
                            if (targetFile.exists()) targetFile.delete()
                            return false
                        }

                        // 2. Only base64-decode if the response body is actually a JSON/base64 wrapper
                        val imageBytes: ByteArray = if (rawBytes[0] == '{'.code.toByte()) {
                            try {
                                val jsonStr = String(rawBytes, Charsets.UTF_8)
                                val obj = JSONObject(jsonStr)
                                val base64Str = obj.optString("base64").ifBlank { obj.optString("data") }
                                if (base64Str.isNotBlank()) {
                                    android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                                } else {
                                    Log.w("SyncRepository", "Apps Script reported error for $filename: ${obj.optString("message", "File not found")}")
                                    if (targetFile.exists()) targetFile.delete()
                                    return false
                                }
                            } catch (_: Exception) {
                                rawBytes
                            }
                        } else {
                            // Raw blob bytes AS-IS
                            rawBytes
                        }

                        // 3. Validate image magic bytes (JPEG FF D8 FF / PNG / WEBP)
                        if (!isRealImageBytes(imageBytes)) {
                            Log.w("SyncRepository", "Corrupt or non-image data received for $filename, skipping file")
                            if (targetFile.exists()) targetFile.delete()
                            return false
                        }

                        // 4. Save bytes unchanged to filesDir/hydraulic_content under exact filename
                        targetFile.writeBytes(imageBytes)
                        MMKVStorage.addToFileList(filename)
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncRepository", "Failed downloading image $filename: ${e.message}")
            }
        }
        return false
    }

    fun clearSavedData(): Boolean {
        try {
            contentDir.deleteRecursively()
            contentDir.mkdirs()
            MMKVStorage.clearAllSyncData()
            return true
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error clearing saved data", e)
            return false
        }
    }

    fun extractFolderId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("/folders/")) {
            return trimmed.substringAfter("/folders/").substringBefore("?").substringBefore("/")
        }
        if (trimmed.contains("id=")) {
            return trimmed.substringAfter("id=").substringBefore("&")
        }
        return trimmed
    }
}
