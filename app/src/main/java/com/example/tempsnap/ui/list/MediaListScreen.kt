package com.example.tempsnap.ui.list

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.tempsnap.data.MediaItem
import com.example.tempsnap.data.SettingsDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    viewModel: MediaListViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val items by viewModel.allItems.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val retentionDays by viewModel.retentionDays.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val cleanupNotification by viewModel.cleanupNotification.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待清理清单") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (selectedItems.isNotEmpty()) {
                BottomAppBar(
                    containerColor = Color.Black
                ) {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.keepSelectedItems() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("永久保留 (${selectedItems.size})")
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无待清理的照片或视频",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    MediaItemCard(
                        item = item,
                        isSelected = selectedItems.contains(item.id),
                        remainingTime = viewModel.getRemainingTime(item),
                        remainingTimeColor = viewModel.getRemainingTimeColor(item),
                        onClick = { viewModel.toggleSelection(item.id) }
                    )
                }
            }
        }
    }
    
    if (showSettings) {
        SettingsBottomSheet(
            retentionDays = retentionDays,
            videoQuality = videoQuality,
            cleanupNotification = cleanupNotification,
            onRetentionDaysChange = { viewModel.setRetentionDays(it) },
            onVideoQualityChange = { viewModel.setVideoQuality(it) },
            onCleanupNotificationChange = { viewModel.setCleanupNotification(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun MediaItemCard(
    item: MediaItem,
    isSelected: Boolean,
    remainingTime: String,
    remainingTimeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.DarkGray)
            .then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(item.uriString))
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // 视频标识
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Videocam,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                if (item.duration > 0) {
                    Spacer(Modifier.width(2.dp))
                    val seconds = (item.duration / 1000).toInt()
                    val mins = seconds / 60
                    val secs = seconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // 倒计时标签
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = remainingTime,
                color = remainingTimeColor,
                fontSize = 9.sp
            )
        }
        
        // 选中标记
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    retentionDays: Int,
    videoQuality: Int,
    cleanupNotification: Boolean,
    onRetentionDaysChange: (Int) -> Unit,
    onVideoQualityChange: (Int) -> Unit,
    onCleanupNotificationChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // 保留时长
            Text("保留时长", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 7, 30).forEach { days ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = { onRetentionDaysChange(days) },
                        label = { Text("${days}天") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            Text(
                "修改后仅对新拍摄内容生效",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            // 视频画质
            Text("视频画质", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = videoQuality == SettingsDataStore.QUALITY_720P,
                    onClick = { onVideoQualityChange(SettingsDataStore.QUALITY_720P) },
                    label = { Text("720P (省空间)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary
                    )
                )
                FilterChip(
                    selected = videoQuality == SettingsDataStore.QUALITY_1080P,
                    onClick = { onVideoQualityChange(SettingsDataStore.QUALITY_1080P) },
                    label = { Text("1080P") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // 清理通知
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("清理通知", color = Color.White)
                    Text("删除前发送系统通知", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = cleanupNotification,
                    onCheckedChange = onCleanupNotificationChange
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}
