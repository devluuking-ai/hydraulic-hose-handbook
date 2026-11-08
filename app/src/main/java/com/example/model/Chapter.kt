package com.example.model

enum class CalloutType {
    NONE,
    INFO,
    WARNING,
    TIP
}

data class ChapterSection(
    val heading: String = "",
    val text: String = "",
    val image: String? = null,
    val caption: String? = null,
    val calloutType: CalloutType = CalloutType.NONE,
    val calloutTitle: String? = null,
    val calloutText: String? = null
)

data class Chapter(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val category: String = "General",
    val order: Int = 1,
    val readingTimeMinutes: Int = 5,
    val thumbnail: String? = null,
    val secondImage: String? = null,
    val summary: String = "",
    val sections: List<ChapterSection> = emptyList(),
    val isBookmarked: Boolean = false,
    val source: String = "builtin", // "builtin", "drive", "admin"
    val uploadStatus: String = "SAVED_TO_DRIVE" // "SAVED_TO_DRIVE", "SAVED_OFFLINE", "UPLOADING", "LOCAL_BUILTIN"
)

data class PendingUpload(
    val id: String,
    val action: String, // "UPSERT" or "DELETE"
    val articleId: String,
    val title: String,
    val localImageName: String? = null,
    val localSecondImageName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
