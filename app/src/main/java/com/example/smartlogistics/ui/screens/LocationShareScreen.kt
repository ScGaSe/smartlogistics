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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.*
import com.example.smartlogistics.network.*
import com.example.smartlogistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 位置共享页面
 * 支持两种模式：
 * 1. 发起共享模式 (mode = "share") - 分享自己的位置给别人看
 * 2. 查看共享模式 (mode = "view") - 查看别人分享的位置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareScreen(
    navController: NavController,
    mode: String,      // "share" 或 "view"
    tripId: Int? = null,    // 发起共享时的行程ID
    shareId: String? = null // 查看共享时的分享码
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { Repository(context) }

    // ==================== 状态 ====================
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 我的位置
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var myAddress by remember { mutableStateOf("正在获取位置...") }

    // 对方位置（查看模式）
    var otherLocation by remember { mutableStateOf<LatLng?>(null) }
    var lastUpdateTime by remember { mutableStateOf<String?>(null) }

    // 共享信息
    var shareInfo by remember { mutableStateOf<LocationShareResponse?>(null) }
    var shareDetail by remember { mutableStateOf<LocationShareDetail?>(null) }
    var isSharing by remember { mutableStateOf(false) }

    // 停止共享确认对话框
    var showStopDialog by remember { mutableStateOf(false) }

    // 地图相关
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var myMarker by remember { mutableStateOf<Marker?>(null) }
    var otherMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    // WebSocket管理器（使用Mock）
    val webSocketManager = remember {
        if (Repository.USE_LOCAL_MOCK) {
            MockWebSocketManager()
        } else {
            null
        }
    }
    val realWebSocketManager = remember {
        if (!Repository.USE_LOCAL_MOCK) {
            WebSocketManager(repository.getWebSocketBaseUrl(), repository.getToken())
        } else {
            null
        }
    }

    // 位置客户端
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }

    // 权限请求
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            // 权限获取成功，开始定位
            startLocation(context, locationClient) { location ->
                myLocation = LatLng(location.latitude, location.longitude)
                myAddress = location.address ?: "位置已获取"

                // 更新地图
                aMap?.let { map ->
                    updateMyMarker(map, myLocation!!, myMarker) { myMarker = it }

                    // 如果是发起共享模式且正在共享，发送位置
                    if (mode == "share" && isSharing) {
                        if (Repository.USE_LOCAL_MOCK) {
                            webSocketManager?.sendLocation(location.latitude, location.longitude, location.accuracy)
                        } else {
                            realWebSocketManager?.sendLocation(location.latitude, location.longitude, location.accuracy)
                        }
                    }
                }
            }
        }
    }

    // 收集WebSocket位置更新（查看模式）
    LaunchedEffect(mode) {
        if (mode == "view") {
            val wsManager = if (Repository.USE_LOCAL_MOCK) webSocketManager else realWebSocketManager

            wsManager?.let { manager ->
                when (manager) {
                    is MockWebSocketManager -> {
                        manager.locationUpdates.collect { locationMsg ->
                            otherLocation = LatLng(locationMsg.latitude, locationMsg.longitude)
                            lastUpdateTime = locationMsg.timestamp

                            // 更新地图上的对方标记
                            aMap?.let { map ->
                                updateOtherMarker(map, otherLocation!!, otherMarker) { otherMarker = it }

                                // 如果两个位置都有，绘制路线
                                if (myLocation != null && otherLocation != null) {
                                    drawRoute(map, myLocation!!, otherLocation!!, routePolyline) { routePolyline = it }
                                }
                            }
                        }
                    }
                    is WebSocketManager -> {
                        manager.locationUpdates.collect { locationMsg ->
                            otherLocation = LatLng(locationMsg.latitude, locationMsg.longitude)
                            lastUpdateTime = locationMsg.timestamp

                            aMap?.let { map ->
                                updateOtherMarker(map, otherLocation!!, otherMarker) { otherMarker = it }

                                if (myLocation != null && otherLocation != null) {
                                    drawRoute(map, myLocation!!, otherLocation!!, routePolyline) { routePolyline = it }
                                }
                            }
                        }
                    }
                    else -> { /* 其他类型不处理 */ }
                }
            }
        }
    }

    // 初始化
    LaunchedEffect(Unit) {
        // 检查并请求位置权限
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // 已有权限
            }
            else -> {
                locationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }

        // 根据模式初始化
        when (mode) {
            "share" -> {
                // 发起共享模式：调用API创建共享
                tripId?.let { id ->
                    isLoading = true
                    when (val result = repository.createLocationShare(id)) {
                        is NetworkResult.Success -> {
                            shareInfo = result.data
                            isSharing = true

                            // 连接WebSocket开始上报位置
                            if (Repository.USE_LOCAL_MOCK) {
                                webSocketManager?.connect(result.data.shareId)
                            } else {
                                realWebSocketManager?.connect(result.data.shareId)
                            }
                        }
                        is NetworkResult.Error -> {
                            errorMessage = result.message
                        }
                        is NetworkResult.Exception -> {
                            errorMessage = "网络错误: ${result.throwable.message}"
                        }
                        else -> {}
                    }
                    isLoading = false
                }
            }
            "view" -> {
                // 查看共享模式：获取共享详情并连接WebSocket
                // 统一转小写，确保与发起者在同一channel
                shareId?.lowercase()?.let { id ->
                    isLoading = true
                    when (val result = repository.getLocationShareDetail(id)) {
                        is NetworkResult.Success -> {
                            shareDetail = result.data

                            // 连接WebSocket接收位置
                            if (Repository.USE_LOCAL_MOCK) {
                                webSocketManager?.connect(id)
                            } else {
                                realWebSocketManager?.connect(id)
                            }
                        }
                        is NetworkResult.Error -> {
                            errorMessage = result.message
                        }
                        is NetworkResult.Exception -> {
                            errorMessage = "网络错误: ${result.throwable.message}"
                        }
                        else -> {}
                    }
                    isLoading = false
                }
            }
        }
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
            webSocketManager?.release()
            realWebSocketManager?.release()
            mapView?.onDestroy()
        }
    }

    // ==================== UI ====================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (mode == "share") "共享实时位置" else "查看位置",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSharing && mode == "share") {
                            showStopDialog = true
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (mode == "share" && isSharing) {
                        TextButton(onClick = { showStopDialog = true }) {
                            Text("停止共享", color = Color(0xFFE53935))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 地图
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(null)
                        mapView = this
                        aMap = this.map.apply {
                            uiSettings.apply {
                                isZoomControlsEnabled = false
                                isMyLocationButtonEnabled = false
                                isCompassEnabled = true
                            }

                            // 设置初始位置（长沙）
                            moveCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(28.194, 113.005), 14f
                            ))
                        }

                        // 初始化定位
                        initLocationClient(ctx) { client ->
                            locationClient = client

                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                startLocation(ctx, client) { location ->
                                    myLocation = LatLng(location.latitude, location.longitude)
                                    myAddress = location.address ?: "位置已获取"

                                    this.map.let { map ->
                                        updateMyMarker(map, myLocation!!, myMarker) { myMarker = it }
                                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation!!, 15f))
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 底部信息卡片
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 加载中
                if (isLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CarGreen)
                        }
                    }
                }
                // 错误信息
                else if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage!!,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = ButtonDefaults.buttonColors(containerColor = CarGreen)
                            ) {
                                Text("返回")
                            }
                        }
                    }
                }
                // 发起共享模式 - 显示分享码
                else if (mode == "share" && shareInfo != null) {
                    ShareModeCard(
                        shareInfo = shareInfo!!,
                        myAddress = myAddress,
                        context = context
                    )
                }
                // 查看共享模式 - 显示对方信息
                else if (mode == "view" && shareDetail != null) {
                    ViewModeCard(
                        shareDetail = shareDetail!!,
                        otherLocation = otherLocation,
                        myLocation = myLocation,
                        lastUpdateTime = lastUpdateTime,
                        onNavigate = {
                            // 调用高德导航
                            otherLocation?.let { dest ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setPackage("com.autonavi.minimap")
                                    data = android.net.Uri.parse(
                                        "amapuri://route/plan/?dlat=${dest.latitude}&dlon=${dest.longitude}&dname=对方位置&dev=0&t=0"
                                    )
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "请安装高德地图", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // 停止共享确认对话框
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("停止共享", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要停止位置共享吗？对方将无法再看到你的位置。") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        scope.launch {
                            tripId?.let { id ->
                                repository.stopLocationShare(id)
                            }
                            webSocketManager?.disconnect()
                            realWebSocketManager?.disconnect()
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("停止共享")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("继续共享", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * 发起共享模式卡片
 */
@Composable
private fun ShareModeCard(
    shareInfo: LocationShareResponse,
    myAddress: String,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 状态指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在共享位置",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 分享码
            Text(
                text = "分享码",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shareInfo.shareId,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("分享码", shareInfo.shareId))
                        Toast.makeText(context, "分享码已复制", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "复制",
                        tint = CarGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 我的位置
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = CarGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = myAddress,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            // 有效期
            shareInfo.expiredAt?.let { expired ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "有效期至: ${expired.take(16).replace("T", " ")}",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 分享按钮
            Button(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "我正在共享实时位置，请打开智慧物流APP，输入分享码：${shareInfo.shareId} 查看我的位置")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "分享给好友"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CarGreen)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("发送给好友")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 让对方打开APP → 我的行程 → 加入位置共享，输入分享码即可",
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 查看共享模式卡片
 */
@Composable
private fun ViewModeCard(
    shareDetail: LocationShareDetail,
    otherLocation: LatLng?,
    myLocation: LatLng?,
    lastUpdateTime: String?,
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 对方信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shareDetail.ownerName ?: "对方",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (otherLocation != null) SuccessGreen else Color.Gray,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (otherLocation != null) "位置更新中" else "等待位置...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 行程信息
            shareDetail.tripInfo?.let { trip ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CarGreen.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (trip.tripType == "flight") Icons.Rounded.Flight else Icons.Rounded.Train,
                        contentDescription = null,
                        tint = CarGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${trip.tripNumber} · ${trip.tripDate}",
                        color = CarGreen,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 距离和预计时间
            if (myLocation != null && otherLocation != null) {
                val distance = calculateDistance(myLocation, otherLocation)
                val estimatedTime = (distance / 500).toInt() // 简单估算，假设500米/分钟

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDistance(distance),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "距离",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(DividerColor)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${estimatedTime.coerceAtLeast(1)}分钟",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "预计到达",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 最后更新时间
            lastUpdateTime?.let { time ->
                Text(
                    text = "最后更新: ${time.take(19).replace("T", " ")}",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 导航按钮
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                enabled = otherLocation != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CarGreen)
            ) {
                Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("导航前往")
            }
        }
    }
}

// ==================== 辅助函数 ====================

private fun initLocationClient(context: Context, onCreated: (AMapLocationClient) -> Unit) {
    try {
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)

        val client = AMapLocationClient(context)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = 3000
            isNeedAddress = true
        }
        client.setLocationOption(option)
        onCreated(client)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun startLocation(
    context: Context,
    client: AMapLocationClient?,
    onLocation: (AMapLocation) -> Unit
) {
    client?.setLocationListener { location ->
        if (location != null && location.errorCode == 0) {
            onLocation(location)
        }
    }
    client?.startLocation()
}

private fun updateMyMarker(
    map: AMap,
    location: LatLng,
    existingMarker: Marker?,
    onMarkerCreated: (Marker) -> Unit
) {
    existingMarker?.remove()

    val marker = map.addMarker(MarkerOptions()
        .position(location)
        .title("我的位置")
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
    )
    onMarkerCreated(marker)
}

private fun updateOtherMarker(
    map: AMap,
    location: LatLng,
    existingMarker: Marker?,
    onMarkerCreated: (Marker) -> Unit
) {
    existingMarker?.remove()

    val marker = map.addMarker(MarkerOptions()
        .position(location)
        .title("对方位置")
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
    )
    onMarkerCreated(marker)

    // 调整地图视野包含两个点
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 14f))
}

private fun drawRoute(
    map: AMap,
    start: LatLng,
    end: LatLng,
    existingPolyline: Polyline?,
    onPolylineCreated: (Polyline) -> Unit
) {
    existingPolyline?.remove()

    // 简单的直线连接（实际可以调用高德路线规划API）
    val polyline = map.addPolyline(PolylineOptions()
        .add(start, end)
        .width(8f)
        .color(0xFF4CAF50.toInt())
        .setDottedLine(true)
    )
    onPolylineCreated(polyline)

    // 调整视野包含两个点
    val boundsBuilder = LatLngBounds.Builder()
    boundsBuilder.include(start)
    boundsBuilder.include(end)
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
}

private fun calculateDistance(start: LatLng, end: LatLng): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        start.latitude, start.longitude,
        end.latitude, end.longitude,
        results
    )
    return results[0].toDouble()
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format("%.1fkm", meters / 1000)
    } else {
        "${meters.toInt()}m"
    }
}