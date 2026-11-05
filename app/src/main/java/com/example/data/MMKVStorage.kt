package com.example.data

import com.tencent.mmkv.MMKV

object MMKVStorage {
    private val mmkv: MMKV get() = MMKV.defaultMMKV()

    private const val KEY_FOLDER_ID = "drive_folder_id"
    private const val KEY_DRIVE_API_KEY = "drive_api_key"
    private const val KEY_CUSTOM_URL = "custom_sync_url"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_SYNC_INDEX = "sync_index"
    private const val KEY_FILE_LIST = "file_list"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
    private const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
    private const val KEY_FONT_SCALE = "font_scale"

    fun getFolderId(): String = mmkv.decodeString(KEY_FOLDER_ID, "") ?: ""
    fun saveFolderId(id: String) {
        mmkv.encode(KEY_FOLDER_ID, id.trim())
    }

    fun getDriveApiKey(): String = mmkv.decodeString(KEY_DRIVE_API_KEY, "") ?: ""
    fun saveDriveApiKey(key: String) {
        mmkv.encode(KEY_DRIVE_API_KEY, key.trim())
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

    fun clearAllSyncData() {
        mmkv.removeValueForKey(KEY_LAST_SYNC_TIME)
        mmkv.removeValueForKey(KEY_SYNC_INDEX)
        mmkv.removeValueForKey(KEY_FILE_LIST)
        mmkv.removeValueForKey(KEY_LAST_SYNC_STATUS)
        mmkv.removeValueForKey(KEY_LAST_SYNC_MESSAGE)
    }
}
