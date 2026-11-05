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
    val order: Int = 1,
    val readingTimeMinutes: Int = 5,
    val thumbnail: String? = null,
    val summary: String = "",
    val sections: List<ChapterSection> = emptyList(),
    val isBookmarked: Boolean = false,
    val source: String = "builtin" // "builtin" or "drive"
)
