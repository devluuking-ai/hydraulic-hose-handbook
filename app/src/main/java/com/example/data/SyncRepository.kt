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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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
     * FIX (b): One-time MMKV migration (flag demo_cleanup_done) that runs on first launch
     * of the new APK: drops demo chapters 6-7 (chapter_6_troubleshooting, chapter_7_crimper)
     * and restores chapters 1-5 from DefaultChapters, while KEEPING any admin-added articles
     * (source 'admin' or id starting with custom_).
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

        // Keep admin created articles, drop demo chapters (chapter_6_troubleshooting, chapter_7_crimper)
        val adminArticles = currentList.filter { ch ->
            (ch.source == "admin" || ch.id.startsWith("custom_")) &&
                    ch.id != "chapter_6_troubleshooting" && ch.id != "chapter_7_crimper" &&
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

            // Skip removed demo chapters 6 and 7 if present in Drive content.json
            if (id == "chapter_6_troubleshooting" || id == "chapter_7_crimper") {
                continue
            }

            val title = item.optString("title", "Untitled Chapter")
            val subtitle = item.optString("subtitle", "")
            val category = item.optString("category", "General")
            val order = item.optInt("order", i + 1)
            val readingTime = item.optInt("readingTimeMinutes", 5)
            val thumbnail = item.optString("thumbnail").ifBlank { item.optString("image") }.takeIf { it.isNotBlank() }
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
     * Writes image & content.json to internal filesDir, updates MMKV,
     * attempts Drive upload, or queues for offline upload.
     */
    suspend fun saveOrUpdateArticle(
        article: Chapter,
        imageBytes: ByteArray?,
        imageExtension: String = "jpg"
    ): Pair<Chapter, String> = withContext(Dispatchers.IO) {
        // 1. If new image bytes were provided, save to filesDir/hydraulic_content/
        var updatedThumbnail = article.thumbnail
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            val safeTitle = article.title.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(15)
            val imgFileName = "art_${safeTitle}_${System.currentTimeMillis()}.$imageExtension"
            val imgFile = File(contentDir, imgFileName)
            imgFile.writeBytes(imageBytes)
            updatedThumbnail = imgFileName
            MMKVStorage.addToFileList(imgFileName)
        }

        // 2. Load existing chapters, replace or add
        val currentChapters = loadChapters().toMutableList()
        val existingIndex = currentChapters.indexOfFirst { it.id == article.id }

        val determinedOrder = if (existingIndex >= 0) {
            article.order
        } else {
            (currentChapters.maxOfOrNull { it.order } ?: 0) + 1
        }

        val articleToSave = article.copy(
            thumbnail = updatedThumbnail,
            order = determinedOrder,
            source = if (article.source == "builtin") "admin" else article.source
        )

        if (existingIndex >= 0) {
            currentChapters[existingIndex] = articleToSave
        } else {
            currentChapters.add(articleToSave)
        }

        // 3. Save updated content.json to filesDir
        val contentJsonFile = File(contentDir, "content.json")
        val jsonString = serializeChaptersToJson(currentChapters)
        contentJsonFile.writeText(jsonString)

        // 4. Update MMKV
        val now = System.currentTimeMillis()
        MMKVStorage.addToFileList("content.json")
        MMKVStorage.saveLastSyncTime(now)
        val syncIndex = JSONObject(MMKVStorage.getSyncIndex()).apply {
            put("content.json", now.toString())
            if (updatedThumbnail != null) put(updatedThumbnail, now.toString())
        }
        MMKVStorage.saveSyncIndex(syncIndex.toString())

        // 5. Check Drive upload readiness
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
                    localImageName = updatedThumbnail
                )
            )
        } else if (folderId.isNotEmpty() && (oauthToken.isNotEmpty() || uploadEndpoint.isNotEmpty())) {
            // Attempt upload to Google Drive
            val uploadSuccess = uploadToDrive(articleToSave, updatedThumbnail, jsonString, folderId, oauthToken, uploadEndpoint)
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
                        localImageName = updatedThumbnail
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
                    localImageName = updatedThumbnail
                )
            )
        }

        // Update the article's uploadStatus in the local file
        val finalArticle = articleToSave.copy(uploadStatus = finalUploadStatus)
        val reUpdatedChapters = currentChapters.map { if (it.id == finalArticle.id) finalArticle else it }
        contentJsonFile.writeText(serializeChaptersToJson(reUpdatedChapters))

        return@withContext Pair(finalArticle, statusMessage)
    }

    /**
     * FIX (c): Delete an article locally ONLY.
     * Removes from local content.json and filesDir cache.
     * Does NOT delete it from Google Drive permanently (leaves Drive copy content.json and images untouched).
     * No Drive upload of post-delete content.json and no DELETE pending-upload queueing.
     */
    suspend fun deleteArticle(articleId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val currentChapters = loadChapters().toMutableList()
        val target = currentChapters.find { it.id == articleId } ?: return@withContext Pair(false, "Article not found")

        // If local custom image exists, remove it locally
        if (!target.thumbnail.isNullOrBlank()) {
            val imgFile = File(contentDir, target.thumbnail)
            if (imgFile.exists()) {
                imgFile.delete()
                MMKVStorage.removeFromFileList(target.thumbnail)
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
        imageFileName: String?,
        contentJsonString: String,
        folderId: String,
        oauthToken: String,
        uploadEndpoint: String
    ): Boolean {
        try {
            // Upload image first if present
            if (!imageFileName.isNullOrBlank()) {
                val imgFile = File(contentDir, imageFileName)
                if (imgFile.exists()) {
                    uploadFileToDrive(imgFile, folderId, oauthToken, uploadEndpoint)
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
                // Post to custom webhook or Google Apps Script Web App
                val requestBody = file.readBytes().toRequestBody("application/octet-stream".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$uploadEndpoint?filename=${file.name}&folderId=$folderId")
                    .post(requestBody)
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
                val reqBody = contentJsonString.toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$uploadEndpoint?filename=content.json&folderId=$folderId")
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
        val apiKey = MMKVStorage.getDriveApiKey()
        val customUrl = MMKVStorage.getCustomSyncUrl()

        val folderId = extractFolderId(rawFolderId)

        if (folderId.isEmpty() && customUrl.isEmpty()) {
            return@withContext SyncResult.Failure("No Google Drive Folder ID or Sync URL configured. Please enter Folder ID in Admin Settings.")
        }

        try {
            if (customUrl.isNotEmpty()) {
                onProgress("Connecting to custom sync URL...")
                return@withContext syncFromCustomUrl(customUrl, onProgress)
            }

            if (apiKey.isNotEmpty()) {
                onProgress("Querying Google Drive API for folder contents...")
                return@withContext syncViaDriveApi(folderId, apiKey, onProgress)
            } else {
                onProgress("Checking Drive API Key...")
                return@withContext syncDirectOrPromptApiKey(folderId, onProgress)
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Sync failed", e)
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

    /**
     * FIX (b): Plain explanation when syncing without a Drive API key.
     * No dead-end, no demo fallback.
     */
    private fun syncDirectOrPromptApiKey(
        folderId: String,
        onProgress: (String) -> Unit
    ): SyncResult {
        val msg = "Google Drive folder sync requires a free Google Drive API Key to query folder contents without user login. Please add an API Key in Admin Settings."
        MMKVStorage.saveSyncResult("notice", msg)
        return SyncResult.Failure(msg)
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
     * FIX (c): Restore articles from Google Drive via the Apps Script upload endpoint
     * (GET <exec>?action=download&filename=content.json, and download listed images by filename)
     * and merges into local content.json every chapter whose id is missing locally,
     * then refreshes Home. Do NOT use the Drive API for restore.
     * If download fails or Drive content.json is empty, show a clear message saying so.
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

        if (missing.isEmpty()) {
            val msg = "No missing articles found in Drive — all ${driveChapters.size} Drive articles already exist locally."
            MMKVStorage.saveSyncResult("success", msg)
            return@withContext SyncResult.Success(msg, 0)
        }

        onProgress("Restoring ${missing.size} missing article(s) and downloading images...")

        // Download missing images for the restored articles via Apps Script endpoint
        for (ch in missing) {
            val imagesToFetch = mutableSetOf<String>()
            ch.thumbnail?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { imagesToFetch.add(it) }
            for (sec in ch.sections) {
                sec.image?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { imagesToFetch.add(it) }
            }

            for (imgName in imagesToFetch) {
                val targetFile = File(contentDir, imgName)
                if (!targetFile.exists()) {
                    downloadFileFromAppsScript(uploadEndpoint, customUrl, folderId, imgName, targetFile)
                }
            }
        }

        // Merge into local content.json
        val merged = (local + missing).sortedBy { it.order }
        val contentJsonFile = File(contentDir, "content.json")
        contentJsonFile.writeText(serializeChaptersToJson(merged))

        val now = System.currentTimeMillis()
        MMKVStorage.saveLastSyncTime(now)
        val fileList = MMKVStorage.getFileList().toMutableSet()
        fileList.add("content.json")
        for (ch in missing) {
            ch.thumbnail?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
            for (s in ch.sections) {
                s.image?.takeIf { it.isNotBlank() }?.let { fileList.add(it) }
            }
        }
        MMKVStorage.saveFileList(fileList)

        val successMsg = "Successfully restored ${missing.size} article(s) from Google Drive."
        MMKVStorage.saveSyncResult("success", successMsg)
        return@withContext SyncResult.Success(successMsg, missing.size)
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
        if (uploadEndpoint.isNotEmpty()) {
            val sep = if (uploadEndpoint.contains("?")) "&" else "?"
            val folderParam = if (folderId.isNotEmpty()) "&folderId=$folderId" else ""
            val url = "$uploadEndpoint${sep}action=download&filename=$filename$folderParam"
            try {
                val req = Request.Builder().url(url).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        targetFile.writeBytes(bytes)
                        MMKVStorage.addToFileList(filename)
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncRepository", "Failed downloading image $filename from Apps Script: ${e.message}")
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
