package com.example.smartlogistics.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.AIState
import com.example.smartlogistics.viewmodel.MainViewModel
import com.amap.api.maps.model.LatLng

// ==================== 地图导航页面 (高德地图版) ====================
@Composable
fun NavigationMapScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val routeResult by viewModel?.routeResult?.collectAsState() ?: remember { mutableStateOf(null) }
    val trafficData by viewModel?.trafficData?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // 地图状态
    var showTraffic by remember { mutableStateOf(true) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var destination by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(true) }
    
    // 权限请求
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!hasLocationPermission) {
            Toast.makeText(context, "需要定位权限才能显示当前位置", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 请求权限
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        viewModel?.fetchTrafficData()
    }
    
    // 示例POI标记点
    val sampleMarkers = remember {
        listOf(
            MarkerData(
                position = LatLng(39.908823, 116.397470),
                title = "天安门广场",
                snippet = "北京市中心"
            ),
            MarkerData(
                position = LatLng(39.915119, 116.403963),
                title = "故宫博物院",
                snippet = "世界文化遗产"
            )
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 高德地图 ====================
        AMapView(
            modifier = Modifier.fillMaxSize(),
            showMyLocation = hasLocationPermission,
            showTraffic = showTraffic,
            markers = sampleMarkers,
            onMapReady = { aMap ->
                // 地图准备就绪
            },
            onLocationChanged = { location ->
                currentLocation = LatLng(location.latitude, location.longitude)
            }
        )
        
        // ==================== 顶部搜索栏 ====================
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 返回按钮和标题
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "返回",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "导航",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 收起按钮
                            IconButton(onClick = { isSearchExpanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "收起",
                                    tint = TextSecondary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 起点
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(CarGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (currentLocation != null) "我的位置 (已定位)" else "我的位置 (定位中...)",
                                fontSize = 15.sp,
                                color = if (currentLocation != null) TextPrimary else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = if (currentLocation != null) CarGreen else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 连接线
                        Box(
                            modifier = Modifier
                                .padding(start = 5.dp, top = 4.dp, bottom = 4.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(BorderLight)
                        )
                        
                        // 终点输入
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it },
                            placeholder = { Text("输入目的地...", color = TextTertiary) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(ErrorRed, CircleShape)
                                )
                            },
                            trailingIcon = {
                                if (destination.isNotBlank()) {
                                    IconButton(onClick = { destination = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "清除",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBlue,
                                unfocusedBorderColor = BorderLight,
                                unfocusedContainerColor = BackgroundSecondary,
                                focusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )
                        
                        // 搜索按钮
                        if (destination.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    // TODO: 调用路线规划API
                                    Toast.makeText(context, "正在规划路线到: $destination", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("开始导航")
                            }
                        }
                    }
                }
            }
        }
        
        // 展开搜索栏按钮 (当收起时显示)
        if (!isSearchExpanded) {
            FloatingActionButton(
                onClick = { isSearchExpanded = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding(),
                containerColor = Color.White,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        }
        
        // ==================== 右侧控制按钮 ====================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            // 路况开关
            FloatingActionButton(
                onClick = { showTraffic = !showTraffic },
                modifier = Modifier.size(48.dp),
                containerColor = if (showTraffic) BrandBlue else Color.White,
                contentColor = if (showTraffic) Color.White else TextPrimary
            ) {
                Icon(Icons.Rounded.Traffic, contentDescription = "路况")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 图层切换
            FloatingActionButton(
                onClick = { /* 切换地图图层 */ },
                modifier = Modifier.size(48.dp),
                containerColor = Color.White,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Rounded.Layers, contentDescription = "图层")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 定位按钮
            FloatingActionButton(
                onClick = {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        Toast.makeText(context, "正在定位...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = BrandBlue,
                contentColor = Color.White
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = "定位",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        // ==================== 底部路况信息 ====================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "实时路况",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (showTraffic) "已开启" else "已关闭",
                        fontSize = 12.sp,
                        color = if (showTraffic) CarGreen else TextTertiary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 路况图例
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TrafficLegendItem(color = CongestionFree, label = "畅通")
                    TrafficLegendItem(color = CongestionLight, label = "缓行")
                    TrafficLegendItem(color = CongestionModerate, label = "拥堵")
                    TrafficLegendItem(color = CongestionSevere, label = "严重")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 快捷操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.NearMe,
                        text = "附近",
                        onClick = { }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.LocalParking,
                        text = "停车场",
                        onClick = { }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.LocalGasStation,
                        text = "加油站",
                        onClick = { }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Restaurant,
                        text = "美食",
                        onClick = { }
                    )
                }
            }
        }
    }
}

// ==================== 路况图例项 ====================
@Composable
private fun TrafficLegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp, 4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

// ==================== 快捷操作按钮 ====================
@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(BackgroundSecondary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

// ==================== AI智能助手结果页 ====================
@Composable
fun AiResultScreen(
    navController: NavController,
    query: String,
    viewModel: MainViewModel? = null
) {
    val aiState by viewModel?.aiState?.collectAsState() ?: remember { mutableStateOf(AIState.Idle) }
    val aiResponse by viewModel?.aiResponse?.collectAsState() ?: remember { mutableStateOf(null) }
    
    var userInput by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<Pair<Boolean, String>>()) } // true = user, false = AI
    
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            chatHistory = chatHistory + (true to query)
            viewModel?.askAI(query)
        }
    }
    
    LaunchedEffect(aiResponse) {
        aiResponse?.let {
            chatHistory = chatHistory + (false to it.answer)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPrimary)
        ) {
            // 顶部栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // AI头像
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(AIGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "AI智能助手",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "为您提供专业导航建议",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            // 聊天内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 欢迎消息
                if (chatHistory.isEmpty()) {
                    AiWelcomeCard()
                }
                
                // 聊天历史
                chatHistory.forEach { (isUser, message) ->
                    if (isUser) {
                        UserMessageBubble(message = message)
                    } else {
                        AiMessageBubble(message = message)
                    }
                }
                
                // 加载状态
                if (aiState is AIState.Loading) {
                    AiTypingIndicator()
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // 底部输入区域
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入您的问题...", color = TextTertiary) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderLight
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                FloatingActionButton(
                    onClick = {
                        if (userInput.isNotBlank()) {
                            chatHistory = chatHistory + (true to userInput)
                            viewModel?.askAI(userInput)
                            userInput = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = BrandBlue
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "发送",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// ==================== AI欢迎卡片 ====================
@Composable
private fun AiWelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AIGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "您好，我是AI智能助手",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Text(
                text = "我可以帮您规划路线、查询路况、推荐停车场等",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 快捷问题
            Text(
                text = "您可以这样问我：",
                fontSize = 13.sp,
                color = TextTertiary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            listOf(
                "附近有什么好吃的?",
                "去机场走哪条路最快?",
                "危化品车辆禁行区域有哪些?"
            ).forEach { suggestion ->
                SuggestionChip(text = suggestion)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ==================== 建议问题标签 ====================
@Composable
private fun SuggestionChip(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BackgroundSecondary
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = WarningYellow,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}

// ==================== 用户消息气泡 ====================
@Composable
private fun UserMessageBubble(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandBlue)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                fontSize = 15.sp
            )
        }
    }
}

// ==================== AI消息气泡 ====================
@Composable
private fun AiMessageBubble(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AIGradient, CircleShape),
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
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

// ==================== AI输入中指示器 ====================
@Composable
private fun AiTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AIGradient, CircleShape),
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
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(16.dp, 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TextTertiary, CircleShape)
                    )
                }
            }
        }
    }
}

