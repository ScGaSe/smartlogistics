package com.example.smartlogistics.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.utils.XunfeiSpeechHelper
import com.example.smartlogistics.viewmodel.AIState
import com.example.smartlogistics.viewmodel.MainViewModel

// ==================== AI对话消息数据类 ====================
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val intentType: String? = null,
    val destination: String? = null,
    val confidence: Float? = null
)

// ==================== AI对话页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }

    // ========== 讯飞语音识别相关状态 ==========
    val speechHelper = remember { XunfeiSpeechHelper() }
    val speechState by speechHelper.state.collectAsState()
    val volumeLevel by speechHelper.volumeLevel.collectAsState()
    var showVoiceDialog by remember { mutableStateOf(false) }

    // 权限请求
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            showVoiceDialog = true
            speechHelper.startListening()
        } else {
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 处理语音识别结果
    LaunchedEffect(speechState) {
        when (speechState) {
            is XunfeiSpeechHelper.SpeechState.Result -> {
                val text = (speechState as XunfeiSpeechHelper.SpeechState.Result).text
                inputText = text
                showVoiceDialog = false
                speechHelper.resetState()
            }
            is XunfeiSpeechHelper.SpeechState.Error -> {
                val error = (speechState as XunfeiSpeechHelper.SpeechState.Error).message
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                showVoiceDialog = false
                speechHelper.resetState()
            }
            else -> {}
        }
    }

    // 释放资源
    DisposableEffect(Unit) {
        onDispose {
            speechHelper.destroy()
        }
    }
    // ========== 语音识别相关状态结束 ==========

    val listState = rememberLazyListState()
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    // AI回复状态
    val aiState by viewModel?.aiState?.collectAsState() ?: remember { mutableStateOf(AIState.Idle) }
    val aiResponse by viewModel?.aiResponse?.collectAsState() ?: remember { mutableStateOf(null) }

    // 处理AI回复
    LaunchedEffect(aiState) {
        when (aiState) {
            is AIState.Loading -> isLoading = true
            is AIState.Success -> {
                isLoading = false
                aiResponse?.let { response ->
                    val aiMessage = ChatMessage(
                        content = response.answer,
                        isUser = false,
                        intentType = response.intent?.intentType,
                        destination = response.intent?.destination,
                        confidence = response.intent?.confidence
                    )
                    messages = messages + aiMessage
                }
                viewModel?.resetAIState()
            }
            is AIState.Error -> {
                isLoading = false
                messages = messages + ChatMessage(
                    content = "抱歉，请求出错了，请稍后重试。",
                    isUser = false
                )
                viewModel?.resetAIState()
            }
            else -> {}
        }
    }

    // 滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "智能助手",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = BackgroundPrimary
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 消息列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 欢迎消息
                    if (messages.isEmpty()) {
                        item {
                            WelcomeSection(
                                isProfessional = isProfessional,
                                onSuggestionClick = { suggestion ->
                                    inputText = suggestion
                                    messages = messages + ChatMessage(content = suggestion, isUser = true)
                                    viewModel?.askAI(suggestion, if (isProfessional) "professional" else "personal")
                                    inputText = ""
                                }
                            )
                        }
                    }

                    // 消息列表
                    items(messages) { message ->
                        ChatBubble(
                            message = message,
                            primaryColor = primaryColor,
                            onNavigateClick = { destination ->
                                navController.navigate("navigation_map")
                            }
                        )
                    }
<<<<<<< HEAD
                }
                
                // 消息列表
                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        primaryColor = primaryColor,
                        onNavigateClick = { destination ->
                            // 跳转到导航页面
                            navController.navigate("navigation_map_new")
=======

                    // 加载中
                    if (isLoading) {
                        item {
                            TypingIndicator()
>>>>>>> 3a884642b16cc6e6af1d4d3ea8697fee44ce37ab
                        }
                    }
                }

                // 输入区域
                InputSection(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    primaryColor = primaryColor,
                    onVoiceClick = {
                        if (hasRecordPermission) {
                            showVoiceDialog = true
                            speechHelper.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            messages = messages + ChatMessage(content = inputText, isUser = true)
                            viewModel?.askAI(inputText, if (isProfessional) "professional" else "personal")
                            inputText = ""
                        }
                    }
                )
            }

            // 语音识别弹窗
            if (showVoiceDialog) {
                VoiceRecordingDialog(
                    speechState = speechState,
                    volumeLevel = volumeLevel,
                    primaryColor = primaryColor,
                    onDismiss = {
                        speechHelper.cancel()
                        showVoiceDialog = false
                    },
                    onStop = {
                        speechHelper.stopListening()
                    }
                )
            }
        }
    }
}

