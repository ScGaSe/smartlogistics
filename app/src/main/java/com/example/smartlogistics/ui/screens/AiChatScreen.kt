package com.example.smartlogistics.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.AIState
import com.example.smartlogistics.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.net.URLEncoder

// ==================== AI对话消息数据类 ====================
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val intentType: String? = null,  // 导航/问答/拥堵等
    val destination: String? = null,  // 如果是导航，目的地POI
    val confidence: Float? = null
)

// ==================== AI对话页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    
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
                                // 发送消息
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
                            // 跳转到导航页面
                            navController.navigate("navigation_map")
                        }
                    )
                }
                
                // 加载中
                if (isLoading) {
                    item {
                        TypingIndicator()
                    }
                }
            }
            
            // 输入区域
            InputSection(
                inputText = inputText,
                onInputChange = { inputText = it },
                isRecording = isRecording,
                onRecordingChange = { isRecording = it },
                primaryColor = primaryColor,
                onSend = {
                    if (inputText.isNotBlank()) {
                        messages = messages + ChatMessage(content = inputText, isUser = true)
                        viewModel?.askAI(inputText, if (isProfessional) "professional" else "personal")
                        inputText = ""
                    }
                }
            )
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
                "危化品车从哪个门进？",
                "导航到3号仓库",
                "查询今天的路况",
                "哪个停车区适合大货车？"
            )
        } else {
            listOf(
                "附近有什么好吃的？",
                "导航到T2航站楼",
                "哪个停车场有空位？",
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
            // AI头像
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
            // 消息内容
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
            
            // 如果是导航意图，显示导航按钮
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
            
            // 意图识别标签
            if (!message.isUser && message.confidence != null && message.confidence > 0.7f) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "识别: ${message.intentType ?: "问答"} | 置信度: ${(message.confidence * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }
        
        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
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
        // AI头像
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
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    primaryColor: Color,
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
            // 输入框
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
                    .background(
                        if (isRecording) ErrorRed else primaryColor.copy(alpha = 0.1f)
                    )
                    .clickable { onRecordingChange(!isRecording) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = "语音",
                    tint = if (isRecording) Color.White else primaryColor,
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
