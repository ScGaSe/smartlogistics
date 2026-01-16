package com.example.smartlogistics.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel

/**
 * 位置共享页面 - 优化版
 * 支持接人/送人模式，使用高德地图URI分享位置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    
    // 判断当前模式
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    val primaryColorLight = if (isProfessional) TruckOrangeLight else CarGreenLight
    val gradientColors = if (isProfessional) {
        listOf(TruckOrange, Color(0xFFFF8A50))
    } else {
        listOf(CarGreen, Color(0xFF4ECDC4))
    }
    
    // 位置权限状态
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    
    // 共享模式
    var shareMode by remember { mutableStateOf(ShareMode.PICK_UP) }
    
    // 位置状态
    var currentLocation by remember { mutableStateOf<AMapLocation?>(null) }
    var isLocating by remember { mutableStateOf(true) }
    var locationError by remember { mutableStateOf<String?>(null) }
    
    // 定位客户端
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }
    
    // 旋转动画（定位中）
    val infiniteTransition = rememberInfiniteTransition(label = "locating")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // 脉冲动画
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // 初始化定位
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && locationClient == null) {
            try {
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                
                locationClient = AMapLocationClient(context).apply {
                    setLocationOption(AMapLocationClientOption().apply {
                        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                        isOnceLocation = false
                        interval = 3000
                        isNeedAddress = true
                    })
                    setLocationListener { location ->
                        if (location != null && location.errorCode == 0) {
                            currentLocation = location
                            isLocating = false
                            locationError = null
                        } else {
                            locationError = location?.errorInfo ?: "定位失败"
                        }
                    }
                    startLocation()
                }
            } catch (e: Exception) {
                locationError = "定位初始化失败: ${e.message}"
                isLocating = false
            }
        }
    }
    
    // 清理
    DisposableEffect(Unit) {
        onDispose {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        }
    }
    
    // 生成分享链接
    fun generateShareLink(): String {
        if (currentLocation == null) return ""
        val lat = currentLocation!!.latitude
        val lng = currentLocation!!.longitude
        val address = currentLocation!!.address?.replace(" ", "")?.take(30) ?: "我的位置"
        return "https://uri.amap.com/marker?position=$lng,$lat&name=$address&coordinate=gaode&callnative=1"
    }
    
    // 分享位置
    fun shareLocation() {
        val link = generateShareLink()
        if (link.isBlank()) {
            Toast.makeText(context, "正在获取位置，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        
        val modeText = if (shareMode == ShareMode.PICK_UP) "📍 来接我" else "🚗 我在路上"
        val address = currentLocation?.address ?: "当前位置"
        val shareText = """
            |$modeText
            |
            |📌 我在：$address
            |
            |🔗 点击导航：
            |$link
        """.trimMargin()
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "分享位置到"))
    }
    
    // 复制链接
    fun copyLink() {
        val link = generateShareLink()
        if (link.isBlank()) {
            Toast.makeText(context, "正在获取位置，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("位置链接", link)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "✓ 链接已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    // 刷新位置
    fun refreshLocation() {
        isLocating = true
        locationError = null
        locationClient?.startLocation()
    }
    
    Scaffold(
        topBar = {
            // 渐变顶部栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(gradientColors)
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = "位置共享",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    // 刷新按钮
                    IconButton(
                        onClick = { refreshLocation() },
                        enabled = !isLocating
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新位置",
                            tint = Color.White,
                            modifier = if (isLocating) Modifier.rotate(rotationAngle) else Modifier
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundPrimary)
                .verticalScroll(rememberScrollState())
        ) {
            // 权限检查
            if (!hasLocationPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PermissionRequestCard(
                        primaryColor = primaryColor,
                        gradientColors = gradientColors,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
                return@Scaffold
            }
            
            // 位置状态卡片（大卡片）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 位置图标（带动画）
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .then(
                                    if (currentLocation != null && !isLocating) {
                                        Modifier.graphicsLayer(
                                            scaleX = pulseScale,
                                            scaleY = pulseScale
                                        )
                                    } else Modifier
                                )
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = if (currentLocation != null) {
                                            listOf(primaryColor.copy(alpha = 0.2f), primaryColor.copy(alpha = 0.05f))
                                        } else {
                                            listOf(Color.Gray.copy(alpha = 0.2f), Color.Gray.copy(alpha = 0.05f))
                                        }
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .background(
                                        color = if (currentLocation != null) primaryColor.copy(alpha = 0.15f)
                                        else Color.Gray.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLocating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = primaryColor,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (currentLocation != null) Icons.Rounded.LocationOn 
                                                     else Icons.Rounded.LocationOff,
                                        contentDescription = null,
                                        tint = if (currentLocation != null) primaryColor else Color.Gray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 状态文字
                        AnimatedContent(
                            targetState = when {
                                isLocating -> "正在定位..."
                                locationError != null -> "定位失败"
                                currentLocation != null -> "定位成功"
                                else -> "等待定位"
                            },
                            label = "status"
                        ) { status ->
                            Text(
                                text = status,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isLocating -> TextSecondary
                                    locationError != null -> ErrorRed
                                    currentLocation != null -> primaryColor
                                    else -> TextSecondary
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 当前位置地址
                        Text(
                            text = locationError ?: currentLocation?.address ?: "获取位置中...",
                            fontSize = 14.sp,
                            color = if (locationError != null) ErrorRed else TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        // 经纬度显示
                        if (currentLocation != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .background(
                                        color = BackgroundSecondary,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${String.format("%.5f", currentLocation!!.latitude)}, ${String.format("%.5f", currentLocation!!.longitude)}",
                                    fontSize = 12.sp,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }
            
            // 模式选择
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "选择共享模式",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShareModeCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.PersonPinCircle,
                        title = "接人模式",
                        subtitle = "分享位置让TA来找我",
                        isSelected = shareMode == ShareMode.PICK_UP,
                        primaryColor = primaryColor,
                        onClick = { shareMode = ShareMode.PICK_UP }
                    )
                    
                    ShareModeCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.DirectionsCar,
                        title = "送人模式",
                        subtitle = "让TA知道我在路上",
                        isSelected = shareMode == ShareMode.DROP_OFF,
                        primaryColor = primaryColor,
                        onClick = { shareMode = ShareMode.DROP_OFF }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 分享操作区
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // 主分享按钮
                Button(
                    onClick = { shareLocation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp),
                    enabled = currentLocation != null && !isLocating
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = if (currentLocation != null && !isLocating) {
                                    Brush.horizontalGradient(gradientColors)
                                } else {
                                    Brush.horizontalGradient(listOf(Color.Gray, Color.Gray.copy(alpha = 0.7f)))
                                },
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (shareMode == ShareMode.PICK_UP) "分享位置 · 来接我" else "分享位置 · 我在路上",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 次要操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.ContentCopy,
                        text = "复制链接",
                        primaryColor = primaryColor,
                        enabled = currentLocation != null,
                        onClick = { copyLink() }
                    )
                    
                    SecondaryActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.NearMe,
                        text = "导航到我",
                        primaryColor = primaryColor,
                        enabled = currentLocation != null,
                        onClick = {
                            // 打开高德地图导航到当前位置
                            val link = generateShareLink()
                            if (link.isNotBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "打开地图失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 提示卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColorLight)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = primaryColor.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用小贴士",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• 链接可在微信、高德地图中直接打开\n• 对方点击后可一键导航到你的位置\n• 位置会定时更新，如需最新位置请刷新后重新分享",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 权限请求卡片
 */
@Composable
private fun PermissionRequestCard(
    primaryColor: Color,
    gradientColors: List<Color>,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = ErrorRed.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOff,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "需要位置权限",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "为了分享您的实时位置给亲友，\n请授权位置访问权限",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(gradientColors),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "授权位置权限",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * 模式选择卡片
 */
@Composable
private fun ShareModeCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.shadow(4.dp, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.White
        ),
        border = if (isSelected) BorderStroke(2.dp, primaryColor) else BorderStroke(1.dp, DividerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isSelected) primaryColor else BackgroundSecondary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) primaryColor else TextPrimary
            )
            
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 次要操作按钮
 */
@Composable
private fun SecondaryActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    primaryColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (enabled) primaryColor else Color.Gray.copy(alpha = 0.3f)
        ),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = primaryColor,
            disabledContentColor = Color.Gray.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 共享模式枚举
 */
enum class ShareMode {
    PICK_UP,   // 接人
    DROP_OFF   // 送人
}