// ==================== 语音录制弹窗 ====================
@Composable
private fun VoiceRecordingDialog(
    speechState: XunfeiSpeechHelper.SpeechState,
    volumeLevel: Float,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onStop: () -> Unit
) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // 音量响应的缩放
    val volumeScale = 1f + volumeLevel * 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        // 点击背景关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        Card(
            modifier = Modifier.width(280.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态文字
                Text(
                    text = when (speechState) {
                        is XunfeiSpeechHelper.SpeechState.Listening -> "正在聆听..."
                        is XunfeiSpeechHelper.SpeechState.Processing -> "识别中..."
                        else -> "请说话"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 麦克风动画
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // 外圈波纹
                    if (speechState is XunfeiSpeechHelper.SpeechState.Listening) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(scale * volumeScale)
                                .background(
                                    primaryColor.copy(alpha = 0.1f),
                                    CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .scale(volumeScale)
                                .background(
                                    primaryColor.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                    }

                    // 麦克风图标
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(primaryColor, primaryColor.copy(alpha = 0.8f))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (speechState is XunfeiSpeechHelper.SpeechState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 音量指示条
                if (speechState is XunfeiSpeechHelper.SpeechState.Listening) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(7) { index ->
                            val barHeight = when {
                                index == 3 -> 24.dp * (0.5f + volumeLevel * 0.5f)
                                index == 2 || index == 4 -> 20.dp * (0.4f + volumeLevel * 0.6f)
                                index == 1 || index == 5 -> 16.dp * (0.3f + volumeLevel * 0.7f)
                                else -> 12.dp * (0.2f + volumeLevel * 0.8f)
                            }
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(barHeight)
                                    .background(
                                        primaryColor.copy(alpha = 0.6f + volumeLevel * 0.4f),
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 提示文字
                Text(
                    text = when (speechState) {
                        is XunfeiSpeechHelper.SpeechState.Listening -> "松开或点击完成"
                        is XunfeiSpeechHelper.SpeechState.Processing -> "正在识别您的语音"
                        else -> "点击麦克风开始"
                    },
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 取消按钮
                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextSecondary)
                }
            }
        }
    }
}

// ==================== 欢迎区域 ====================
@Composable
private fun WelcomeSection(
    isProfessional: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // AI头像
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "你好！我是智能助手",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isProfessional) "货运导航、货物报备、路况查询，有什么可以帮你？"
            else "导航出行、停车查询、行程管理，有什么可以帮你？",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 快捷建议
        Text(
            text = "你可以这样问我：",
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        val suggestions = if (isProfessional) {
            listOf(
                "导航到3号仓库",
                "查询今天的路况",
                "哪个停车区适合大货车？",
                "危化品车从哪个门进？"
            )
        } else {
            listOf(
                "导航到T2航站楼",
                "哪个停车场有空位？",
                "附近有什么好吃的？",
                "今天机场路堵不堵？"
            )
        }

        suggestions.forEach { suggestion ->
            SuggestionChip(
                text = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ==================== 建议芯片 ====================
@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.LightbulbCircle,
                contentDescription = null,
                tint = Color(0xFF667EEA),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}

// ==================== 聊天气泡 ====================
@Composable
private fun ChatBubble(
    message: ChatMessage,
    primaryColor: Color,
    onNavigateClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = if (message.isUser) 16.dp else 4.dp,
                    topEnd = if (message.isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isUser) primaryColor else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp, 10.dp),
                    fontSize = 15.sp,
                    color = if (message.isUser) Color.White else TextPrimary,
                    lineHeight = 22.sp
                )
            }

            if (!message.isUser && message.intentType == "navigation" && message.destination != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onNavigateClick(message.destination) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("开始导航", fontSize = 14.sp)
                }
            }
        }

        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(primaryColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== 打字指示器 ====================
@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Card(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(16.dp, 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = index * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TextTertiary.copy(alpha = alpha), CircleShape)
                    )
                }
            }
        }
    }
}

// ==================== 输入区域 ====================
@Composable
private fun InputSection(
    inputText: String,
    onInputChange: (String) -> Unit,
    primaryColor: Color,
    onVoiceClick: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入您的问题...", color = TextTertiary) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = BackgroundSecondary,
                    unfocusedContainerColor = BackgroundSecondary
                ),
                singleLine = true,
                trailingIcon = {
                    if (inputText.isNotBlank()) {
                        IconButton(onClick = { onInputChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = TextTertiary
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 语音按钮
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.1f))
                    .clickable { onVoiceClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "语音输入",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) primaryColor else primaryColor.copy(alpha = 0.3f)
                    )
                    .clickable(enabled = inputText.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}