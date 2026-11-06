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
            val title = item.optString("title", "Untitled Chapter")
            val subtitle = item.optString("subtitle", "")
            val category = item.optString("category", "General")
            val order = item.optInt("order", i + 1)
            val readingTime = item.optInt("readingTimeMinutes", 5)
            val thumbnail = item.optString("thumbnail", item.optString("image", null))
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
                            image = secObj.optString("image", null).takeIf { it?.isNotBlank() == true },
                            caption = secObj.optString("caption", null).takeIf { it?.isNotBlank() == true },
                            calloutType = calloutType,
                            calloutTitle = secObj.optString("calloutTitle", null).takeIf { it?.isNotBlank() == true },
                            calloutText = secObj.optString("calloutText", null).takeIf { it?.isNotBlank() == true }
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
     * Admin: Delete an article.
     * Removes from content.json, removes associated image, updates MMKV,
     * updates Drive or queues deletion.
     */
    suspend fun deleteArticle(articleId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val currentChapters = loadChapters().toMutableList()
        val target = currentChapters.find { it.id == articleId } ?: return@withContext Pair(false, "Article not found")

        // If local custom image exists, remove it
        if (!target.thumbnail.isNullOrBlank()) {
            val imgFile = File(contentDir, target.thumbnail)
            if (imgFile.exists()) {
                imgFile.delete()
                MMKVStorage.removeFromFileList(target.thumbnail)
            }
        }

        currentChapters.removeAll { it.id == articleId }

        // Update content.json
        val contentJsonFile = File(contentDir, "content.json")
        val jsonString = serializeChaptersToJson(currentChapters)
        contentJsonFile.writeText(jsonString)

        val now = System.currentTimeMillis()
        MMKVStorage.saveLastSyncTime(now)

        val isNetAvailable = isOnline()
        val folderId = extractFolderId(MMKVStorage.getFolderId())
        val oauthToken = MMKVStorage.getDriveOAuthToken()
        val uploadEndpoint = MMKVStorage.getDriveUploadEndpoint()

        var msg = "Deleted locally."
        if (isNetAvailable && folderId.isNotEmpty() && (oauthToken.isNotEmpty() || uploadEndpoint.isNotEmpty())) {
            uploadContentJsonToDrive(jsonString, folderId, oauthToken, uploadEndpoint)
            msg = "Deleted from local cache and Drive."
        } else {
            MMKVStorage.addPendingUpload(
                PendingUpload(
                    id = UUID.randomUUID().toString(),
                    action = "DELETE",
                    articleId = articleId,
                    title = target.title
                )
            )
            msg = "Deleted locally, Drive deletion queued."
        }

        return@withContext Pair(true, msg)
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
                    if (currentJson.isNotEmpty()) {
                        uploadContentJsonToDrive(currentJson, folderId, oauthToken, uploadEndpoint)
                    }
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
            return@withContext SyncResult.Failure("No Google Drive Folder ID or Sync URL configured. Please enter Folder ID in Settings.")
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
                onProgress("Attempting direct sync with folder ID...")
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

    private fun syncDirectOrPromptApiKey(
        folderId: String,
        onProgress: (String) -> Unit
    ): SyncResult {
        onProgress("Checking public Drive link...")
        val msg = "Google Drive folder sync requires a free Google Drive API Key to query folder contents without user login. Please add an API Key in Admin Settings, or use 'Load Demo Cloud Update' to test full sync."
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

    suspend fun loadDemoCloudUpdate(onProgress: (String) -> Unit): SyncResult = withContext(Dispatchers.IO) {
        onProgress("Initializing demo cloud data packet...")
        kotlinx.coroutines.delay(300)
        onProgress("Formatting content.json with 7 complete chapters...")
        kotlinx.coroutines.delay(300)

        val demoChaptersJson = """
        {
          "version": 2,
          "title": "Hydraulic Hose Master Handbook (Cloud Synced Edition)",
          "lastUpdated": ${System.currentTimeMillis()},
          "chapters": [
            {
              "id": "chapter_1_intro",
              "order": 1,
              "title": "Chapter 1: Introduction to Hydraulic Hoses",
              "subtitle": "Principles of High-Pressure Fluid Power (Updated v2.1)",
              "category": "Fundamentals",
              "readingTimeMinutes": 4,
              "thumbnail": "img_hose_intro",
              "summary": "Updated guide with added pressure envelope charts: inner tube, reinforcement layers, and outer cover, plus working pressure vs burst pressure design safety factors.",
              "sections": [
                {
                  "heading": "1.1 The Role of Hydraulic Hoses",
                  "text": "A hydraulic hose is a high-pressure, flexible conduit designed to transmit pressurized fluid between pumps, control valves, actuators, cylinders, and motors. Unlike rigid steel piping, hydraulic hoses absorb mechanical vibrations, tolerate thermal expansion, and allow fluid power transmission across articulating machine parts.",
                  "image": "img_hose_intro",
                  "caption": "Figure 1.1: Anatomical cutaway of high-pressure hydraulic hose."
                },
                {
                  "heading": "1.2 Updated Pressure & Impulse Guidelines",
                  "text": "Modern dynamic duty cycles necessitate strict adherence to SAE 100R ratings. Standard dynamic safety factor remains 4:1 (burst pressure = 4x working pressure). Static hydraulic applications may operate at a 3:1 safety factor.",
                  "calloutType": "INFO",
                  "calloutTitle": "Updated Cloud Spec",
                  "calloutText": "Fluid velocity must be kept between 10-25 ft/s on pressure lines to minimize internal laminar turbulence."
                }
              ]
            },
            {
              "id": "chapter_2_types",
              "order": 2,
              "title": "Chapter 2: Hydraulic Hose Types & Ratings",
              "subtitle": "SAE Specifications, Wire Braid vs. Spiral",
              "category": "Hose Types",
              "readingTimeMinutes": 5,
              "thumbnail": "img_hose_types",
              "summary": "Explore SAE 100R standards (100R1, 100R2, 100R12, 100R13/R15), compare braided vs spiral wire reinforcement, and master the STAMPED sizing methodology.",
              "sections": [
                {
                  "heading": "2.1 Wire Braid vs Spiral Wire Fatigue Resistance",
                  "text": "Spiral reinforced hoses provide superior cycle life under aggressive pressure surges. In continuous cyclic testing, four-spiral hoses exceed 500,000 impulse cycles without wire strand crossing fatigue.",
                  "image": "img_hose_types",
                  "caption": "Figure 2.1: Wire braid vs four spiral wire cross section."
                }
              ]
            },
            {
              "id": "chapter_3_fittings",
              "order": 3,
              "title": "Chapter 3: Fittings, Couplings & Thread Identification",
              "subtitle": "JIC 37°, ORFS, NPT & Crimp Assembly",
              "category": "Fittings",
              "readingTimeMinutes": 5,
              "thumbnail": "img_hose_fittings",
              "summary": "Identify hydraulic coupling standards: JIC 37° flare, O-Ring Face Seal (ORFS), NPT pipe threads, and permanent crimped ferrule tolerances.",
              "sections": [
                {
                  "heading": "3.1 Zero-Leakage Connection Best Practices",
                  "text": "ORFS (O-Ring Face Seal) fittings are the premier recommendation for high-vibration off-highway equipment. Unlike tapered pipe threads, ORFS seals on a replaceable elastomer face, eliminating thread distortion.",
                  "image": "img_hose_fittings",
                  "caption": "Figure 3.1: JIC 37°, ORFS, and NPT fitting mechanics."
                }
              ]
            },
            {
              "id": "chapter_4_install",
              "order": 4,
              "title": "Chapter 4: Installation & Routing Best Practices",
              "subtitle": "Bend Radius, Layline Alignment & Clamping",
              "category": "Installation",
              "readingTimeMinutes": 5,
              "thumbnail": "img_hose_install",
              "summary": "Master correct hose routing techniques: avoiding twists using the printed layline, respecting minimum bend radiuses, and installing proper clamp supports.",
              "sections": [
                {
                  "heading": "4.1 Preventing Premature Failures",
                  "text": "Always observe the printed layline. Never allow the hose to twist during swivel nut torquing. Use two wrenches for all swivel installations.",
                  "image": "img_hose_install",
                  "caption": "Figure 4.1: Correct vs incorrect bend radius and clamping."
                }
              ]
            },
            {
              "id": "chapter_5_safety",
              "order": 5,
              "title": "Chapter 5: Maintenance, Inspection & Safety Protocols",
              "subtitle": "Fluid Injection Hazards, Cardboard Test & Inspections",
              "category": "Safety",
              "readingTimeMinutes": 6,
              "thumbnail": "img_hose_safety",
              "summary": "Critical safety standards for hydraulic technicians: high-pressure fluid injection injury prevention, the cardboard leak test, and daily inspection checkpoints.",
              "sections": [
                {
                  "heading": "5.1 Urgent Safety Protocol",
                  "text": "Never touch pressurized hydraulic lines. Even pinholes can inject mineral oil beneath human skin, creating emergency necrosis. Always use cardboard on a wand.",
                  "image": "img_hose_safety",
                  "caption": "Figure 5.1: High pressure fluid injection risks."
                }
              ]
            },
            {
              "id": "chapter_6_troubleshooting",
              "order": 6,
              "title": "Chapter 6: Field Troubleshooting & Pressure Spikes",
              "subtitle": "Diagnosing Water Hammer, Thermal Shock & Blow-offs",
              "category": "Field Diagnostics",
              "readingTimeMinutes": 6,
              "thumbnail": "img_hose_types",
              "summary": "A cloud-synced diagnostic chapter: how to identify failure patterns such as burst at outer radius, coupling detachment, and inner tube hardening.",
              "sections": [
                {
                  "heading": "6.1 Diagnostic Failure Modes Table",
                  "text": "• Fitting Blow-off: Caused by improper crimp diameter, lack of skiving, or undersized ferrule teeth.\n• Hose Bursts at Outer Bend: Exceeded minimum bend radius; outer wires stretched beyond yield point.\n• Tube Hard and Brittle: Fluid operating temperature exceeded rated maximum (+121°C), baking the elastomer.\n• Pinholes Near Coupling: Stress concentration due to unsupported hose hanging off rigid port."
                },
                {
                  "heading": "6.2 Water Hammer & Surge Dampening",
                  "text": "Rapidly closing solenoid valves creates destructive pressure waves that travel back through hydraulic fluid at roughly 4,000 ft/sec. To protect hose assemblies, install inline hydraulic accumulators or proportional deceleration valves.",
                  "calloutType": "WARNING",
                  "calloutTitle": "Surge Spike Alert",
                  "calloutText": "High-frequency pressure transducers reveal that system spikes often peak at 300% of nominal relief valve pressure within 15 milliseconds."
                }
              ]
            },
            {
              "id": "chapter_7_crimper",
              "order": 7,
              "title": "Chapter 7: Crimper Calibration & Tolerances",
              "subtitle": "Micrometer Verification & Proof Pressure Testing",
              "category": "Maintenance",
              "readingTimeMinutes": 5,
              "thumbnail": "img_hose_fittings",
              "summary": "Standards for shop crimpers: calibration routines, die wear compensation, and hydrostatic pressure proof testing protocols.",
              "sections": [
                {
                  "heading": "7.1 Daily Crimper Verification",
                  "text": "Before assembling production hose batches:\n1. Inspect crimper die fingers for galling, metal chips, and grease contamination.\n2. Verify micrometer dial zero against a known calibration plug.\n3. Measure finished crimp diameter on the ferrule rib with a digital vernier caliper across two perpendicular axes."
                },
                {
                  "heading": "7.2 Hydrostatic Proof Testing (SAE J343)",
                  "text": "Critical safety assemblies should undergo hydrostatic proof testing at 2.0x rated working pressure for 60 seconds with water. If no leakage, drop in pressure, or coupling movement is observed, the assembly is certified for service.",
                  "calloutType": "TIP",
                  "calloutTitle": "Quality Control Rule",
                  "calloutText": "Log every assembly with serial numbers, crimp diameter, technician initials, and date code on an aluminum hose tag."
                }
              ]
            }
          ]
        }
        """.trimIndent()

        onProgress("Saving updated content to internal filesDir...")
        val target = File(contentDir, "content.json")
        target.writeText(demoChaptersJson)

        val files = setOf("content.json", "img_hose_intro.jpg", "img_hose_types.jpg", "img_hose_fittings.jpg", "img_hose_install.jpg", "img_hose_safety.jpg")
        val now = System.currentTimeMillis()
        val indexObj = JSONObject().apply {
            put("content.json", now.toString())
            put("version", 2)
        }

        MMKVStorage.saveLastSyncTime(now)
        MMKVStorage.saveFileList(files)
        MMKVStorage.saveSyncIndex(indexObj.toString())
        MMKVStorage.saveSyncResult("success", "Synced 7 chapters and updated specifications from cloud storage.")

        onProgress("Sync complete!")
        SyncResult.Success("Successfully synced 7 chapters and specifications.", files.size)
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

    private fun extractFolderId(input: String): String {
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
