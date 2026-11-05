package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CalloutType
import com.example.model.Chapter
import com.example.model.ChapterSection
import com.example.ui.components.ChapterThumbnail
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.ui.theme.WarningRedBg
import com.example.ui.viewmodel.EbookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    chapter: Chapter,
    viewModel: EbookViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val allChapters by viewModel.allChapters.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()

    // Determine prev / next chapter for continuous reading
    val currentIndex = allChapters.indexOfFirst { it.id == chapter.id }
    val prevChapter = if (currentIndex > 0) allChapters[currentIndex - 1] else null
    val nextChapter = if (currentIndex >= 0 && currentIndex < allChapters.size - 1) allChapters[currentIndex + 1] else null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chapter ${chapter.order}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to chapters",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Font size decrease
                    IconButton(
                        onClick = { viewModel.changeFontScale(-0.1f) },
                        modifier = Modifier.testTag("font_decrease_button")
                    ) {
                        Text(
                            text = "A-",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Font size increase
                    IconButton(
                        onClick = { viewModel.changeFontScale(0.1f) },
                        modifier = Modifier.testTag("font_increase_button")
                    ) {
                        Text(
                            text = "A+",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    // Bookmark toggle
                    IconButton(
                        onClick = { viewModel.toggleBookmark(chapter.id) },
                        modifier = Modifier.testTag("detail_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (chapter.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (chapter.isBookmarked) "Remove bookmark" else "Bookmark",
                            tint = if (chapter.isBookmarked) Color(0xFFFFD54F) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("chapter_detail_content"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header: Chapter Badge & Reading time
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Chapter ${chapter.order} of ${allChapters.size}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "${chapter.readingTimeMinutes} min read",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Big Chapter Title
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = NavyDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = (26 * fontScale).sp,
                        lineHeight = (34 * fontScale).sp
                    )
                )

                if (chapter.subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = chapter.subtitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (17 * fontScale).sp,
                            lineHeight = (24 * fontScale).sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Summary Card if present
                if (chapter.summary.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Overview",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = chapter.summary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary,
                                    fontSize = (15 * fontScale).sp,
                                    lineHeight = (23 * fontScale).sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Sections
            items(chapter.sections) { section ->
                SectionItem(
                    section = section,
                    fontScale = fontScale
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom Navigation: Previous & Next Chapter Buttons
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (prevChapter != null) {
                        OutlinedButton(
                            onClick = { viewModel.selectChapter(prevChapter) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                                .testTag("prev_chapter_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chapter ${prevChapter.order}",
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (nextChapter != null) {
                        Button(
                            onClick = { viewModel.selectChapter(nextChapter) },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp)
                                .testTag("next_chapter_button")
                        ) {
                            Text(
                                text = "Chapter ${nextChapter.order}",
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SectionItem(
    section: ChapterSection,
    fontScale: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section heading
        if (section.heading.isNotEmpty()) {
            Text(
                text = section.heading,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (20 * fontScale).sp,
                    lineHeight = (28 * fontScale).sp
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Section Diagram / Image
        if (!section.image.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column {
                    ChapterThumbnail(
                        imageName = section.image,
                        contentDescription = section.caption ?: section.heading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )

                    if (!section.caption.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = section.caption,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = (12 * fontScale).sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Section Body Text (Large, readable, generous line height)
        if (section.text.isNotEmpty()) {
            Text(
                text = section.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = TextPrimary,
                    fontSize = (17 * fontScale).sp,
                    lineHeight = (28 * fontScale).sp,
                    letterSpacing = 0.3.sp
                )
            )
        }

        // Callout card if present
        if (section.calloutType != CalloutType.NONE && !section.calloutText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            CalloutCard(
                type = section.calloutType,
                title = section.calloutTitle,
                text = section.calloutText,
                fontScale = fontScale
            )
        }
    }
}

@Composable
fun CalloutCard(
    type: CalloutType,
    title: String?,
    text: String,
    fontScale: Float,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, iconColor, icon) = when (type) {
        CalloutType.WARNING -> Quadruple(
            WarningRedBg,
            Color(0xFFFCA5A5),
            WarningRed,
            Icons.Default.Warning
        )
        CalloutType.TIP -> Quadruple(
            Color(0xFFECFDF5),
            Color(0xFF6EE7B7),
            Color(0xFF047857),
            Icons.Default.Lightbulb
        )
        CalloutType.INFO, CalloutType.NONE -> Quadruple(
            Color(0xFFEFF6FF),
            Color(0xFF93C5FD),
            AccentBlue,
            Icons.Default.Info
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = iconColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = (15 * fontScale).sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF1E293B),
                        fontSize = (14 * fontScale).sp,
                        lineHeight = (22 * fontScale).sp
                    )
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
