package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MMKVStorage
import com.example.data.SyncRepository
import com.example.data.SyncResult
import com.example.model.Chapter
import com.example.model.PendingUpload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class EbookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SyncRepository(application)
    private val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Live network listener: flush any MMKV-queued Drive uploads as soon as
    // connectivity returns, without waiting for an app restart or Sync Now.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            refreshNetworkState()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            refreshNetworkState()
        }
    }

    private val _allChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val allChapters: StateFlow<List<Chapter>> = _allChapters

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedTab = MutableStateFlow(0) // 0 = All Chapters, 1 = Bookmarked
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _selectedChapter = MutableStateFlow<Chapter?>(null)
    val selectedChapter: StateFlow<Chapter?> = _selectedChapter

    private val _isOffline = MutableStateFlow(!repository.isOnline())
    val isOffline: StateFlow<Boolean> = _isOffline

    private val _lastSyncTime = MutableStateFlow(MMKVStorage.getLastSyncTime())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncStatusMessage = MutableStateFlow(MMKVStorage.getLastSyncMessage())
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage

    private val _syncProgressText = MutableStateFlow("")
    val syncProgressText: StateFlow<String> = _syncProgressText

    private val _fontScale = MutableStateFlow(MMKVStorage.getFontScale())
    val fontScale: StateFlow<Float> = _fontScale

    // Drive configuration state
    private val _folderId = MutableStateFlow(MMKVStorage.getFolderId())
    val folderId: StateFlow<String> = _folderId

    private val _apiKey = MutableStateFlow(MMKVStorage.getDriveApiKey())
    val apiKey: StateFlow<String> = _apiKey

    private val _oauthToken = MutableStateFlow(MMKVStorage.getDriveOAuthToken())
    val oauthToken: StateFlow<String> = _oauthToken

    private val _uploadEndpoint = MutableStateFlow(MMKVStorage.getDriveUploadEndpoint())
    val uploadEndpoint: StateFlow<String> = _uploadEndpoint

    private val _customUrl = MutableStateFlow(MMKVStorage.getCustomSyncUrl())
    val customUrl: StateFlow<String> = _customUrl

    private val _syncedFiles = MutableStateFlow(MMKVStorage.getFileList())
    val syncedFiles: StateFlow<Set<String>> = _syncedFiles

    private val _pendingUploads = MutableStateFlow<List<PendingUpload>>(MMKVStorage.getPendingUploads())
    val pendingUploads: StateFlow<List<PendingUpload>> = _pendingUploads

    // Admin security & lock state
    private val _isAdminUnlocked = MutableStateFlow(false)
    val isAdminUnlocked: StateFlow<Boolean> = _isAdminUnlocked

    private val _isPinDialogVisible = MutableStateFlow(false)
    val isPinDialogVisible: StateFlow<Boolean> = _isPinDialogVisible

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val _lockoutRemainingSeconds = MutableStateFlow(0)
    val lockoutRemainingSeconds: StateFlow<Int> = _lockoutRemainingSeconds

    private var lockoutTimerJob: Job? = null
    private var lastAdminInteractionTime: Long = System.currentTimeMillis()
    private var idleCheckJob: Job? = null

    // Filtered list based on search and tab
    val displayedChapters: StateFlow<List<Chapter>> = combine(
        _allChapters,
        _searchQuery,
        _selectedTab
    ) { chapters, query, tab ->
        var list = chapters
        if (tab == 1) {
            list = list.filter { it.isBookmarked }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { ch ->
                ch.title.lowercase().contains(q) ||
                        ch.subtitle.lowercase().contains(q) ||
                        ch.category.lowercase().contains(q) ||
                        ch.summary.lowercase().contains(q) ||
                        ch.sections.any { sec ->
                            sec.heading.lowercase().contains(q) ||
                                    sec.text.lowercase().contains(q) ||
                                    sec.calloutTitle?.lowercase()?.contains(q) == true ||
                                    sec.calloutText?.lowercase()?.contains(q) == true
                        }
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        refreshNetworkState()
        loadLocalChapters()
        checkLockoutStatus()
        startIdleChecker()

        // Auto-sync on app open if online and sync config is set
        if (repository.isOnline()) {
            val hasConfig = MMKVStorage.getFolderId().isNotEmpty() || MMKVStorage.getCustomSyncUrl().isNotEmpty()
            if (hasConfig) {
                syncNow(isAutoSync = true)
            }
            // Auto process pending uploads if any
            processPendingUploads()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Best-effort cleanup; the callback may already be gone.
        }
    }

    private fun startIdleChecker() {
        idleCheckJob?.cancel()
        idleCheckJob = viewModelScope.launch {
            while (true) {
                delay(15000) // check every 15s
                if (_isAdminUnlocked.value) {
                    val idleTime = System.currentTimeMillis() - lastAdminInteractionTime
                    if (idleTime >= 5 * 60 * 1000L) { // 5 minutes idle
                        lockAdmin()
                    }
                }
            }
        }
    }

    fun onAdminInteraction() {
        lastAdminInteractionTime = System.currentTimeMillis()
    }

    fun checkLockoutStatus() {
        val remaining = MMKVStorage.getRemainingLockoutSeconds()
        _lockoutRemainingSeconds.value = remaining
        if (remaining > 0) {
            _pinError.value = "Locked. Try again in ${remaining}s."
            startLockoutCountdown()
        }
    }

    private fun startLockoutCountdown() {
        lockoutTimerJob?.cancel()
        lockoutTimerJob = viewModelScope.launch {
            while (MMKVStorage.isLockedOut()) {
                val sec = MMKVStorage.getRemainingLockoutSeconds()
                _lockoutRemainingSeconds.value = sec
                _pinError.value = "Locked. Try again in ${sec}s."
                delay(1000)
            }
            _lockoutRemainingSeconds.value = 0
            _pinError.value = null
        }
    }

    // Hidden Admin Trigger
    fun triggerPinDialog() {
        checkLockoutStatus()
        _isPinDialogVisible.value = true
    }

    fun dismissPinDialog() {
        _isPinDialogVisible.value = false
        _pinError.value = null
    }

    fun verifyPin(input: String): Boolean {
        if (MMKVStorage.isLockedOut()) {
            val sec = MMKVStorage.getRemainingLockoutSeconds()
            _pinError.value = "Locked. Try again in ${sec}s."
            return false
        }

        val correctPin = MMKVStorage.getAdminPin()
        if (input == correctPin) {
            MMKVStorage.resetFailedAttempts()
            _isAdminUnlocked.value = true
            _isPinDialogVisible.value = false
            _pinError.value = null
            lastAdminInteractionTime = System.currentTimeMillis()
            return true
        } else {
            val failed = MMKVStorage.incrementFailedAttempts()
            if (failed >= 3) {
                MMKVStorage.setLockoutUntil(System.currentTimeMillis() + 30_000L)
                startLockoutCountdown()
            } else {
                _pinError.value = "Incorrect PIN. ${3 - failed} attempt(s) remaining."
            }
            return false
        }
    }

    fun changePin(currentPin: String, newPin: String): Pair<Boolean, String> {
        val stored = MMKVStorage.getAdminPin()
        if (currentPin != stored) {
            return Pair(false, "Current PIN is incorrect.")
        }
        if (newPin.length < 4) {
            return Pair(false, "New PIN must be at least 4 digits.")
        }
        MMKVStorage.saveAdminPin(newPin)
        return Pair(true, "PIN updated successfully.")
    }

    fun lockAdmin() {
        _isAdminUnlocked.value = false
        _isPinDialogVisible.value = false
    }

    fun refreshNetworkState() {
        _isOffline.value = !repository.isOnline()
        if (!_isOffline.value) {
            processPendingUploads()
        }
    }

    fun loadLocalChapters() {
        val loaded = repository.loadChapters()
        _allChapters.value = loaded
        _lastSyncTime.value = MMKVStorage.getLastSyncTime()
        _syncedFiles.value = MMKVStorage.getFileList()
        _pendingUploads.value = MMKVStorage.getPendingUploads()
        _selectedChapter.value?.let { current ->
            _selectedChapter.value = loaded.find { it.id == current.id }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectChapter(chapter: Chapter?) {
        _selectedChapter.value = chapter
    }

    fun toggleBookmark(chapterId: String) {
        MMKVStorage.toggleBookmark(chapterId)
        loadLocalChapters()
    }

    fun changeFontScale(delta: Float) {
        val current = _fontScale.value
        val newScale = (current + delta).coerceIn(0.85f, 1.45f)
        _fontScale.value = newScale
        MMKVStorage.saveFontScale(newScale)
    }

    fun resetFontScale() {
        _fontScale.value = 1.0f
        MMKVStorage.saveFontScale(1.0f)
    }

    fun saveAdminDriveSettings(
        folderId: String,
        apiKey: String,
        oauthToken: String,
        uploadEndpoint: String,
        customUrl: String
    ) {
        MMKVStorage.saveFolderId(folderId)
        MMKVStorage.saveDriveApiKey(apiKey)
        MMKVStorage.saveDriveOAuthToken(oauthToken)
        MMKVStorage.saveDriveUploadEndpoint(uploadEndpoint)
        MMKVStorage.saveCustomSyncUrl(customUrl)

        _folderId.value = folderId
        _apiKey.value = apiKey
        _oauthToken.value = oauthToken
        _uploadEndpoint.value = uploadEndpoint
        _customUrl.value = customUrl
        _syncStatusMessage.value = "Admin Drive configuration saved."
    }

    fun saveDriveSettings(folderId: String, apiKey: String, customUrl: String) {
        saveAdminDriveSettings(
            folderId = folderId,
            apiKey = apiKey,
            oauthToken = _oauthToken.value,
            uploadEndpoint = _uploadEndpoint.value,
            customUrl = customUrl
        )
    }

    // Admin Add / Edit Article
    fun saveArticle(
        article: Chapter,
        imageBytes: ByteArray?,
        imageExtension: String = "jpg",
        onComplete: (String) -> Unit
    ) {
        onAdminInteraction()
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgressText.value = "Saving article & checking Drive sync..."

            val (_, statusMsg) = repository.saveOrUpdateArticle(article, imageBytes, imageExtension)

            loadLocalChapters()
            _isSyncing.value = false
            _syncProgressText.value = ""
            onComplete(statusMsg)
        }
    }

    // Admin Delete Article
    fun deleteArticle(articleId: String, onComplete: (String) -> Unit) {
        onAdminInteraction()
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgressText.value = "Deleting article..."

            val (success, msg) = repository.deleteArticle(articleId)
            loadLocalChapters()
            _isSyncing.value = false
            _syncProgressText.value = ""
            onComplete(msg)
        }
    }

    // Process pending offline uploads
    fun processPendingUploads(onResult: ((String) -> Unit)? = null) {
        if (!repository.isOnline()) {
            onResult?.invoke("Device is offline. Uploads will resume when connected.")
            return
        }

        viewModelScope.launch {
            val uploadedCount = repository.processPendingUploads()
            _pendingUploads.value = MMKVStorage.getPendingUploads()
            loadLocalChapters()
            if (uploadedCount > 0) {
                onResult?.invoke("Successfully uploaded $uploadedCount pending item(s) to Google Drive.")
            } else {
                onResult?.invoke("No pending uploads or missing Drive write authorization.")
            }
        }
    }

    fun syncNow(isAutoSync: Boolean = false) {
        refreshNetworkState()
        if (_isOffline.value) {
            _syncStatusMessage.value = "Device is offline. Reading saved data."
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgressText.value = if (isAutoSync) "Auto-checking Google Drive updates..." else "Initiating Drive sync..."

            val result = repository.syncFromDrive { progressMsg ->
                _syncProgressText.value = progressMsg
            }

            when (result) {
                is SyncResult.Success -> {
                    _syncStatusMessage.value = result.message
                    _lastSyncTime.value = MMKVStorage.getLastSyncTime()
                    _syncedFiles.value = MMKVStorage.getFileList()
                    loadLocalChapters()
                }
                is SyncResult.Failure -> {
                    _syncStatusMessage.value = result.error
                }
                is SyncResult.Offline -> {
                    _syncStatusMessage.value = "Device is offline. Reading saved data."
                }
            }

            _isSyncing.value = false
            _syncProgressText.value = ""
        }
    }

    fun loadDemoCloudUpdate() {
        refreshNetworkState()
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgressText.value = "Loading cloud update demo..."

            val result = repository.loadDemoCloudUpdate { progressMsg ->
                _syncProgressText.value = progressMsg
            }

            if (result is SyncResult.Success) {
                _syncStatusMessage.value = result.message
                _lastSyncTime.value = MMKVStorage.getLastSyncTime()
                _syncedFiles.value = MMKVStorage.getFileList()
                loadLocalChapters()
            }

            _isSyncing.value = false
            _syncProgressText.value = ""
        }
    }

    fun clearSavedData() {
        repository.clearSavedData()
        _lastSyncTime.value = 0L
        _syncedFiles.value = emptySet()
        _syncStatusMessage.value = "Cloud data cleared. Restored built-in chapters."
        loadLocalChapters()
    }

    fun getLocalFile(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val file = File(repository.getLocalContentDir(), fileName)
        return if (file.exists()) file else null
    }
}
