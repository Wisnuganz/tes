package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.SolvedTaskItem
import com.example.model.StealthMode
import com.example.model.TaskAssistantUiState
import com.example.model.TaskToolType
import com.example.ui.components.AiKnotOrbIcon
import com.example.ui.components.ChatGptMarkdownText
import com.example.ui.theme.AiAmber
import com.example.ui.theme.AiEmerald
import com.example.ui.theme.AiEmeraldLightBg
import com.example.ui.theme.CbtBlueLightBg
import com.example.ui.theme.CbtCanvasBg
import com.example.ui.theme.CbtCanvasBgBottom
import com.example.ui.theme.CbtCardBorder
import com.example.ui.theme.CbtCardWhite
import com.example.ui.theme.CbtDangerLightBg
import com.example.ui.theme.CbtDangerRed
import com.example.ui.theme.CbtNavyHeader
import com.example.ui.theme.CbtRoyalBlue
import com.example.ui.theme.CbtSubtleSurface
import com.example.ui.theme.CbtTextPrimary
import com.example.ui.theme.CbtTextSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun TaskWorkspaceScreen(
    state: TaskAssistantUiState,
    onScratchpadChange: (String) -> Unit,
    onOpenAiPanel: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleSystemOverlay: () -> Unit,
    onToggleLiveScreen: () -> Unit,
    onScanScreenNow: () -> Unit,
    onSelectStealthMode: (StealthMode) -> Unit,
    onToggleAntiCapture: () -> Unit,
    onOpenToolWithInput: (TaskToolType, String, Bitmap?) -> Unit,
    onSolveDirectChat: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onCopySuccess: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var apiKeyDraft by remember { mutableStateOf("") }
    var revealApiKey by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                onOpenToolWithInput(TaskToolType.SCAN_PHOTO, state.scratchpadText, bmp)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onOpenToolWithInput(TaskToolType.SCAN_PHOTO, state.scratchpadText, bitmap)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(CbtCanvasBg, CbtCanvasBgBottom)
                )
            )
    ) {
        // Dark Top Header
        Surface(
            color = CbtNavyHeader,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AiEmerald),
                        contentAlignment = Alignment.Center
                    ) {
                        AiKnotOrbIcon(size = 20.dp, tint = Color.White)
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.workspace_title),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isOverlayServiceRunning) {
                                "Overlay Sistem Aktif • ${state.stealthMode.label}"
                            } else {
                                "Gemini 3.8 • Siap Bantu Tugas"
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = AiEmerald,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onOpenAiPanel)
                        .testTag("workspace_open_ai_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiKnotOrbIcon(size = 15.dp, tint = Color.White)
                        Text(
                            text = "Buka AI",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gemini API Key (BYOK) Card
                item(key = "gemini_api_key_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CbtCardWhite),
                        border = BorderStroke(
                            1.dp,
                            if (state.hasCustomApiKey) AiEmerald.copy(alpha = 0.6f) else CbtCardBorder
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (state.hasCustomApiKey) AiEmeraldLightBg else CbtBlueLightBg,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Key,
                                                contentDescription = "Gemini API Key",
                                                tint = if (state.hasCustomApiKey) AiEmerald else CbtRoyalBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Koneksi Gemini API Key",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CbtTextPrimary
                                        )
                                        Text(
                                            text = if (state.hasCustomApiKey) {
                                                "Tersimpan: ${state.maskedApiKey}"
                                            } else {
                                                "Ambil key gratis di aistudio.google.com/apikey"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CbtTextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (state.hasCustomApiKey) AiEmeraldLightBg else CbtDangerLightBg
                                ) {
                                    Text(
                                        text = if (state.hasCustomApiKey) "AKTIF" else "PERLU KEY",
                                        color = if (state.hasCustomApiKey) AiEmerald else CbtDangerRed,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = apiKeyDraft,
                                onValueChange = { apiKeyDraft = it },
                                singleLine = true,
                                visualTransformation = if (revealApiKey) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                placeholder = {
                                    Text(
                                        text = if (state.hasCustomApiKey) {
                                            "Tempel key baru untuk mengganti..."
                                        } else {
                                            "Tempel API Key (AIza...) di sini"
                                        },
                                        color = CbtTextSecondary,
                                        fontSize = 13.sp
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { revealApiKey = !revealApiKey }) {
                                            Icon(
                                                imageVector = if (revealApiKey) {
                                                    Icons.Outlined.VisibilityOff
                                                } else {
                                                    Icons.Outlined.Visibility
                                                },
                                                contentDescription = "Tampilkan atau sembunyikan API Key",
                                                tint = CbtTextSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                                if (clip.isNotBlank()) {
                                                    apiKeyDraft = clip
                                                    onCopySuccess("API Key ditempel")
                                                }
                                            },
                                            modifier = Modifier.testTag("paste_api_key_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentPaste,
                                                contentDescription = "Tempel API Key",
                                                tint = AiEmerald
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AiEmerald,
                                    unfocusedBorderColor = CbtCardBorder,
                                    focusedTextColor = CbtTextPrimary,
                                    unfocusedTextColor = CbtTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input")
                            )

                            if (!state.apiKeyStatusMessage.isNullOrBlank()) {
                                Text(
                                    text = state.apiKeyStatusMessage,
                                    fontSize = 12.sp,
                                    color = if (state.apiKeyStatusMessage.startsWith("Gagal")) {
                                        CbtDangerRed
                                    } else {
                                        AiEmerald
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onSaveApiKey(apiKeyDraft)
                                    },
                                    enabled = !state.isTestingApiKey && apiKeyDraft.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AiEmerald),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("save_test_api_key_button")
                                ) {
                                    if (state.isTestingApiKey) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Simpan & Tes Key",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (state.hasCustomApiKey) {
                                    OutlinedButton(
                                        onClick = {
                                            apiKeyDraft = ""
                                            onClearApiKey()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CbtDangerRed.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .height(46.dp)
                                            .testTag("clear_api_key_button")
                                    ) {
                                        Text(
                                            text = "Hapus",
                                            color = CbtDangerRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // System Floating Overlay & Stealth Control Card
                item(key = "overlay_control_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CbtCardWhite),
                        border = BorderStroke(1.dp, CbtCardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (state.isOverlayServiceRunning) AiEmeraldLightBg else CbtBlueLightBg,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Layers,
                                                contentDescription = "Floating Overlay",
                                                tint = if (state.isOverlayServiceRunning) AiEmerald else CbtRoyalBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Floating Overlay (Di Atas Aplikasi Lain)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CbtTextPrimary
                                        )
                                        Text(
                                            text = when {
                                                !state.hasOverlayPermission -> "Perlu izin tampil di atas aplikasi lain"
                                                state.isOverlayServiceRunning -> "Aktif • Melayang di atas semua aplikasi"
                                                else -> "Izin aktif • Siap dijalankan"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CbtTextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when {
                                        !state.hasOverlayPermission -> CbtDangerLightBg
                                        state.isOverlayServiceRunning -> AiEmeraldLightBg
                                        else -> CbtSubtleSurface
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            !state.hasOverlayPermission -> "IZIN"
                                            state.isOverlayServiceRunning -> "AKTIF"
                                            else -> "MATI"
                                        },
                                        color = when {
                                            !state.hasOverlayPermission -> CbtDangerRed
                                            state.isOverlayServiceRunning -> AiEmerald
                                            else -> CbtTextSecondary
                                        },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (!state.hasOverlayPermission) {
                                Button(
                                    onClick = onRequestOverlayPermission,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CbtRoyalBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("grant_overlay_permission_button")
                                ) {
                                    Text("Izinkan Tampil di Atas Aplikasi Lain", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onToggleSystemOverlay,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isOverlayServiceRunning) CbtDangerRed else AiEmerald
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("toggle_system_overlay_button")
                                ) {
                                    Text(
                                        text = if (state.isOverlayServiceRunning) {
                                            "Matikan Floating Overlay"
                                        } else {
                                            "Jalankan Floating AI di Layar"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Live Screen (MediaProjection 1-Tap Screen Scan)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.isLiveScreenActive) AiEmeraldLightBg else CbtSubtleSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isLiveScreenActive) AiEmerald else CbtCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onToggleLiveScreen)
                                    .testTag("workspace_live_screen_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FitScreen,
                                            contentDescription = "Live Screen",
                                            tint = if (state.isLiveScreenActive) AiEmerald else CbtTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Live Screen (Scan Layar Otomatis)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CbtTextPrimary
                                            )
                                            Text(
                                                text = if (state.isLiveScreenActive) {
                                                    "Aktif • Ketuk Scan Layar di bola/panel AI"
                                                } else {
                                                    "Izinkan tangkap layar agar AI baca soal 1-ketuk"
                                                },
                                                fontSize = 11.sp,
                                                color = CbtTextSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (state.isLiveScreenActive) "AKTIF" else "MATI",
                                        color = if (state.isLiveScreenActive) AiEmerald else CbtTextSecondary,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Auto-Tap Aksesibilitas (Baca soal & ketuk jawaban otomatis)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.isAccessibilityEnabled) AiEmeraldLightBg else CbtSubtleSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isAccessibilityEnabled) AiEmerald else CbtCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenAccessibilitySettings)
                                    .testTag("workspace_accessibility_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.TouchApp,
                                            contentDescription = "Auto-Tap Aksesibilitas",
                                            tint = if (state.isAccessibilityEnabled) AiEmerald else CbtRoyalBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Auto-Tap Aksesibilitas (Jawab Otomatis)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CbtTextPrimary
                                            )
                                            Text(
                                                text = if (state.isAccessibilityEnabled) {
                                                    "Aktif • Siap Jawab 1 Soal, Auto 100 & KKM"
                                                } else {
                                                    "Ketuk untuk aktifkan 'Cerdas AI Auto-Jawab' di Setelan"
                                                },
                                                fontSize = 11.sp,
                                                color = CbtTextSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (state.isAccessibilityEnabled) "AKTIF" else "ATUR",
                                        color = if (state.isAccessibilityEnabled) AiEmerald else CbtRoyalBlue,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (state.isLiveScreenActive) {
                                OutlinedButton(
                                    onClick = onScanScreenNow,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AiEmerald),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("workspace_scan_screen_now_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FitScreen,
                                        contentDescription = "Scan Layar Sekarang",
                                        tint = AiEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        text = "Tes Scan Layar Sekarang",
                                        color = AiEmerald,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Mode Samaran Selector (Normal / 16% / Tak Terlihat 0%)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VisibilityOff,
                                        contentDescription = "Mode Samaran",
                                        tint = CbtTextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Mode Samaran Bola Mengambang",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CbtTextPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StealthMode.entries.forEach { mode ->
                                        val selected = state.stealthMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) CbtNavyHeader else CbtSubtleSurface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) CbtNavyHeader else CbtCardBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSelectStealthMode(mode) }
                                                .testTag("workspace_stealth_${mode.name.lowercase()}")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = mode.badge,
                                                    color = if (selected) AiEmerald else CbtTextPrimary,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = mode.label,
                                                    color = if (selected) Color.White else CbtTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Anti-Rekam / Screenshot (FLAG_SECURE)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.isAntiCaptureEnabled) AiEmeraldLightBg else CbtSubtleSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isAntiCaptureEnabled) AiEmerald else CbtCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onToggleAntiCapture)
                                    .testTag("workspace_anti_capture_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Security,
                                            contentDescription = "Anti Rekam",
                                            tint = if (state.isAntiCaptureEnabled) AiEmerald else CbtTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Anti-Screenshot & Rekam Layar",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CbtTextPrimary
                                            )
                                            Text(
                                                text = "Jendela AI tidak tertangkap rekaman layar / SS",
                                                fontSize = 11.sp,
                                                color = CbtTextSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (state.isAntiCaptureEnabled) "AKTIF" else "MATI",
                                        color = if (state.isAntiCaptureEnabled) AiEmerald else CbtTextSecondary,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Task Capture Card
                item(key = "capture_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CbtCardWhite),
                        border = BorderStroke(1.dp, CbtCardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Input Cepat Tugas",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CbtTextPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AiEmeraldLightBg,
                                    modifier = Modifier
                                        .clickable {
                                            val clip = clipboardManager.getText()?.text.orEmpty()
                                            if (clip.isNotBlank()) {
                                                onScratchpadChange(clip)
                                                onCopySuccess("Teks ditempel dari clipboard")
                                            }
                                        }
                                        .testTag("workspace_paste_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentPaste,
                                            contentDescription = "Tempel teks",
                                            tint = AiEmerald,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Tempel",
                                            color = AiEmerald,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = state.scratchpadText,
                                onValueChange = onScratchpadChange,
                                minLines = 3,
                                maxLines = 6,
                                placeholder = {
                                    Text(
                                        "Tempel soal PR, pilihan ganda, materi, atau pertanyaan esai di sini...",
                                        color = CbtTextSecondary
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AiEmerald,
                                    unfocusedBorderColor = CbtCardBorder,
                                    focusedTextColor = CbtTextPrimary,
                                    unfocusedTextColor = CbtTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("workspace_scratchpad_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CbtCardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("workspace_gallery_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = "Pilih foto soal",
                                        tint = CbtTextPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Foto Soal", color = CbtTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { cameraLauncher.launch(null) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CbtCardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("workspace_camera_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CameraAlt,
                                        contentDescription = "Ambil foto kamera",
                                        tint = CbtTextPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Kamera", color = CbtTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Button(
                                onClick = {
                                    val text = state.scratchpadText.trim()
                                    if (text.isNotEmpty()) {
                                        onSolveDirectChat(text)
                                    } else {
                                        onOpenAiPanel()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AiEmerald),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("workspace_solve_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = if (state.scratchpadText.isBlank()) "Buka Panel Tools AI" else "Kerjakan dengan AI",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // History Header
                item(key = "history_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Riwayat Jawaban Tugas (${state.solvedTasks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = CbtTextPrimary
                        )
                    }
                }

                if (state.solvedTasks.isEmpty()) {
                    item(key = "empty_history") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CbtCardWhite,
                            border = BorderStroke(1.dp, CbtCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = "Belum ada tugas",
                                    tint = CbtTextSecondary,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Belum ada riwayat jawaban",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CbtTextPrimary
                                )
                                Text(
                                    text = "Nyalakan Floating Overlay untuk memunculkan bola AI di atas aplikasi lain, atau gunakan Scan Foto / Tools di panel AI.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CbtTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(state.solvedTasks, key = { it.id }) { item ->
                        SolvedTaskHistoryCard(
                            item = item,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(item.answerText))
                                onCopySuccess("Jawaban disalin ke clipboard")
                            },
                            onDelete = { onDeleteTask(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SolvedTaskHistoryCard(
    item: SolvedTaskItem,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CbtCardWhite),
        border = BorderStroke(1.dp, CbtCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CbtBlueLightBg
                    ) {
                        Text(
                            text = item.toolType.badge,
                            color = CbtRoyalBlue,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = item.timestampLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = CbtTextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AiEmeraldLightBg,
                        modifier = Modifier
                            .clickable(onClick = onCopy)
                            .testTag("copy_history_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Salin jawaban",
                                tint = AiEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Salin",
                                color = AiEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Hapus riwayat",
                            tint = CbtTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = item.promptPreview,
                style = MaterialTheme.typography.bodySmall,
                color = CbtTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CbtSubtleSurface,
                border = BorderStroke(1.dp, CbtCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                ChatGptMarkdownText(
                    markdown = item.answerText,
                    textColor = CbtTextPrimary,
                    accentColor = AiEmerald,
                    secondaryColor = CbtTextSecondary,
                    codeBackgroundColor = CbtNavyHeader,
                    inlineCodeBackgroundColor = AiEmeraldLightBg,
                    calloutBackgroundColor = AiEmeraldLightBg,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
