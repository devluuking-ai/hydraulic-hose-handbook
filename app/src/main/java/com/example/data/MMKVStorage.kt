package com.example.data

import com.example.model.PendingUpload
import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject

object MMKVStorage {
    private val mmkv: MMKV get() = MMKV.defaultMMKV()

    private const val KEY_FOLDER_ID = "drive_folder_id"
    private const val KEY_DRIVE_API_KEY = "drive_api_key"
    private const val KEY_DRIVE_OAUTH_TOKEN = "drive_oauth_token"
    private const val KEY_DRIVE_UPLOAD_ENDPOINT = "drive_upload_endpoint"
    private const val KEY_CUSTOM_URL = "custom_sync_url"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_SYNC_INDEX = "sync_index"
    private const val KEY_FILE_LIST = "file_list"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
    private const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val DEFAULT_FOLDER_ID = "1R-ki1wWO2wiztRVHdiL53vlC8ajind6a"
    private const val DEFAULT_UPLOAD_ENDPOINT = "https://script.google.com/macros/s/AKfycbygFnoSNYz66jBzubYJGdBdnpWj9c6ydvp2giVtDiuScCv-ryV6JvRt5AG1CYnHKOC4/exec"

    // Admin security keys
    private const val KEY_ADMIN_PIN = "admin_pin"
    private const val KEY_FAILED_ATTEMPTS = "admin_failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "admin_lockout_until"
    private const val KEY_PENDING_UPLOADS = "pending_uploads_queue"

    // PIN Management
    fun getAdminPin(): String = mmkv.decodeString(KEY_ADMIN_PIN, "1234") ?: "1234"
    fun saveAdminPin(newPin: String) {
        mmkv.encode(KEY_ADMIN_PIN, newPin.trim())
    }

    fun getFailedAttempts(): Int = mmkv.decodeInt(KEY_FAILED_ATTEMPTS, 0)
    fun incrementFailedAttempts(): Int {
        val current = getFailedAttempts() + 1
        mmkv.encode(KEY_FAILED_ATTEMPTS, current)
        return current
    }

    fun resetFailedAttempts() {
        mmkv.encode(KEY_FAILED_ATTEMPTS, 0)
        mmkv.encode(KEY_LOCKOUT_UNTIL, 0L)
    }

    fun getLockoutUntil(): Long = mmkv.decodeLong(KEY_LOCKOUT_UNTIL, 0L)
    fun setLockoutUntil(timestamp: Long) {
        mmkv.encode(KEY_LOCKOUT_UNTIL, timestamp)
    }

    fun isLockedOut(): Boolean {
        val until = getLockoutUntil()
        return until > System.currentTimeMillis()
    }

    fun getRemainingLockoutSeconds(): Int {
        val remaining = (getLockoutUntil() - System.currentTimeMillis()) / 1000
        return if (remaining > 0) remaining.toInt() else 0
    }

    // Google Drive settings
    fun getFolderId(): String = mmkv.decodeString(KEY_FOLDER_ID, DEFAULT_FOLDER_ID)?.ifBlank { DEFAULT_FOLDER_ID } ?: DEFAULT_FOLDER_ID
    fun saveFolderId(id: String) {
        mmkv.encode(KEY_FOLDER_ID, id.trim())
    }

    fun getDriveApiKey(): String = mmkv.decodeString(KEY_DRIVE_API_KEY, "") ?: ""
    fun saveDriveApiKey(key: String) {
        mmkv.encode(KEY_DRIVE_API_KEY, key.trim())
    }

    fun getDriveOAuthToken(): String = mmkv.decodeString(KEY_DRIVE_OAUTH_TOKEN, "") ?: ""
    fun saveDriveOAuthToken(token: String) {
        mmkv.encode(KEY_DRIVE_OAUTH_TOKEN, token.trim())
    }

    fun getDriveUploadEndpoint(): String = mmkv.decodeString(KEY_DRIVE_UPLOAD_ENDPOINT, DEFAULT_UPLOAD_ENDPOINT)?.ifBlank { DEFAULT_UPLOAD_ENDPOINT } ?: DEFAULT_UPLOAD_ENDPOINT
    fun saveDriveUploadEndpoint(endpoint: String) {
        mmkv.encode(KEY_DRIVE_UPLOAD_ENDPOINT, endpoint.trim())
    }

