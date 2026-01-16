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
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * 位置共享页面 - 接人/送人模式
 * 方案A：使用高德地图URI分享当前位置
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
    
    // 定位客户端
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }
    
    // 初始化定位
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && locationClient == null) {
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
                    }
                }
                startLocation()
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
        val address = currentLocation!!.address?.replace(" ", "") ?: "我的位置"
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
            |我在：$address
            |
            |点击查看位置：
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
        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
    }
    
    // 刷新位置
    fun refreshLocation() {
        isLocating = true
        locationClient?.startLocation()
        Toast.makeText(context, "正在刷新位置...", Toast.LENGTH_SHORT).show()
    }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                    
                    Text(
                        text = "位置共享",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    // 刷新按钮
                    IconButton(onClick = { refreshLocation() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新位置",
                            tint = primaryColor
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限检查
            if (!hasLocationPermission) {
                PermissionRequestCard(
                    primaryColor = primaryColor,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
                return@Scaffold
            }
            
            // 模式选择
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择模式",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShareModeButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.PersonPinCircle,
                            title = "接人",
                            subtitle = "分享位置让TA来接",
                            isSelected = shareMode == ShareMode.PICK_UP,
                            primaryColor = primaryColor,
                            onClick = { shareMode = ShareMode.PICK_UP }
                        )
                        
                        ShareModeButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.DirectionsCar,
                            title = "送人",
                            subtitle = "让TA知道你在路上",
                            isSelected = shareMode == ShareMode.DROP_OFF,
                            primaryColor = primaryColor,
                            onClick = { shareMode = ShareMode.DROP_OFF }
                        )
                    }
                }
            }
            
            // 当前位置卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 位置图标
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = if (currentLocation != null) primaryColor.copy(alpha = 0.1f) 
                                       else Color.Gray.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = primaryColor,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = if (currentLocation != null) primaryColor else Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 状态文字
                    Text(
                        text = if (isLocating) "正在定位..." else "定位成功",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (currentLocation != null) primaryColor else TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 当前位置地址
                    Text(
                        text = currentLocation?.address ?: "获取位置中...",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    
                    // 经纬度显示
                    if (currentLocation != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "经纬度: ${String.format("%.6f", currentLocation!!.longitude)}, ${String.format("%.6f", currentLocation!!.latitude)}",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
            }
            
            // 分享操作卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "分享给亲友",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 主分享按钮
                    Button(
                        onClick = { shareLocation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        enabled = currentLocation != null
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "分享我的位置",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 次要操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyLink() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, primaryColor),
                            enabled = currentLocation != null
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "复制链接", fontSize = 14.sp, color = primaryColor)
                        }
                        
                        OutlinedButton(
                            onClick = { refreshLocation() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, primaryColor)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "刷新位置", fontSize = 14.sp, color = primaryColor)
                        }
                    }
                }
            }
            
            // 提示信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColorLight)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "使用提示",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 分享的链接可在高德地图中打开\n• 对方可直接导航到你的位置\n• 如需更新位置，请点击刷新后重新分享",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 权限请求卡片
 */
@Composable
private fun PermissionRequestCard(
    primaryColor: Color,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOff,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "需要位置权限",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = "请授权位置权限以使用位置共享功能",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("授权位置权限")
            }
        }
    }
}

/**
 * 模式选择按钮
 */
@Composable
private fun ShareModeButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) primaryColor.copy(alpha = 0.1f) else BackgroundPrimary,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.5.dp, primaryColor) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) primaryColor else TextSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) primaryColor else TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 共享模式
 */
enum class ShareMode {
    PICK_UP,   // 接人
    DROP_OFF   // 送人
}
