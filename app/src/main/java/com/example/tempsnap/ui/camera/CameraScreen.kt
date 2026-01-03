package com.example.tempsnap.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import java.util.concurrent.Executor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    onNavigateToList: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    
    val isVideoMode by viewModel.isVideoMode.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val lensFacing by viewModel.lensFacing.collectAsState()
    val latestItem by viewModel.latestItem.collectAsState()
    val itemCount by viewModel.itemCount.collectAsState()
    
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    
    val permissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    val permissionState = rememberMultiplePermissionsState(permissions)
    
    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }
    
    // 录制计时器
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                viewModel.updateRecordingDuration(System.currentTimeMillis() - startTime)
                delay(1000)
            }
        }
    }
    
    // 更新闪光灯模式
    LaunchedEffect(flashMode) {
        imageCapture?.flashMode = flashMode
    }
    
    if (!permissionState.allPermissionsGranted) {
        PermissionRequest(onRequestPermission = { permissionState.launchMultiplePermissionRequest() })
        return
    }
    
    // 相机是否已加载
    var isCameraReady by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    
    // 相机绑定函数
    fun bindCamera(provider: ProcessCameraProvider, previewView: PreviewView, facing: Int) {
        val preview = Preview.Builder().build()
        
        val cameraSelector = if (facing == 0) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        
        val newImageCapture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()
        imageCapture = newImageCapture
        
        val recorder = Recorder.Builder()
            .setQualitySelector(viewModel.getQualitySelector())
            .build()
        val newVideoCapture = VideoCapture.withOutput(recorder)
        videoCapture = newVideoCapture
        
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                newImageCapture,
                newVideoCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
            isCameraReady = true
        } catch (e: Exception) {
            e.printStackTrace()
            isCameraReady = true // 即使失败也标记为 ready，避免卡在 loading
        }
    }
    
    // 切换镜头时重新绑定
    LaunchedEffect(lensFacing) {
        val provider = cameraProvider
        val preview = previewViewRef
        if (provider != null && preview != null) {
            bindCamera(provider, preview, lensFacing)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 相机预览
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewViewRef = this
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            cameraProvider = provider
                            bindCamera(provider, this, lensFacing)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isCameraReady = true
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        camera?.let { cam ->
                            val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                            val newZoom = (currentZoom * zoom).coerceIn(1f, 10f)
                            cam.cameraControl.setZoomRatio(newZoom)
                        }
                    }
                }
        )
        
        // Loading 页面 - 相机加载前显示
        androidx.compose.animation.AnimatedVisibility(
            visible = !isCameraReady,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            LoadingScreen()
        }
        
        // 顶部区域
        TopBar(
            isRecording = isRecording,
            recordingDuration = recordingDuration,
            flashMode = flashMode,
            onFlashClick = { viewModel.toggleFlash() }
        )
        
        // 底部操作区
        BottomControls(
            isVideoMode = isVideoMode,
            isRecording = isRecording,
            latestItemUri = latestItem?.uriString,
            latestItemIsVideo = latestItem?.isVideo ?: false,
            itemCount = itemCount,
            onModeChange = { isVideo ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                viewModel.setVideoMode(isVideo)
            },
            onShutterClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                if (isVideoMode) {
                    if (isRecording) {
                        recording?.stop()
                        recording = null
                        viewModel.setRecording(false)
                    } else {
                        startVideoRecording(
                            context = context,
                            videoCapture = videoCapture,
                            contentValues = viewModel.createVideoContentValues(),
                            onRecordingStarted = { rec ->
                                recording = rec
                                viewModel.setRecording(true)
                            },
                            onRecordingFinished = { uri, duration ->
                                viewModel.onMediaCaptured(uri, true, duration)
                            }
                        )
                    }
                } else {
                    takePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        contentValues = viewModel.createImageContentValues(),
                        onPhotoSaved = { uri -> viewModel.onMediaCaptured(uri, false) }
                    )
                }
            },
            onThumbnailClick = onNavigateToList,
            onSwitchCamera = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                viewModel.toggleLens()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun TopBar(
    isRecording: Boolean,
    recordingDuration: Long,
    flashMode: Int,
    onFlashClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // 闪光灯按钮
        IconButton(
            onClick = onFlashClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                    else -> Icons.Default.FlashOn // AUTO 模式也用闪电图标
                },
                contentDescription = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> "闪光灯开启"
                    ImageCapture.FLASH_MODE_OFF -> "闪光灯关闭"
                    else -> "闪光灯自动"
                },
                tint = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> Color.Gray
                    else -> Color.White
                }
            )
            // AUTO 模式显示 "A" 标记
            if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
                Text(
                    text = "A",
                    color = Color.White,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                )
            }
        }
        
        // 录制计时器
        if (isRecording) {
            val seconds = (recordingDuration / 1000).toInt()
            val minutes = seconds / 60
            val secs = seconds % 60
            
            Text(
                text = String.format("%02d:%02d", minutes, secs),
                color = Color.Red,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun BottomControls(
    isVideoMode: Boolean,
    isRecording: Boolean,
    latestItemUri: String?,
    latestItemIsVideo: Boolean,
    itemCount: Int,
    onModeChange: (Boolean) -> Unit,
    onShutterClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 32.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) {
                        onModeChange(false) // 右滑切换到照片
                    } else if (dragAmount < -50) {
                        onModeChange(true) // 左滑切换到视频
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 模式切换栏
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text(
                text = "照片",
                color = if (!isVideoMode) Color.Red else Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onModeChange(false) }
            )
            Text(
                text = "视频",
                color = if (isVideoMode) Color.Red else Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onModeChange(true) }
            )
        }
        
        // 快门和控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图入口
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .clickable(onClick = onThumbnailClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (latestItemUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(latestItemUri))
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = "最近拍摄",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // 角标（在外层，不受裁剪）
                if (itemCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .background(Color.Red, CircleShape)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (itemCount > 99) "99+" else itemCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // 快门按钮
            ShutterButton(
                isVideoMode = isVideoMode,
                isRecording = isRecording,
                onClick = onShutterClick
            )
            
            // 镜头切换
            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "切换镜头",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isVideoMode: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(3.dp, Color.White, CircleShape)
            .padding(5.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isVideoMode) {
            if (isRecording) {
                // 录制中：红色圆角矩形（停止符号）
                Box(
                    modifier = Modifier
                        .size((24 * scale).dp)
                        .background(Color.Red, RoundedCornerShape(4.dp))
                )
            } else {
                // 待机：红色实心圆
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Red, CircleShape)
                )
            }
        } else {
            // 照片模式：白色实心圆
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("需要相机和存储权限", color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    contentValues: ContentValues,
    onPhotoSaved: (Uri) -> Unit
) {
    val capture = imageCapture ?: return
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()
    
    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { uri ->
                    // 标记文件写入完成
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                    onPhotoSaved(uri)
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

private fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    contentValues: ContentValues,
    onRecordingStarted: (Recording) -> Unit,
    onRecordingFinished: (Uri, Long) -> Unit
) {
    val capture = videoCapture ?: return
    
    val mediaStoreOutput = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(contentValues).build()
    
    val startTime = System.currentTimeMillis()
    
    val recording = capture.output
        .prepareRecording(context, mediaStoreOutput)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        val duration = System.currentTimeMillis() - startTime
                        onRecordingFinished(event.outputResults.outputUri, duration)
                    }
                }
            }
        }
    
    onRecordingStarted(recording)
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // App 名称
            Text(
                text = "TempSnap",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 副标题
            Text(
                text = "Ephemeral Camera",
                color = Color.Gray,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // 功能介绍
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeatureItem(
                    icon = Icons.Default.PhotoCamera,
                    text = "Capture temporary photos & videos"
                )
                FeatureItem(
                    icon = Icons.Default.Timer,
                    text = "Auto-delete after set time"
                )
                FeatureItem(
                    icon = Icons.Default.Lock,
                    text = "All data stored locally for privacy"
                )
                FeatureItem(
                    icon = Icons.Default.CleaningServices,
                    text = "Keep your gallery clutter-free"
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // 加载指示器
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
    }
}
