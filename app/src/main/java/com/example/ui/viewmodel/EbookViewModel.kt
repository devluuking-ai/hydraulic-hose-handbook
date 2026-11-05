package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MMKVStorage
import com.example.data.SyncRepository
import com.example.data.SyncResult
import com.example.model.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class EbookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SyncRepository(application)

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

    private val _folderId = MutableStateFlow(MMKVStorage.getFolderId())
    val folderId: StateFlow<String> = _folderId

    private val _apiKey = MutableStateFlow(MMKVStorage.getDriveApiKey())
    val apiKey: StateFlow<String> = _apiKey

    private val _customUrl = MutableStateFlow(MMKVStorage.getCustomSyncUrl())
    val customUrl: StateFlow<String> = _customUrl

    private val _syncedFiles = MutableStateFlow(MMKVStorage.getFileList())
    val syncedFiles: StateFlow<Set<String>> = _syncedFiles

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
        refreshNetworkState()
        loadLocalChapters()

        // Auto-sync on app open if online and sync config is set
        if (repository.isOnline()) {
            val hasConfig = MMKVStorage.getFolderId().isNotEmpty() || MMKVStorage.getCustomSyncUrl().isNotEmpty()
            if (hasConfig) {
                syncNow(isAutoSync = true)
            }
        }
    }

    fun refreshNetworkState() {
        _isOffline.value = !repository.isOnline()
    }

    fun loadLocalChapters() {
        val loaded = repository.loadChapters()
        _allChapters.value = loaded
        _lastSyncTime.value = MMKVStorage.getLastSyncTime()
        _syncedFiles.value = MMKVStorage.getFileList()
        // If current selected chapter is active, refresh its instance
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

    fun saveDriveSettings(folderId: String, apiKey: String, customUrl: String) {
        MMKVStorage.saveFolderId(folderId)
        MMKVStorage.saveDriveApiKey(apiKey)
        MMKVStorage.saveCustomSyncUrl(customUrl)
        _folderId.value = folderId
        _apiKey.value = apiKey
        _customUrl.value = customUrl
        _syncStatusMessage.value = "Settings saved successfully."
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