    fun getCustomSyncUrl(): String = mmkv.decodeString(KEY_CUSTOM_URL, "") ?: ""
    fun saveCustomSyncUrl(url: String) {
        mmkv.encode(KEY_CUSTOM_URL, url.trim())
    }

    fun getLastSyncTime(): Long = mmkv.decodeLong(KEY_LAST_SYNC_TIME, 0L)
    fun saveLastSyncTime(timestamp: Long) {
        mmkv.encode(KEY_LAST_SYNC_TIME, timestamp)
    }

    fun getSyncIndex(): String = mmkv.decodeString(KEY_SYNC_INDEX, "{}") ?: "{}"
    fun saveSyncIndex(index: String) {
        mmkv.encode(KEY_SYNC_INDEX, index)
    }

    fun getFileList(): Set<String> {
        return mmkv.decodeStringSet(KEY_FILE_LIST, emptySet()) ?: emptySet()
    }

    fun saveFileList(files: Set<String>) {
        mmkv.encode(KEY_FILE_LIST, files)
    }

    fun addToFileList(fileName: String) {
        val current = getFileList().toMutableSet()
        current.add(fileName)
        saveFileList(current)
    }

    fun removeFromFileList(fileName: String) {
        val current = getFileList().toMutableSet()
        current.remove(fileName)
        saveFileList(current)
    }

    fun getBookmarks(): Set<String> {
        return mmkv.decodeStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
    }

    fun toggleBookmark(chapterId: String): Boolean {
        val current = getBookmarks().toMutableSet()
        val isNowBookmarked = if (current.contains(chapterId)) {
            current.remove(chapterId)
            false
        } else {
            current.add(chapterId)
            true
        }
        mmkv.encode(KEY_BOOKMARKS, current)
        return isNowBookmarked
    }

    fun isBookmarked(chapterId: String): Boolean {
        return getBookmarks().contains(chapterId)
    }

    fun getLastSyncStatus(): String = mmkv.decodeString(KEY_LAST_SYNC_STATUS, "idle") ?: "idle"
    fun getLastSyncMessage(): String = mmkv.decodeString(KEY_LAST_SYNC_MESSAGE, "") ?: ""

    fun saveSyncResult(status: String, message: String) {
        mmkv.encode(KEY_LAST_SYNC_STATUS, status)
        mmkv.encode(KEY_LAST_SYNC_MESSAGE, message)
    }

    fun getFontScale(): Float = mmkv.decodeFloat(KEY_FONT_SCALE, 1.0f)
    fun saveFontScale(scale: Float) {
        mmkv.encode(KEY_FONT_SCALE, scale)
    }

    // Pending uploads queue
    fun getPendingUploads(): List<PendingUpload> {
        val jsonStr = mmkv.decodeString(KEY_PENDING_UPLOADS, "[]") ?: "[]"
        val list = mutableListOf<PendingUpload>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PendingUpload(
                        id = obj.getString("id"),
                        action = obj.getString("action"),
                        articleId = obj.getString("articleId"),
                        title = obj.getString("title"),
                        localImageName = obj.optString("localImageName").takeIf { it.isNotBlank() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun savePendingUploads(list: List<PendingUpload>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("action", item.action)
            obj.put("articleId", item.articleId)
            obj.put("title", item.title)
            obj.put("localImageName", item.localImageName)
            obj.put("timestamp", item.timestamp)
            array.put(obj)
        }
        mmkv.encode(KEY_PENDING_UPLOADS, array.toString())
    }

    fun addPendingUpload(item: PendingUpload) {
        val current = getPendingUploads().toMutableList()
        current.removeAll { it.articleId == item.articleId && it.action == item.action }
        current.add(item)
        savePendingUploads(current)
    }

    fun removePendingUpload(id: String) {
        val current = getPendingUploads().filter { it.id != id }
        savePendingUploads(current)
    }

    fun clearAllSyncData() {
        mmkv.removeValueForKey(KEY_LAST_SYNC_TIME)
        mmkv.removeValueForKey(KEY_SYNC_INDEX)
        mmkv.removeValueForKey(KEY_FILE_LIST)
        mmkv.removeValueForKey(KEY_LAST_SYNC_STATUS)
        mmkv.removeValueForKey(KEY_LAST_SYNC_MESSAGE)
        mmkv.removeValueForKey(KEY_PENDING_UPLOADS)
    }
}
