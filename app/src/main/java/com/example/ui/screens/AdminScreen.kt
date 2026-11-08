package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Chapter
import com.example.model.ChapterSection
import com.example.ui.components.ChapterThumbnail
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OfflineAmber
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.ui.viewmodel.EbookViewModel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: EbookViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.onAdminInteraction()
        onBackClick()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allChapters by viewModel.allChapters.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pendingUploads by viewModel.pendingUploads.collectAsState()

    var selectedAdminTab by rememberSaveable { mutableIntStateOf(0) }
    // 0 = Articles List, 1 = Add/Edit Form, 2 = Drive Settings, 3 = Security/PIN

    // Form editing state
    var editingChapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var formTitle by rememberSaveable { mutableStateOf("") }
    var formCategory by rememberSaveable { mutableStateOf("Hose Types") }
    var formSubtitle by rememberSaveable { mutableStateOf("") }
    var formReadingTime by rememberSaveable { mutableStateOf("5") }
    var formTextContent by rememberSaveable { mutableStateOf("") }
    var selectedImageUri1 by rememberSaveable { mutableStateOf<Uri?>(null) }
    var existingThumbnail1 by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImageUri2 by rememberSaveable { mutableStateOf<Uri?>(null) }
    var existingThumbnail2 by rememberSaveable { mutableStateOf<String?>(null) }
    var activePickerSlot by rememberSaveable { mutableIntStateOf(1) }

    // Deletion dialog state
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }

    // Photo picker launcher (Zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setPickingPhoto(false)
        viewModel.onAdminInteraction()
        if (uri != null) {
            if (activePickerSlot == 1) {
                selectedImageUri1 = uri
            } else {
                selectedImageUri2 = uri
            }
        }
    }

    fun startNewArticle() {
        editingChapterId = null
        formTitle = ""
        formCategory = "Hose Types"
        formSubtitle = ""
        formReadingTime = "5"
        formTextContent = ""
        selectedImageUri1 = null
        existingThumbnail1 = null
        selectedImageUri2 = null
        existingThumbnail2 = null
        selectedAdminTab = 1
    }

    fun startEditArticle(chapter: Chapter) {
        editingChapterId = chapter.id
        formTitle = chapter.title
        formCategory = chapter.category.ifBlank { "Hose Types" }
        formSubtitle = chapter.subtitle
        formReadingTime = chapter.readingTimeMinutes.toString()
        formTextContent = chapter.sections.joinToString("\n\n") { sec ->
            if (sec.heading.isNotEmpty() && sec.heading != chapter.title) {
                "### ${sec.heading}\n${sec.text}"
            } else {
                sec.text
            }
        }.ifBlank { chapter.summary }
        selectedImageUri1 = null
        existingThumbnail1 = chapter.thumbnail
        selectedImageUri2 = null
        existingThumbnail2 = chapter.secondImage
        selectedAdminTab = 1
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundLight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Portal",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onAdminInteraction()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Lock session immediately
                    IconButton(
                        onClick = {
                            viewModel.lockAdmin()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("admin_lock_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Admin",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs Bar
            ScrollableTabRow(
                selectedTabIndex = selectedAdminTab,
                containerColor = SurfaceWhite,
                contentColor = NavyPrimary,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedAdminTab == 0,
                    onClick = {
                        viewModel.onAdminInteraction()
                        selectedAdminTab = 0
                    },
                    text = { Text("Articles (${allChapters.size})", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("admin_tab_articles")
                )
                Tab(
                    selected = selectedAdminTab == 1,
                    onClick = {
                        viewModel.onAdminInteraction()
                        selectedAdminTab = 1
                    },
                    text = {
                        Text(
                            if (editingChapterId != null) "Edit Article" else "Add Article",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.testTag("admin_tab_form")
                )
                Tab(
                    selected = selectedAdminTab == 2,
                    onClick = {
                        viewModel.onAdminInteraction()
                        selectedAdminTab = 2
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Drive Sync", fontWeight = FontWeight.Bold)
                            if (pendingUploads.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = OfflineAmber,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${pendingUploads.size}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("admin_tab_drive")
                )
                Tab(
                    selected = selectedAdminTab == 3,
                    onClick = {
                        viewModel.onAdminInteraction()
                        selectedAdminTab = 3
                    },
                    text = { Text("PIN Security", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("admin_tab_security")
                )
            }

            // Tab Content
            when (selectedAdminTab) {
                0 -> {
                    // Articles List
                    AdminArticlesList(
                        chapters = allChapters,
                        onAddNewClick = { startNewArticle() },
                        onEditClick = { ch -> startEditArticle(ch) },
                        onDeleteClick = { ch -> chapterToDelete = ch }
                    )
                }
                1 -> {
                    // Add / Edit Form
                    AdminArticleForm(
                        isEdit = editingChapterId != null,
                        title = formTitle,
                        onTitleChange = { formTitle = it; viewModel.onAdminInteraction() },
                        category = formCategory,
                        onCategoryChange = { formCategory = it; viewModel.onAdminInteraction() },
                        subtitle = formSubtitle,
                        onSubtitleChange = { formSubtitle = it; viewModel.onAdminInteraction() },
                        readingTime = formReadingTime,
                        onReadingTimeChange = { formReadingTime = it; viewModel.onAdminInteraction() },
                        textContent = formTextContent,
                        onTextContentChange = { formTextContent = it; viewModel.onAdminInteraction() },
                        selectedImageUri1 = selectedImageUri1,
                        existingThumbnail1 = existingThumbnail1,
                        onPickImage1Click = {
                            activePickerSlot = 1
                            viewModel.setPickingPhoto(true)
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveImage1Click = {
                            viewModel.onAdminInteraction()
                            selectedImageUri1 = null
                            existingThumbnail1 = null
                        },
                        selectedImageUri2 = selectedImageUri2,
                        existingThumbnail2 = existingThumbnail2,
                        onPickImage2Click = {
                            activePickerSlot = 2
                            viewModel.setPickingPhoto(true)
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveImage2Click = {
                            viewModel.onAdminInteraction()
                            selectedImageUri2 = null
                            existingThumbnail2 = null
                        },
                        isSaving = isSyncing,
                        onSaveClick = {
                            viewModel.onAdminInteraction()
                            val trimmedTitle = formTitle.trim()
                            if (trimmedTitle.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter a title.") }
                                return@AdminArticleForm
                            }

                            // Read image 1 bytes if picked
                            var imageBytes1: ByteArray? = null
                            var imageExt1 = "jpg"
                            if (selectedImageUri1 != null) {
                                try {
                                    val stream: InputStream? = context.contentResolver.openInputStream(selectedImageUri1!!)
                                    imageBytes1 = stream?.readBytes()
                                    stream?.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // Read image 2 bytes if picked
                            var imageBytes2: ByteArray? = null
                            var imageExt2 = "jpg"
                            if (selectedImageUri2 != null) {
                                try {
                                    val stream: InputStream? = context.contentResolver.openInputStream(selectedImageUri2!!)
                                    imageBytes2 = stream?.readBytes()
                                    stream?.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            val readTime = formReadingTime.toIntOrNull() ?: 5
                            val targetId = editingChapterId ?: "custom_${UUID.randomUUID().toString().take(8)}"

                            val sections = listOf(
                                ChapterSection(
                                    heading = trimmedTitle,
                                    text = formTextContent.trim(),
                                    image = existingThumbnail1
                                )
                            )

                            val articleToSave = Chapter(
                                id = targetId,
                                title = trimmedTitle,
                                subtitle = formSubtitle.trim(),
                                category = formCategory.trim(),
                                readingTimeMinutes = readTime,
                                summary = formSubtitle.ifBlank { formTextContent.take(120) },
                                thumbnail = existingThumbnail1,
                                secondImage = existingThumbnail2,
                                sections = sections,
                                source = "admin"
                            )

                            viewModel.saveArticle(
                                article = articleToSave,
                                imageBytes1 = imageBytes1,
                                imageExtension1 = imageExt1,
                                imageBytes2 = imageBytes2,
                                imageExtension2 = imageExt2
                            ) { resultMsg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(resultMsg)
                                }
                                selectedAdminTab = 0
                            }
                        },
                        onCancelClick = {
                            viewModel.onAdminInteraction()
                            selectedAdminTab = 0
                        }
                    )
                }
                2 -> {
                    // Drive Sync Settings & Queue
                    AdminDriveSettingsView(
                        viewModel = viewModel,
                        pendingUploads = pendingUploads,
                        isOffline = isOffline,
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
                3 -> {
                    // Security & Change PIN
                    AdminSecuritySettingsView(
                        viewModel = viewModel,
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        onLockout = {
                            onBackClick()
                        }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (chapterToDelete != null) {
        val target = chapterToDelete!!
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("Delete Article?") },
            text = {
                Text("Are you sure you want to delete '${target.title}'? This removes it from this device only. The copy in Google Drive is kept, and you can bring it back with Restore from Drive.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteArticle(target.id) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                        chapterToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                    modifier = Modifier.testTag("confirm_delete_article_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { chapterToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_article_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminArticlesList(
    chapters: List<Chapter>,
    onAddNewClick: () -> Unit,
    onEditClick: (Chapter) -> Unit,
    onDeleteClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_articles_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Handbook Articles",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = NavyDark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Edit, delete, or create new chapters directly from APK.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Button(
                    onClick = onAddNewClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("admin_add_article_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Article")
                }
            }
        }

        items(chapters, key = { it.id }) { chapter ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_article_card_${chapter.id}"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        ChapterThumbnail(
                            imageName = chapter.thumbnail,
                            contentDescription = chapter.title,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${chapter.category} • #${chapter.order}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = AccentBlue,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (!chapter.secondImage.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "2 imgs",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = NavyPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Status Badge
                                val (statusText, statusBg, statusColor) = when (chapter.uploadStatus) {
                                    "SAVED_TO_DRIVE" -> Triple("Saved to Drive", Color(0xFFD1FAE5), OnlineGreen)
                                    "SAVED_OFFLINE" -> Triple("Saved offline", Color(0xFFFEF3C7), OfflineAmber)
                                    "UPLOADING" -> Triple("Uploading...", Color(0xFFE0F2FE), AccentBlue)
                                    else -> Triple("Built-in", Color(0xFFF1F5F9), TextSecondary)
                                }

                                Surface(
                                    color = statusBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = statusColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (chapter.subtitle.isNotEmpty()) {
                                Text(
                                    text = chapter.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Action buttons: Edit & Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onEditClick(chapter) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("admin_edit_btn_${chapter.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = NavyPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, color = NavyPrimary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { onDeleteClick(chapter) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("admin_delete_btn_${chapter.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = WarningRed
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp, color = WarningRed)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AdminArticleForm(
    isEdit: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    subtitle: String,
    onSubtitleChange: (String) -> Unit,
    readingTime: String,
    onReadingTimeChange: (String) -> Unit,
    textContent: String,
    onTextContentChange: (String) -> Unit,
    selectedImageUri1: Uri?,
    existingThumbnail1: String?,
    onPickImage1Click: () -> Unit,
    onRemoveImage1Click: () -> Unit,
    selectedImageUri2: Uri?,
    existingThumbnail2: String?,
    onPickImage2Click: () -> Unit,
    onRemoveImage2Click: () -> Unit,
    isSaving: Boolean,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("Hose Types", "Fittings", "Installation", "Safety", "Field Diagnostics", "Maintenance", "Standards")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_article_form"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (isEdit) "Edit Article" else "Add New Article",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = NavyDark,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Changes save locally immediately and sync with Google Drive when online.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
        }

        // Title
        item {
            Text(text = "Article Title *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                placeholder = { Text("e.g. Hydraulic Crimp Die Calibration Standards") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_title_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Category with quick selector chips
        item {
            Text(text = "Category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = category,
                onValueChange = onCategoryChange,
                placeholder = { Text("e.g. Hose Types, Safety") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_category_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = category.equals(cat, ignoreCase = true),
                        onClick = { onCategoryChange(cat) },
                        label = { Text(cat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Subtitle / Summary
        item {
            Text(text = "Subtitle / Summary", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = subtitle,
                onValueChange = onSubtitleChange,
                placeholder = { Text("Brief 1-2 sentence overview for the card snippet") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_subtitle_input"),
                singleLine = false,
                maxLines = 2,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Reading Time
        item {
            Text(text = "Reading Time (minutes)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = readingTime,
                onValueChange = onReadingTimeChange,
                placeholder = { Text("5") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_reading_time_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Image Section: Slot 1 (Primary / Thumbnail)
        item {
            Text(text = "Image 1 (Primary Thumbnail & Diagram)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        selectedImageUri1 != null -> {
                            AsyncImage(
                                model = selectedImageUri1,
                                contentDescription = "Selected Gallery Image 1",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        !existingThumbnail1.isNullOrBlank() -> {
                            ChapterThumbnail(
                                imageName = existingThumbnail1,
                                contentDescription = "Current Image 1",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("No primary image selected", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onPickImage1Click,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("form_pick_image_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedImageUri1 != null || existingThumbnail1 != null) "Change Image 1" else "Add Image 1")
                        }

                        if (selectedImageUri1 != null || existingThumbnail1 != null) {
                            TextButton(
                                onClick = onRemoveImage1Click,
                                colors = ButtonDefaults.textButtonColors(contentColor = WarningRed),
                                modifier = Modifier.testTag("form_remove_image_btn")
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }

        // Image Section: Slot 2 (Secondary Diagram / Cutaway)
        item {
            Text(text = "Image 2 (Secondary Reference Diagram)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        selectedImageUri2 != null -> {
                            AsyncImage(
                                model = selectedImageUri2,
                                contentDescription = "Selected Gallery Image 2",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        !existingThumbnail2.isNullOrBlank() -> {
                            ChapterThumbnail(
                                imageName = existingThumbnail2,
                                contentDescription = "Current Image 2",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("No secondary image (optional)", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onPickImage2Click,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("form_pick_image_2_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedImageUri2 != null || existingThumbnail2 != null) "Change Image 2" else "Add Image 2")
                        }

                        if (selectedImageUri2 != null || existingThumbnail2 != null) {
                            TextButton(
                                onClick = onRemoveImage2Click,
                                colors = ButtonDefaults.textButtonColors(contentColor = WarningRed),
                                modifier = Modifier.testTag("form_remove_image_2_btn")
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }

        // Text Content
        item {
            Text(text = "Article Body Content *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = textContent,
                onValueChange = onTextContentChange,
                placeholder = { Text("Write detailed technical documentation, procedures, safety instructions...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .testTag("form_content_input"),
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Action Buttons
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("form_cancel_btn")
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onSaveClick,
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("form_save_btn")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Article", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun AdminDriveSettingsView(
    viewModel: EbookViewModel,
    pendingUploads: List<com.example.model.PendingUpload>,
    isOffline: Boolean,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val folderIdState by viewModel.folderId.collectAsState()
    val apiKeyState by viewModel.apiKey.collectAsState()
    val oauthTokenState by viewModel.oauthToken.collectAsState()
    val uploadEndpointState by viewModel.uploadEndpoint.collectAsState()
    val customUrlState by viewModel.customUrl.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var inputFolderId by remember(folderIdState) { mutableStateOf(folderIdState) }
    var inputApiKey by remember(apiKeyState) { mutableStateOf(apiKeyState) }
    var inputOAuthToken by remember(oauthTokenState) { mutableStateOf(oauthTokenState) }
    var inputUploadEndpoint by remember(uploadEndpointState) { mutableStateOf(uploadEndpointState) }
    var inputCustomUrl by remember(customUrlState) { mutableStateOf(customUrlState) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_drive_settings_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pending Queue Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = if (isOffline) OfflineAmber else OnlineGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pending Offline Uploads (${pendingUploads.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.onAdminInteraction()
                                viewModel.processPendingUploads { msg -> onShowSnackbar(msg) }
                            },
                            enabled = !isOffline && pendingUploads.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            modifier = Modifier.testTag("upload_pending_now_btn")
                        ) {
                            Text("Upload Now", fontSize = 12.sp)
                        }
                    }

                    if (pendingUploads.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All local articles are fully in sync with Google Drive.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnlineGreen)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Articles created offline are queued and will automatically upload when an active internet connection is detected.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OfflineAmber)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        for (item in pendingUploads) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.action}: ${item.title}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = OfflineAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Queued",
                                        style = MaterialTheme.typography.labelSmall.copy(color = OfflineAmber)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Configuration Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Google Drive Folder Configuration",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Only visible to Admin. Normal users cannot view or edit these credentials.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Folder ID with Paste
                    Text(text = "Google Drive Folder ID / Link *", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = inputFolderId,
                        onValueChange = { inputFolderId = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("e.g. 1A2b3C4d... or full folder URL") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        inputFolderId = clip.getItemAt(0).coerceToText(context).toString()
                                        viewModel.onAdminInteraction()
                                    }
                                },
                                modifier = Modifier.testTag("admin_paste_folder_btn")
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentBlue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_folder_id_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // API Key
                    Text(text = "Google Drive API Key (Read / Query)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("e.g. AIzaSy...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_api_key_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // OAuth Write Token or Upload Endpoint
                    Text(text = "Drive OAuth Write Token (for direct APK upload)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = inputOAuthToken,
                        onValueChange = { inputOAuthToken = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("e.g. ya29...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_oauth_token_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Or Webhook / Apps Script Upload URL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = inputUploadEndpoint,
                        onValueChange = { inputUploadEndpoint = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_upload_endpoint_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.onAdminInteraction()
                            viewModel.saveAdminDriveSettings(
                                folderId = inputFolderId,
                                apiKey = inputApiKey,
                                oauthToken = inputOAuthToken,
                                uploadEndpoint = inputUploadEndpoint,
                                customUrl = inputCustomUrl
                            )
                            onShowSnackbar("Drive settings saved successfully.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_save_drive_btn")
                    ) {
                        Text("Save Drive Configuration")
                    }
                }
            }
        }

        // Manual Sync Control Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Manual Sync",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.onAdminInteraction()
                            viewModel.syncNow()
                        },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_sync_now_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now from Drive")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.onAdminInteraction()
                            viewModel.restoreFromDrive { msg ->
                                onShowSnackbar(msg)
                            }
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_restore_drive_btn")
                    ) {
                        Text("Restore from Drive")
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun AdminSecuritySettingsView(
    viewModel: EbookViewModel,
    onShowSnackbar: (String) -> Unit,
    onLockout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_security_settings_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Change Admin PIN",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Default PIN is 1234. Change it to secure your admin functions.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "Current PIN", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("Enter current PIN") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_current_pin_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "New PIN (min 4 digits)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("Enter new PIN") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_new_pin_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Confirm New PIN", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it; viewModel.onAdminInteraction() },
                        placeholder = { Text("Re-enter new PIN") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_confirm_pin_input")
                    )

                    if (statusText != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = statusText!!,
                            color = if (isError) WarningRed else OnlineGreen,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.onAdminInteraction()
                            if (newPin != confirmPin) {
                                isError = true
                                statusText = "New PIN and confirmation do not match."
                                return@Button
                            }

                            val (ok, msg) = viewModel.changePin(currentPin, newPin)
                            isError = !ok
                            statusText = msg
                            if (ok) {
                                currentPin = ""
                                newPin = ""
                                confirmPin = ""
                                onShowSnackbar("PIN successfully changed.")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_update_pin_btn")
                    ) {
                        Text("Update PIN")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security Policies",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 3 Wrong PINs: Locks PIN keypad for 30 seconds.\n• Auto-lock: Locks automatically when the app is closed, minimized, or inactive for 5 minutes.\n• Hidden Access: No menu item is displayed. Tapping the home title 5 times quickly unlocks the PIN dialog.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.lockAdmin()
                            onLockout()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_lock_now_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = WarningRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock Admin Now", color = WarningRed)
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