// ==================== 个人中心页面 ====================
@Composable
fun UserProfileScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val userInfo by viewModel?.userInfo?.collectAsState() ?: remember { mutableStateOf(null) }
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 头部个人信息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.8f))
                    )
                )
        ) {
            // 装饰
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundSecondary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = userInfo?.userName ?: viewModel?.getUserName() ?: "用户",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isProfessional) Icons.Rounded.LocalShipping 
                                     else Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isProfessional) "货运司机" else "私家车主",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        // 功能列表
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 账户设置组
            Text(
                text = "账户设置",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Person,
                title = "个人资料",
                subtitle = "修改头像、昵称",
                onClick = { }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Security,
                title = "账号安全",
                subtitle = "密码、手机号",
                onClick = { }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Notifications,
                title = "消息通知",
                subtitle = "推送设置",
                onClick = { }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 其他设置组
            Text(
                text = "其他",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Help,
                title = "帮助中心",
                subtitle = "常见问题、使用指南",
                onClick = { }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Info,
                title = "关于我们",
                subtitle = "版本 1.0.0",
                onClick = { }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Settings,
                title = "系统设置",
                subtitle = "通用设置",
                onClick = { navController.navigate("settings") }
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ==================== 个人中心菜单项 ====================
@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(BackgroundSecondary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

// ==================== 设置页面 ====================
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    
    var darkMode by remember { mutableStateOf(false) }
    var autoUpdate by remember { mutableStateOf(true) }
    var locationService by remember { mutableStateOf(true) }
    
    DetailScreenTemplate(
        navController = navController,
        title = "系统设置",
        backgroundColor = BackgroundPrimary
    ) {
        // 通用设置
        Text(
            text = "通用设置",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        SettingsCard {
            SettingsToggleItem(
                icon = Icons.Rounded.DarkMode,
                title = "深色模式",
                subtitle = "夜间使用更护眼",
                checked = darkMode,
                onCheckedChange = { darkMode = it }
            )
            
            HorizontalDivider(color = DividerColor)
            
            SettingsToggleItem(
                icon = Icons.Rounded.Update,
                title = "自动更新",
                subtitle = "WiFi下自动更新应用",
                checked = autoUpdate,
                onCheckedChange = { autoUpdate = it }
            )
            
            HorizontalDivider(color = DividerColor)
            
            SettingsToggleItem(
                icon = Icons.Rounded.LocationOn,
                title = "位置服务",
                subtitle = "允许获取位置信息",
                checked = locationService,
                onCheckedChange = { locationService = it }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 数据管理
        Text(
            text = "数据管理",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        SettingsCard {
            SettingsClickItem(
                icon = Icons.Rounded.Cached,
                title = "清除缓存",
                subtitle = "当前缓存 128MB",
                onClick = { }
            )
            
            HorizontalDivider(color = DividerColor)
            
            SettingsClickItem(
                icon = Icons.Rounded.Download,
                title = "离线地图",
                subtitle = "管理已下载的地图",
                onClick = { }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 账户操作
        Text(
            text = "账户操作",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        SettingsCard {
            SettingsClickItem(
                icon = Icons.Rounded.SwapHoriz,
                title = "切换账号",
                subtitle = "登录其他账号",
                onClick = { }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 退出登录按钮
        Button(
            onClick = {
                viewModel?.logout()
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ErrorRedLight,
                contentColor = ErrorRed
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "退出登录",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 版本信息
        Text(
            text = "HubLink Navigator v1.0.0",
            fontSize = 12.sp,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== 设置卡片容器 ====================
@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(content = content)
    }
}

// ==================== 设置开关项 ====================
@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandBlue,
                checkedTrackColor = BrandBlueLight
            )
        )
    }
}

// ==================== 设置点击项 ====================
@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}
