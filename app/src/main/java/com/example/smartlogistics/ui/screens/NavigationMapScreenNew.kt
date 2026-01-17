package com.example.smartlogistics.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.*
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.network.TrafficWebSocket

// ==================== 导航步骤数据类 ====================
data class NavigationStep(
    val instruction: String,
    val distance: Int,          // 米
    val duration: Int,          // 秒
    val action: String,         // 动作类型：直行、左转、右转、到达等
    val roadName: String,
    val polylinePoints: List<LatLng>
)

// ==================== 导航状态 ====================
enum class NavigationMode {
    IDLE,           // 空闲（未导航）
    NAVIGATING,     // 导航中
    ARRIVED,        // 已到达
    PAUSED          // 暂停
}

// ==================== 导航页面（合包SDK版） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMapScreenNew(
    navController: NavController,
    viewModel: MainViewModel? = null,
    initialDestination: String = ""
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 地图相关
    var mapView by remember { mutableStateOf<TextureMapView?>(null) }
    var aMap by remember { mutableStateOf<AMap?>(null) }

    // 定位相关
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }

    // 搜索相关
    var searchQuery by remember { mutableStateOf(initialDestination) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedPoi by remember { mutableStateOf<PoiItem?>(null) }
    // ⭐ 直接目的地（用于停车助手等直接传坐标的场景）
    var directDestinationName by remember { mutableStateOf<String?>(null) }
    var directDestinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var hasTriedDirectRoute by remember { mutableStateOf(false) }  // ⭐ 防止重复触发

    var pendingDestination by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }

    // 路线相关
    var routePaths by remember { mutableStateOf<List<DrivePath>>(emptyList()) }
    var selectedRouteIndex by remember { mutableStateOf(0) }
    var isLoadingRoute by remember { mutableStateOf(false) }
    var showRouteInfo by remember { mutableStateOf(false) }

    // 地图覆盖物
    var currentMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var currentPolylines by remember { mutableStateOf<List<Polyline>>(emptyList()) }

    // UI状态
    var showTraffic by remember { mutableStateOf(true) }
    var isSearchExpanded by remember { mutableStateOf(true) }

    // ==================== 动态POI图层状态 ====================
    var showPoiLayer by remember { mutableStateOf(false) }
    var poiMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var isLoadingPoi by remember { mutableStateOf(false) }
    var selectedPoiType by remember { mutableStateOf<String?>(null) }  // 当前选中的POI类型
    var selectedPoiDetail by remember { mutableStateOf<PoiItem?>(null) }  // 点击的POI详情
    var showPoiDetailCard by remember { mutableStateOf(false) }  // 是否显示POI详情卡片

    // ==================== 模拟导航状态 ====================
    var navigationMode by remember { mutableStateOf(NavigationMode.IDLE) }
    var navigationSteps by remember { mutableStateOf<List<NavigationStep>>(emptyList()) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var remainingDistance by remember { mutableStateOf(0) }       // 剩余总距离（米）
    var remainingDuration by remember { mutableStateOf(0) }       // 剩余总时间（秒）
    var simulationProgress by remember { mutableStateOf(0f) }     // 当前步骤进度 0-1
    var isFollowingLocation by remember { mutableStateOf(true) }  // ⭐ 是否跟随定位（用户滑动地图后变为false）

    // 模式判断
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    // ==================== 道路实况 WebSocket 状态 ====================
    val trafficWebSocket = remember { TrafficWebSocket.getInstance() }
    val gateQueues by trafficWebSocket.gateQueues.collectAsState()
    val trafficConnectionState by trafficWebSocket.connectionState.collectAsState()
    val lastTrafficUpdate by trafficWebSocket.lastUpdateTime.collectAsState()
    var showGatePanel by remember { mutableStateOf(false) }  // 是否显示闸口面板

    // 权限
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // ⭐ 权限对话框状态
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted

        if (!granted) {
            // 检查是否是永久拒绝（用户选择了"不再询问"）
            permissionDeniedPermanently = true
            showPermissionDialog = true
        }
    }

// 如果没有权限，显示提示让用户点击
    var shouldRequestPermission by remember { mutableStateOf(!hasLocationPermission) }

    if (shouldRequestPermission && !hasLocationPermission) {
        LaunchedEffect(Unit) {
            // 延迟一下再请求，避免 requestCode 问题
            kotlinx.coroutines.delay(100)
        }
    }

    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    // 连接道路实况WebSocket
                    trafficWebSocket.connect()
                    // ⭐ 从设置返回后重新检查权限状态
                    val newPermissionState = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (newPermissionState != hasLocationPermission) {
                        hasLocationPermission = newPermissionState
                        if (newPermissionState) {
                            permissionDeniedPermanently = false
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView?.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    locationClient?.stopLocation()
                    locationClient?.onDestroy()
                    mapView?.onDestroy()
                    // 断开道路实况WebSocket
                    trafficWebSocket.disconnect()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationClient?.stopLocation()
            locationClient?.onDestroy()
            mapView?.onDestroy()
            trafficWebSocket.disconnect()
        }
    }

    // ==================== 权限变化后启动定位 ====================
    // 当权限被授予且地图已初始化时，启动定位
    var hasMovedToLocation by remember { mutableStateOf(false) }  // ⭐ 只移动一次

    LaunchedEffect(hasLocationPermission, aMap) {
        if (hasLocationPermission && aMap != null && locationClient == null) {
            setupLocation(context, aMap!!) { client, location ->
                locationClient = client
                val newLocation = LatLng(location.latitude, location.longitude)
                currentLocation = newLocation

                // ⭐ 只在第一次定位成功后移动地图，之后不再自动移动
                if (!hasMovedToLocation) {
                    aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 15f))
                    hasMovedToLocation = true
                }
            }
        }
    }

    // ==================== 自动处理初始目的地 ====================
    var hasAutoSearched by remember { mutableStateOf(false) }

    // 解析 DIRECT| 格式的目的地
    LaunchedEffect(initialDestination, aMap) {
        if (initialDestination.isNotBlank() && !hasAutoSearched && aMap != null) {

            if (initialDestination.startsWith("DIRECT:::")) {
                val parts = initialDestination.split(":::")
                if (parts.size >= 4) {
                    val destName = parts[1]
                    val lat = parts[2].toDoubleOrNull()
                    val lng = parts[3].toDoubleOrNull()

                    if (lat != null && lng != null) {
                        hasAutoSearched = true
                        directDestinationName = destName
                        directDestinationLatLng = LatLng(lat, lng)
                        searchQuery = destName

                        // 添加终点标记
                        aMap?.let { map ->
                            clearOverlays(currentMarkers, currentPolylines)
                            val marker = map.addMarker(
                                MarkerOptions()
                                    .position(LatLng(lat, lng))
                                    .title(destName)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )
                            currentMarkers = listOf(marker)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
                        }

                        android.util.Log.d("NAV_DEBUG", "解析目的地: $destName ($lat, $lng)")
                        return@LaunchedEffect
                    }
                }
            }

            // 普通地址走原来的POI搜索逻辑...
        }
    }

    // ⭐ 等待定位完成后规划路线（只尝试一次）
    LaunchedEffect(currentLocation, directDestinationLatLng, hasTriedDirectRoute) {
        val destLatLng = directDestinationLatLng
        val destName = directDestinationName

        if (currentLocation != null &&
            destLatLng != null &&
            destName != null &&
            !hasTriedDirectRoute &&
            !isLoadingRoute) {

            hasTriedDirectRoute = true
            isLoadingRoute = true

            android.util.Log.d("NAV_DEBUG", "开始规划路线:")
            android.util.Log.d("NAV_DEBUG", "  起点: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
            android.util.Log.d("NAV_DEBUG", "  终点: ${destLatLng.latitude}, ${destLatLng.longitude}")

            searchDriveRoute(
                context = context,
                start = LatLonPoint(currentLocation!!.latitude, currentLocation!!.longitude),
                end = LatLonPoint(destLatLng.latitude, destLatLng.longitude)
            ) { paths ->
                isLoadingRoute = false

                if (paths.isNotEmpty()) {
                    routePaths = paths
                    selectedRouteIndex = 0
                    showRouteInfo = true

                    aMap?.let { map ->
                        clearOverlays(currentMarkers, currentPolylines)
                        currentPolylines = drawAllRoutes(map, paths, 0, isProfessional)

                        val startMarker = map.addMarker(
                            MarkerOptions()
                                .position(currentLocation)
                                .title("起点")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        )
                        val endMarker = map.addMarker(
                            MarkerOptions()
                                .position(destLatLng)
                                .title(destName)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                        currentMarkers = listOf(startMarker, endMarker)
                        zoomToRoute(map, paths[0])
                    }
                } else {
                    Toast.makeText(context, "路线规划失败，请手动搜索目的地", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 原有的POI搜索逻辑
    LaunchedEffect(initialDestination, currentLocation, aMap) {
        // 只处理普通地址（不带DIRECT|前缀）
        if (initialDestination.isNotBlank() &&
            !initialDestination.startsWith("DIRECT:::") &&
            !hasAutoSearched &&
            currentLocation != null &&
            aMap != null) {

            hasAutoSearched = true
            isSearching = true
            searchPoi(context, initialDestination) { results ->
                isSearching = false
                searchResults = results

                if (results.isNotEmpty()) {
                    val firstPoi = results.first()
                    selectedPoi = firstPoi
                    searchQuery = firstPoi.title
                    showSearchResults = false

                    aMap?.let { map ->
                        clearOverlays(currentMarkers, currentPolylines)
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(LatLng(firstPoi.latLonPoint.latitude, firstPoi.latLonPoint.longitude))
                                .title(firstPoi.title)
                                .snippet(firstPoi.snippet)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                        currentMarkers = listOf(marker)
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(firstPoi.latLonPoint.latitude, firstPoi.latLonPoint.longitude),
                                15f
                            )
                        )
                    }

                    isLoadingRoute = true
                    searchDriveRoute(
                        context = context,
                        start = LatLonPoint(currentLocation!!.latitude, currentLocation!!.longitude),
                        end = firstPoi.latLonPoint
                    ) { paths ->
                        isLoadingRoute = false
                        if (paths.isNotEmpty()) {
                            routePaths = paths
                            selectedRouteIndex = 0
                            showRouteInfo = true

                            aMap?.let { map ->
                                clearOverlays(currentMarkers, currentPolylines)
                                currentPolylines = drawAllRoutes(map, paths, 0, isProfessional)

                                val startMarker = map.addMarker(
                                    MarkerOptions()
                                        .position(currentLocation)
                                        .title("起点")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                                )
                                val endMarker = map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(firstPoi.latLonPoint.latitude, firstPoi.latLonPoint.longitude))
                                        .title(firstPoi.title)
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                )
                                currentMarkers = listOf(startMarker, endMarker)
                                zoomToRoute(map, paths[0])
                            }

                            if (paths.size > 1) {
                                Toast.makeText(context, "找到 ${paths.size} 条路线", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "路线规划失败，请手动搜索", Toast.LENGTH_SHORT).show()
                            showSearchResults = true
                        }
                    }
                } else {
                    Toast.makeText(context, "未找到目的地，请手动搜索", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 地图 ====================
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureMapView(ctx).apply {
                    mapView = this
                    onCreate(Bundle())

                    aMap = map.also { mapObj ->
                        mapObj.uiSettings.apply {
                            isZoomControlsEnabled = false
                            isCompassEnabled = true
                            isMyLocationButtonEnabled = false
                            isScaleControlsEnabled = true
                        }

                        mapObj.isTrafficEnabled = showTraffic

                        if (hasLocationPermission) {
                            setupLocation(ctx, mapObj) { client, location ->
                                locationClient = client
                                val newLocation = LatLng(location.latitude, location.longitude)
                                currentLocation = newLocation

                                // ===== 真实导航模式：根据GPS位置更新导航状态 =====
                                if (navigationMode == NavigationMode.NAVIGATING && navigationSteps.isNotEmpty()) {
                                    updateNavigationState(
                                        currentPosition = newLocation,
                                        steps = navigationSteps,
                                        map = mapObj,
                                        isFollowing = isFollowingLocation,  // ⭐ 传入跟随状态
                                        onStateUpdate = { stepIndex, progress, remainDist, remainTime ->
                                            currentStepIndex = stepIndex
                                            simulationProgress = progress
                                            remainingDistance = remainDist
                                            remainingDuration = remainTime
                                        },
                                        onArrived = {
                                            navigationMode = NavigationMode.ARRIVED
                                            remainingDistance = 0
                                            remainingDuration = 0
                                        }
                                    )
                                }
                            }
                        }

                        mapObj.setOnMapClickListener { showSearchResults = false }

                        // ⭐ 监听地图触摸，用户手动滑动时关闭跟随模式
                        mapObj.setOnMapTouchListener { event ->
                            // 用户触摸移动地图时，关闭跟随模式
                            if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                                isFollowingLocation = false
                            }
                        }
                    }
                }
            },
            update = { _ -> aMap?.isTrafficEnabled = showTraffic }
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
                        // 标题行
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导航", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { isSearchExpanded = false }) {
                                Icon(Icons.Default.KeyboardArrowUp, "收起", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 起点
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(primaryColor, CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (currentLocation != null) "我的位置 (已定位)" else "我的位置 (定位中...)",
                                fontSize = 15.sp,
                                color = if (currentLocation != null) TextPrimary else TextSecondary
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
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (selectedPoi != null) {
                                    selectedPoi = null
                                    showRouteInfo = false
                                    routePaths = emptyList()
                                    clearOverlays(currentMarkers, currentPolylines)
                                    currentMarkers = emptyList()
                                    currentPolylines = emptyList()
                                }
                            },
                            placeholder = { Text("输入目的地...", color = TextTertiary) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFFEA4335), CircleShape))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        showSearchResults = false
                                        selectedPoi = null
                                        showRouteInfo = false
                                        routePaths = emptyList()
                                        clearOverlays(currentMarkers, currentPolylines)
                                        currentMarkers = emptyList()
                                        currentPolylines = emptyList()
                                    }) {
                                        Icon(Icons.Default.Clear, "清除", tint = TextSecondary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = BorderLight,
                                unfocusedContainerColor = BackgroundSecondary,
                                focusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )

                        // 搜索按钮
                        if (searchQuery.isNotBlank() && selectedPoi == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    isSearching = true
                                    searchPoi(context, searchQuery) { results ->
                                        isSearching = false
                                        searchResults = results
                                        showSearchResults = results.isNotEmpty()
                                        if (results.isEmpty()) {
                                            Toast.makeText(context, "未找到相关地点", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                enabled = !isSearching
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, null, Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSearching) "搜索中..." else "搜索")
                            }
                        }

                        // 规划路线按钮
                        if (selectedPoi != null && currentLocation != null && !showRouteInfo) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    isLoadingRoute = true
                                    searchDriveRoute(
                                        context = context,
                                        start = LatLonPoint(currentLocation!!.latitude, currentLocation!!.longitude),
                                        end = selectedPoi!!.latLonPoint
                                    ) { paths ->
                                        isLoadingRoute = false
                                        if (paths.isNotEmpty()) {
                                            routePaths = paths
                                            selectedRouteIndex = 0
                                            showRouteInfo = true

                                            // 绘制所有路线（选中的高亮，其他灰色）
                                            aMap?.let { map ->
                                                clearOverlays(currentMarkers, currentPolylines)
                                                currentPolylines = drawAllRoutes(map, paths, 0, isProfessional)

                                                // 添加起终点标记
                                                val startMarker = map.addMarker(
                                                    MarkerOptions()
                                                        .position(currentLocation)
                                                        .title("起点")
                                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                                                )
                                                val endMarker = map.addMarker(
                                                    MarkerOptions()
                                                        .position(LatLng(selectedPoi!!.latLonPoint.latitude, selectedPoi!!.latLonPoint.longitude))
                                                        .title(selectedPoi!!.title)
                                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                                )
                                                currentMarkers = listOf(startMarker, endMarker)

                                                // 缩放到显示整条路线
                                                zoomToRoute(map, paths[0])
                                            }

                                            // 显示找到的路线数量
                                            if (paths.size > 1) {
                                                Toast.makeText(context, "找到 ${paths.size} 条路线", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "路线规划失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                enabled = !isLoadingRoute
                            ) {
                                if (isLoadingRoute) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Send, null, Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isLoadingRoute) "规划中..." else "规划路线")
                            }
                        }
                    }
                }

                // 搜索结果
                AnimatedVisibility(
                    visible = showSearchResults && searchResults.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn {
                            items(searchResults) { poi ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPoi = poi
                                            searchQuery = poi.title
                                            showSearchResults = false

                                            aMap?.let { map ->
                                                clearOverlays(currentMarkers, currentPolylines)
                                                val marker = map.addMarker(
                                                    MarkerOptions()
                                                        .position(LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude))
                                                        .title(poi.title)
                                                        .snippet(poi.snippet)
                                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                                )
                                                currentMarkers = listOf(marker)
                                                map.animateCamera(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude), 15f
                                                    )
                                                )
                                            }
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.LocationOn, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(poi.title ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(poi.snippet ?: poi.cityName ?: "", fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (poi.distance > 0) {
                                        Text(formatDistance(poi.distance), fontSize = 12.sp, color = TextTertiary)
                                    }
                                }
                                if (poi != searchResults.last()) {
                                    HorizontalDivider(color = DividerColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 展开按钮
        if (!isSearchExpanded) {
            FloatingActionButton(
                onClick = { isSearchExpanded = true },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding(),
                containerColor = Color.White,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Search, "搜索")
            }
        }

        // ==================== 右侧按钮（高德风格） ====================
        AnimatedVisibility(
            visible = navigationMode == NavigationMode.IDLE,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier.padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 路况按钮
                Surface(
                    onClick = { showTraffic = !showTraffic },
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "路况",
                            tint = if (showTraffic) primaryColor else TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // POI图层按钮
                Surface(
                    onClick = {
                        showPoiLayer = !showPoiLayer
                        if (!showPoiLayer) {
                            poiMarkers.forEach { it.remove() }
                            poiMarkers = emptyList()
                            selectedPoiType = null
                            showPoiDetailCard = false
                            selectedPoiDetail = null
                        }
                    },
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "POI",
                            tint = if (showPoiLayer) primaryColor else TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 定位按钮（同心圆样式）
                Surface(
                    onClick = {
                        if (hasLocationPermission) {
                            // 有权限，直接定位
                            currentLocation?.let { loc ->
                                aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                            } ?: run {
                                Toast.makeText(context, "正在获取位置...", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // 没有权限，请求权限或显示对话框
                            if (permissionDeniedPermanently) {
                                showPermissionDialog = true
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // 同心圆图标，颜色跟随主题，大小与其他图标一致
                        Canvas(modifier = Modifier.size(26.dp)) {
                            val strokeWidth = 2.dp.toPx()
                            // 外圈（空心）- 半径为画布一半减去线宽一半
                            drawCircle(
                                color = if (hasLocationPermission) primaryColor else TextSecondary,
                                radius = (size.minDimension - strokeWidth) / 2,
                                style = Stroke(width = strokeWidth)
                            )
                            // 内圈（实心）- 更大的内圆
                            drawCircle(
                                color = if (hasLocationPermission) primaryColor else TextSecondary,
                                radius = size.minDimension / 4
                            )
                        }
                        // 无权限时显示警告标记
                        if (!hasLocationPermission) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(10.dp)
                                    .background(Color(0xFFE57373), CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ⭐ 闸口实况按钮（WebSocket实时数据）
                Surface(
                    onClick = { showGatePanel = !showGatePanel },
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Traffic,
                            contentDescription = "闸口实况",
                            tint = if (showGatePanel) primaryColor else TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                        // 连接状态指示器
                        if (trafficConnectionState == TrafficWebSocket.ConnectionState.CONNECTED) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                        } else if (trafficConnectionState == TrafficWebSocket.ConnectionState.CONNECTING ||
                            trafficConnectionState == TrafficWebSocket.ConnectionState.RECONNECTING) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(Color(0xFFFFC107), CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // ==================== POI类型选择器 ====================
        // 存储当前搜索到的POI列表
        var currentPoiList by remember { mutableStateOf<List<PoiItem>>(emptyList()) }

        AnimatedVisibility(
            visible = showPoiLayer && navigationMode == NavigationMode.IDLE && !showPoiDetailCard,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 70.dp)
                .statusBarsPadding()
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 根据模式显示不同的POI类型（无标题）
                    val poiTypes = if (isProfessional) {
                        listOf(
                            "加油站" to Icons.Default.LocationOn,
                            "货运站" to Icons.Default.Place,
                            "仓库" to Icons.Default.Home,
                            "停车场" to Icons.Default.Place
                        )
                    } else {
                        listOf(
                            "停车场" to Icons.Default.Place,
                            "加油站" to Icons.Default.LocationOn,
                            "餐厅" to Icons.Default.Favorite,
                            "充电站" to Icons.Default.Star
                        )
                    }

                    poiTypes.forEach { (typeName, icon) ->
                        val isSelected = selectedPoiType == typeName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        // 取消选择，清除标记
                                        selectedPoiType = null
                                        poiMarkers.forEach { it.remove() }
                                        poiMarkers = emptyList()
                                        currentPoiList = emptyList()
                                    } else {
                                        // 选择新类型，搜索POI
                                        selectedPoiType = typeName
                                        showPoiDetailCard = false
                                        selectedPoiDetail = null
                                        currentLocation?.let { loc ->
                                            isLoadingPoi = true
                                            searchNearbyPoi(
                                                context = context,
                                                location = loc,
                                                poiType = typeName,
                                                onResult = { pois ->
                                                    isLoadingPoi = false
                                                    currentPoiList = pois
                                                    // 清除旧标记
                                                    poiMarkers.forEach { it.remove() }
                                                    // 添加新标记
                                                    aMap?.let { map ->
                                                        poiMarkers = addPoiMarkersWithClick(
                                                            map = map,
                                                            pois = pois,
                                                            poiType = typeName,
                                                            isProfessional = isProfessional,
                                                            onPoiClick = { poi ->
                                                                selectedPoiDetail = poi
                                                                showPoiDetailCard = true
                                                            }
                                                        )
                                                    }
                                                    if (pois.isEmpty()) {
                                                        Toast.makeText(context, "附近没有找到$typeName", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "找到 ${pois.size} 个$typeName", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        } ?: Toast.makeText(context, "请等待定位完成", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .background(
                                    if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) primaryColor else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = typeName,
                                fontSize = 14.sp,
                                color = if (isSelected) primaryColor else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                            if (isLoadingPoi && isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = primaryColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== 闸口实况面板（WebSocket实时数据）====================
        AnimatedVisibility(
            visible = showGatePanel && navigationMode == NavigationMode.IDLE,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 70.dp)
                .statusBarsPadding()
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.widthIn(min = 180.dp, max = 220.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚗 闸口实况",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        // 连接状态
                        val statusText = when (trafficConnectionState) {
                            TrafficWebSocket.ConnectionState.CONNECTED -> "实时"
                            TrafficWebSocket.ConnectionState.CONNECTING -> "连接中..."
                            TrafficWebSocket.ConnectionState.RECONNECTING -> "重连中..."
                            TrafficWebSocket.ConnectionState.DISCONNECTED -> "离线"
                        }
                        val statusColor = when (trafficConnectionState) {
                            TrafficWebSocket.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            TrafficWebSocket.ConnectionState.CONNECTING,
                            TrafficWebSocket.ConnectionState.RECONNECTING -> Color(0xFFFFC107)
                            TrafficWebSocket.ConnectionState.DISCONNECTED -> Color(0xFFE57373)
                        }
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = DividerColor)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 闸口列表
                    if (gateQueues.isEmpty()) {
                        Text(
                            text = if (trafficConnectionState == TrafficWebSocket.ConnectionState.CONNECTED)
                                "暂无数据" else "等待连接...",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // 按排队数量排序显示
                        val sortedGates = gateQueues.entries.sortedBy { it.value }
                        sortedGates.forEach { (gateId, queueCount) ->
                            val gateStatus = trafficWebSocket.getGateStatus(queueCount)
                            val statusColor = when (gateStatus) {
                                TrafficWebSocket.GateStatus.SMOOTH -> Color(0xFF4CAF50)    // 绿色-畅通
                                TrafficWebSocket.GateStatus.NORMAL -> Color(0xFF8BC34A)    // 浅绿-正常
                                TrafficWebSocket.GateStatus.BUSY -> Color(0xFFFFC107)      // 黄色-繁忙
                                TrafficWebSocket.GateStatus.CONGESTED -> Color(0xFFE57373) // 红色-拥堵
                            }
                            val statusText = when (gateStatus) {
                                TrafficWebSocket.GateStatus.SMOOTH -> "畅通"
                                TrafficWebSocket.GateStatus.NORMAL -> "正常"
                                TrafficWebSocket.GateStatus.BUSY -> "繁忙"
                                TrafficWebSocket.GateStatus.CONGESTED -> "拥堵"
                            }
                            val gateName = when (gateId) {
                                "Gate_N1" -> "北1号闸口"
                                "Gate_N2" -> "北2号闸口"
                                "Gate_S1" -> "南1号闸口"
                                "Gate_S2" -> "南2号闸口"
                                "Gate_E1" -> "东1号闸口"
                                "Gate_E2" -> "东2号闸口"
                                "Gate_W1" -> "西1号闸口"
                                "Gate_W2" -> "西2号闸口"
                                else -> gateId
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = gateName,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 排队数量
                                    Text(
                                        text = "${queueCount}辆",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // 状态标签
                                    Box(
                                        modifier = Modifier
                                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            color = statusColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 推荐闸口
                        val recommendedGate = trafficWebSocket.getRecommendedGate()
                        if (recommendedGate != null && gateQueues.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = DividerColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "推荐: ${recommendedGate.gateName}",
                                    fontSize = 12.sp,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 更新时间
                    lastTrafficUpdate?.let { updateTime ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "更新: ${updateTime.substringAfter("T").substringBefore(".")}",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        // ==================== POI详情卡片 ====================
        AnimatedVisibility(
            visible = showPoiDetailCard && selectedPoiDetail != null && navigationMode == NavigationMode.IDLE,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedPoiDetail?.let { poi ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 顶部：标题和关闭按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = poi.title ?: "未知地点",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = poi.snippet ?: poi.cityName ?: "",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    showPoiDetailCard = false
                                    selectedPoiDetail = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                            }
                        }

                        // 距离信息
                        if (poi.distance > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val distanceText = if (poi.distance >= 1000) {
                                    String.format("%.1f公里", poi.distance / 1000.0)
                                } else {
                                    "${poi.distance}米"
                                }
                                Text(
                                    text = "距您 $distanceText",
                                    fontSize = 14.sp,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 导航按钮
                        Button(
                            onClick = {
                                // 关闭POI详情卡片
                                showPoiDetailCard = false
                                showPoiLayer = false

                                // 清除POI标记
                                poiMarkers.forEach { it.remove() }
                                poiMarkers = emptyList()
                                selectedPoiType = null

                                // 设置目的地并规划路线
                                selectedPoi = poi
                                searchQuery = poi.title ?: ""

                                currentLocation?.let { start ->
                                    isLoadingRoute = true
                                    searchDriveRoute(
                                        context = context,
                                        start = LatLonPoint(start.latitude, start.longitude),
                                        end = poi.latLonPoint
                                    ) { paths ->
                                        isLoadingRoute = false
                                        if (paths.isNotEmpty()) {
                                            routePaths = paths
                                            selectedRouteIndex = 0
                                            showRouteInfo = true

                                            aMap?.let { map ->
                                                clearOverlays(currentMarkers, currentPolylines)
                                                currentPolylines = drawAllRoutes(map, paths, 0, isProfessional)

                                                val startMarker = map.addMarker(
                                                    MarkerOptions()
                                                        .position(start)
                                                        .title("起点")
                                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                                                )
                                                val endMarker = map.addMarker(
                                                    MarkerOptions()
                                                        .position(LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude))
                                                        .title(poi.title)
                                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                                )
                                                currentMarkers = listOf(startMarker, endMarker)

                                                zoomToRoute(map, paths[0])
                                            }
                                        } else {
                                            Toast.makeText(context, "路线规划失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                selectedPoiDetail = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Default.Navigation, null, Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导航到这里", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ==================== 底部路线信息 ====================
        AnimatedVisibility(
            visible = showRouteInfo && routePaths.isNotEmpty() && navigationMode == NavigationMode.IDLE,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedPath = routePaths.getOrNull(selectedRouteIndex)
            if (selectedPath != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
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
                            Text("路线方案", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            IconButton(onClick = {
                                showRouteInfo = false
                                routePaths = emptyList()
                                clearOverlays(currentMarkers, currentPolylines)
                                currentMarkers = emptyList()
                                currentPolylines = emptyList()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 多路线选择（可横向滚动）
                        if (routePaths.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                routePaths.forEachIndexed { index, path ->
                                    val label = getRouteLabel(path, routePaths, index)
                                    val isSelected = index == selectedRouteIndex

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedRouteIndex = index
                                            aMap?.let { map ->
                                                currentPolylines.forEach { it.remove() }
                                                currentPolylines = drawAllRoutes(map, routePaths, index, isProfessional)
                                                zoomToRoute(map, routePaths[index])
                                            }
                                        },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "${formatDuration(path.duration.toInt())}",
                                                    fontSize = 11.sp,
                                                    color = if (isSelected) primaryColor else TextTertiary
                                                )
                                            }
                                        },
                                        leadingIcon = if (label == "推荐") {
                                            { Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = if (isSelected) primaryColor else TextTertiary) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primaryColor.copy(alpha = 0.15f),
                                            selectedLabelColor = primaryColor,
                                            containerColor = BackgroundSecondary,
                                            labelColor = TextSecondary
                                        ),
                                        border = if (isSelected) {
                                            BorderStroke(1.5.dp, primaryColor)
                                        } else {
                                            BorderStroke(1.dp, BorderLight)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 路线信息
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatDistance(selectedPath.distance.toInt()), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                Text("距离", fontSize = 12.sp, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatDuration(selectedPath.duration.toInt()), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                Text("预计时间", fontSize = 12.sp, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val toll = selectedPath.tolls.toInt()
                                Text(if (toll > 0) "¥$toll" else "免费", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                Text("过路费", fontSize = 12.sp, color = TextSecondary)
                            }
                        }

                        // 路线对比信息（当有多条路线时显示）
                        if (routePaths.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))

                            val firstPath = routePaths[0]
                            val timeDiff = selectedPath.duration - firstPath.duration
                            val distDiff = selectedPath.distance - firstPath.distance

                            if (selectedRouteIndex != 0 && (timeDiff != 0L || distDiff != 0f)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundSecondary, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (timeDiff != 0L) {
                                        val timeText = if (timeDiff > 0) "慢${formatDuration(timeDiff.toInt())}" else "快${formatDuration((-timeDiff).toInt())}"
                                        val timeColor = if (timeDiff > 0) ErrorRed else SuccessGreen
                                        Text("比推荐路线$timeText", fontSize = 12.sp, color = timeColor)
                                    }
                                    if (timeDiff != 0L && distDiff != 0f) {
                                        Text(" · ", fontSize = 12.sp, color = TextTertiary)
                                    }
                                    if (distDiff != 0f) {
                                        val distText = if (distDiff > 0) "多${formatDistance(distDiff.toInt())}" else "少${formatDistance((-distDiff).toInt())}"
                                        Text(distText, fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        if (selectedPath.totalTrafficlights > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "途经 ${selectedPath.totalTrafficlights} 个红绿灯",
                                fontSize = 12.sp, color = TextTertiary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                // 启动真实导航模式
                                val steps = parseNavigationSteps(selectedPath)
                                if (steps.isNotEmpty() && currentLocation != null) {
                                    navigationSteps = steps
                                    currentStepIndex = 0
                                    simulationProgress = 0f
                                    remainingDistance = selectedPath.distance.toInt()
                                    remainingDuration = selectedPath.duration.toInt()
                                    navigationMode = NavigationMode.NAVIGATING
                                    isSearchExpanded = false
                                    showRouteInfo = false
                                    isFollowingLocation = true  // ⭐ 开始导航时启用跟随模式

                                    // 切换到导航视角
                                    aMap?.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(currentLocation)
                                                .zoom(18f)
                                                .tilt(60f)  // 倾斜视角
                                                .build()
                                        )
                                    )

                                    Toast.makeText(context, "开始导航，请沿路线行驶", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "请等待定位完成", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Default.Navigation, null, Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始导航", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 底部路况图例（POI详情卡片显示时隐藏）
        if (!showRouteInfo && navigationMode == NavigationMode.IDLE && !showPoiDetailCard) {
            Card(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("实时路况", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(if (showTraffic) "已开启" else "已关闭", fontSize = 12.sp, color = if (showTraffic) primaryColor else TextTertiary)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TrafficLegendItem(CongestionFree, "畅通")
                        TrafficLegendItem(CongestionLight, "缓行")
                        TrafficLegendItem(CongestionModerate, "拥堵")
                        TrafficLegendItem(CongestionSevere, "严重")
                    }
                }
            }
        }

        // ==================== 模拟导航界面 ====================
        AnimatedVisibility(
            visible = navigationMode != NavigationMode.IDLE,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.fillMaxSize()
        ) {
            NavigationModeUI(
                navigationMode = navigationMode,
                currentStep = navigationSteps.getOrNull(currentStepIndex),
                nextStep = navigationSteps.getOrNull(currentStepIndex + 1),
                remainingDistance = remainingDistance,
                remainingDuration = remainingDuration,
                totalSteps = navigationSteps.size,
                currentStepIndex = currentStepIndex,
                simulationProgress = simulationProgress,
                destinationName = selectedPoi?.title ?: directDestinationName ?: "目的地",
                isProfessional = isProfessional,
                isFollowing = isFollowingLocation,  // ⭐ 传入跟随状态
                onExitNavigation = {
                    navigationMode = NavigationMode.IDLE
                    navigationSteps = emptyList()
                    currentStepIndex = 0
                    simulationProgress = 0f
                    isSearchExpanded = true
                    isFollowingLocation = true  // ⭐ 退出时恢复跟随模式
                    // 恢复显示路线信息
                    if (routePaths.isNotEmpty()) {
                        showRouteInfo = true
                    }
                    // 恢复普通视角
                    currentLocation?.let { loc ->
                        aMap?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(loc)
                                    .zoom(16f)
                                    .tilt(0f)
                                    .bearing(0f)
                                    .build()
                            )
                        )
                    }
                },
                onResumeNavigation = {
                    if (navigationMode == NavigationMode.PAUSED) {
                        navigationMode = NavigationMode.NAVIGATING
                        isFollowingLocation = true  // ⭐ 恢复导航时启用跟随模式
                    }
                },
                onOverviewRoute = {
                    // ⭐ 全览路线：将相机移动到显示整条路线
                    isFollowingLocation = false  // 关闭跟随模式

                    val boundsBuilder = LatLngBounds.Builder()
                    var hasPoints = false

                    // 方式1：使用routePaths获取完整路线点
                    if (routePaths.isNotEmpty()) {
                        val selectedPath = routePaths.getOrNull(selectedRouteIndex) ?: routePaths[0]
                        selectedPath.steps.forEach { step ->
                            step.polyline?.forEach { latLonPoint ->
                                boundsBuilder.include(LatLng(latLonPoint.latitude, latLonPoint.longitude))
                                hasPoints = true
                            }
                        }
                    }

                    // 方式2：如果routePaths为空，使用navigationSteps
                    if (!hasPoints && navigationSteps.isNotEmpty()) {
                        navigationSteps.forEach { step ->
                            step.polylinePoints.forEach { point ->
                                boundsBuilder.include(point)
                                hasPoints = true
                            }
                        }
                    }

                    // 添加当前位置
                    currentLocation?.let {
                        boundsBuilder.include(it)
                        hasPoints = true
                    }

                    // 添加目的地
                    directDestinationLatLng?.let {
                        boundsBuilder.include(it)
                        hasPoints = true
                    }
                    selectedPoi?.let { poi ->
                        boundsBuilder.include(LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude))
                        hasPoints = true
                    }

                    if (hasPoints) {
                        try {
                            val bounds = boundsBuilder.build()
                            aMap?.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(bounds, 150),  // 增加边距
                                1000,  // 动画时长
                                null
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onResumeFollowing = {
                    // ⭐ 恢复跟随模式
                    isFollowingLocation = true
                    currentLocation?.let { loc ->
                        aMap?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(loc)
                                    .zoom(18f)
                                    .tilt(60f)
                                    .build()
                            ),
                            500,
                            null
                        )
                    }
                }
            )
        }

        // ⭐ 位置权限引导对话框
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "需要位置权限",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "为了显示您的实时位置并提供精准导航服务，请允许访问位置权限。\n\n请前往设置 → 权限 → 位置，选择「始终允许」或「仅在使用时允许」。",
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            // 跳转到应用设置页面
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("去设置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("稍后再说", color = TextSecondary)
                    }
                }
            )
        }
    }
}

// ==================== 路况图例 ====================
@Composable
private fun TrafficLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp, 4.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

// ==================== 工具函数 ====================

private fun formatDistance(meters: Int): String = if (meters >= 1000) String.format("%.1fkm", meters / 1000.0) else "${meters}m"

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun clearOverlays(markers: List<Marker>, polylines: List<Polyline>) {
    markers.forEach { it.remove() }
    polylines.forEach { it.remove() }
}

// ==================== 定位 ====================

private fun setupLocation(context: Context, map: AMap, onResult: (AMapLocationClient, AMapLocation) -> Unit) {
    val style = MyLocationStyle().apply {
        myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
        interval(2000)
        strokeColor(AndroidColor.TRANSPARENT)
        radiusFillColor(AndroidColor.parseColor("#1A0066FF"))
    }
    map.myLocationStyle = style
    map.isMyLocationEnabled = true

    try {
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)

        val client = AMapLocationClient(context)
        client.setLocationOption(AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isOnceLocation = false
            interval = 2000
        })

        var isFirst = true
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                if (isFirst) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16f))
                    isFirst = false
                }
                onResult(client, location)
            }
        }
        client.startLocation()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ==================== POI搜索 ====================

private fun searchPoi(context: Context, keyword: String, onResult: (List<PoiItem>) -> Unit) {
    val query = PoiSearch.Query(keyword, "", "")
    query.pageSize = 20
    query.pageNum = 0

    val search = PoiSearch(context, query)
    search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
        override fun onPoiSearched(result: PoiResult?, code: Int) {
            android.util.Log.d("POI_SEARCH", "搜索结果 code: $code, 关键词: $keyword")

            if (code == AMapException.CODE_AMAP_SUCCESS) {
                val pois = result?.pois ?: emptyList()
                android.util.Log.d("POI_SEARCH", "找到 ${pois.size} 个结果")
                onResult(pois)
            } else {
                android.util.Log.e("POI_SEARCH", "搜索失败，错误码: $code")
                onResult(emptyList())
            }
        }
        override fun onPoiItemSearched(item: PoiItem?, code: Int) {}
    })
    search.searchPOIAsyn()
}
// ==================== 路线规划 ====================

/**
 * 路线策略信息
 */
data class RouteStrategy(
    val path: DrivePath,
    val label: String,          // 标签：推荐、时间短、距离短等
    val description: String,    // 描述
    val isRecommended: Boolean = false
)

/**
 * 搜索多条驾车路线
 * 会返回多种策略的路线供用户选择
 */
private fun searchDriveRoute(context: Context, start: LatLonPoint, end: LatLonPoint, onResult: (List<DrivePath>) -> Unit) {
    val routeSearch = RouteSearch(context)
    val fromAndTo = RouteSearch.FromAndTo(start, end)

    // 使用多策略模式，返回多条路线
    // 策略10：返回多条路线（包含最快、最短等）
    val query = RouteSearch.DriveRouteQuery(
        fromAndTo,
        10,  // 多路线策略
        null,
        null,
        ""
    )

    routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
        override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) {
            if (code == AMapException.CODE_AMAP_SUCCESS && result?.paths != null) {
                // 返回所有路线，最多取5条
                val paths = result.paths.take(5)
                android.util.Log.d("ROUTE_SEARCH", "找到 ${paths.size} 条路线")
                onResult(paths)
            } else {
                android.util.Log.e("ROUTE_SEARCH", "路线规划失败，错误码: $code")
                onResult(emptyList())
            }
        }
        override fun onBusRouteSearched(result: BusRouteResult?, code: Int) {}
        override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) {}
        override fun onRideRouteSearched(result: RideRouteResult?, code: Int) {}
    })
    routeSearch.calculateDriveRouteAsyn(query)
}

/**
 * 获取路线标签
 * 根据路线在所有路线中的特点返回标签
 */
private fun getRouteLabel(path: DrivePath, allPaths: List<DrivePath>, index: Int): String {
    if (allPaths.size <= 1) return "推荐"

    val minDuration = allPaths.minOfOrNull { it.duration } ?: 0
    val minDistance = allPaths.minOfOrNull { it.distance } ?: 0
    val minTolls = allPaths.minOfOrNull { it.tolls } ?: 0
    val minTrafficLights = allPaths.minOfOrNull { it.totalTrafficlights } ?: 0

    return when {
        index == 0 -> "推荐"
        path.duration == minDuration && path.duration < allPaths[0].duration -> "时间短"
        path.distance == minDistance && path.distance < allPaths[0].distance -> "距离短"
        path.tolls == minTolls && path.tolls < allPaths[0].tolls -> "少收费"
        path.totalTrafficlights == minTrafficLights && path.totalTrafficlights < allPaths[0].totalTrafficlights -> "红绿灯少"
        else -> "备选"
    }
}

// ==================== 绘制路线 ====================

/**
 * 绘制单条路线
 */
private fun drawRoute(map: AMap, path: DrivePath, isProfessional: Boolean): List<Polyline> {
    val color = if (isProfessional) AndroidColor.parseColor("#FF6D00") else AndroidColor.parseColor("#4285F4")
    val points = mutableListOf<LatLng>()

    path.steps.forEach { step ->
        step.polyline.forEach { point ->
            points.add(LatLng(point.latitude, point.longitude))
        }
    }

    val polyline = map.addPolyline(
        PolylineOptions()
            .addAll(points)
            .width(18f)
            .color(color)
            .geodesic(true)
    )

    return listOf(polyline)
}

/**
 * 绘制所有路线（选中的高亮，未选中的灰色半透明）
 */
private fun drawAllRoutes(
    map: AMap,
    paths: List<DrivePath>,
    selectedIndex: Int,
    isProfessional: Boolean
): List<Polyline> {
    val polylines = mutableListOf<Polyline>()
    val selectedColor = if (isProfessional) AndroidColor.parseColor("#FF6D00") else AndroidColor.parseColor("#4285F4")
    val unselectedColor = AndroidColor.parseColor("#80AAAAAA")  // 灰色半透明

    // 先绘制未选中的路线（在下层）
    paths.forEachIndexed { index, path ->
        if (index != selectedIndex) {
            val points = mutableListOf<LatLng>()
            path.steps.forEach { step ->
                step.polyline.forEach { point ->
                    points.add(LatLng(point.latitude, point.longitude))
                }
            }

            val polyline = map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .width(14f)
                    .color(unselectedColor)
                    .geodesic(true)
                    .zIndex(1f)
            )
            polylines.add(polyline)
        }
    }

    // 再绘制选中的路线（在上层）
    val selectedPath = paths.getOrNull(selectedIndex)
    if (selectedPath != null) {
        val points = mutableListOf<LatLng>()
        selectedPath.steps.forEach { step ->
            step.polyline.forEach { point ->
                points.add(LatLng(point.latitude, point.longitude))
            }
        }

        val polyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(18f)
                .color(selectedColor)
                .geodesic(true)
                .zIndex(10f)
        )
        polylines.add(polyline)
    }

    return polylines
}

private fun zoomToRoute(map: AMap, path: DrivePath) {
    val points = mutableListOf<LatLng>()
    path.steps.forEach { step ->
        step.polyline.forEach { point ->
            points.add(LatLng(point.latitude, point.longitude))
        }
    }

    if (points.size >= 2) {
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ==================== 模拟导航UI组件 ====================

@Composable
private fun NavigationModeUI(
    navigationMode: NavigationMode,
    currentStep: NavigationStep?,
    nextStep: NavigationStep?,
    remainingDistance: Int,
    remainingDuration: Int,
    totalSteps: Int,
    currentStepIndex: Int,
    simulationProgress: Float,
    destinationName: String,
    isProfessional: Boolean,
    isFollowing: Boolean,  // ⭐ 是否跟随模式
    onExitNavigation: () -> Unit,
    onResumeNavigation: () -> Unit,
    onOverviewRoute: () -> Unit,
    onResumeFollowing: () -> Unit  // ⭐ 恢复跟随
) {
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部导航指引卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = primaryColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 当前步骤指引
                if (navigationMode == NavigationMode.ARRIVED) {
                    // 到达目的地
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "已到达目的地",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                destinationName,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                } else if (currentStep != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 导航动作图标
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getNavigationIcon(currentStep.action),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            // 距离下一步
                            Text(
                                text = formatDistance(((1 - simulationProgress) * currentStep.distance).toInt()),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            // 指令
                            Text(
                                text = currentStep.instruction,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 进度指示
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { simulationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )

                    // 路名
                    if (currentStep.roadName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当前路段：${currentStep.roadName}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 下一步预览（非到达状态）
        if (navigationMode != NavigationMode.ARRIVED && nextStep != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .padding(top = 180.dp)
                    .statusBarsPadding(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getNavigationIcon(nextStep.action),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "然后 ${nextStep.instruction}",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatDistance(nextStep.distance),
                        fontSize = 14.sp,
                        color = TextTertiary
                    )
                }
            }
        }

        // 底部信息栏
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
                // 剩余信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDuration(remainingDuration),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        Text("预计剩余", fontSize = 12.sp, color = TextSecondary)
                    }

                    // 分隔线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(DividerColor)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDistance(remainingDistance),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        Text("剩余距离", fontSize = 12.sp, color = TextSecondary)
                    }

                    // 分隔线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(DividerColor)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${currentStepIndex + 1}/$totalSteps",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text("步骤", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 目的地
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFEA4335),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = destinationName,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮
                if (navigationMode == NavigationMode.ARRIVED) {
                    Button(
                        onClick = onExitNavigation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Icon(Icons.Default.Done, null, Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("完成导航", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 退出导航按钮
                        OutlinedButton(
                            onClick = onExitNavigation,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("退出", fontSize = 16.sp)
                        }

                        // ⭐ 根据跟随状态显示不同按钮
                        if (isFollowing) {
                            // 全览路线按钮
                            Button(
                                onClick = onOverviewRoute,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Icon(Icons.Default.Map, null, Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("全览", fontSize = 16.sp)
                            }
                        } else {
                            // 回到当前位置按钮
                            Button(
                                onClick = onResumeFollowing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Icon(Icons.Default.MyLocation, null, Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("定位", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 导航图标映射 ====================

private fun getNavigationIcon(action: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        action.contains("左转") -> Icons.Default.KeyboardArrowLeft
        action.contains("右转") -> Icons.Default.KeyboardArrowRight
        action.contains("掉头") -> Icons.Default.Refresh
        action.contains("直行") -> Icons.Default.KeyboardArrowUp
        action.contains("到达") -> Icons.Default.LocationOn
        action.contains("进入") -> Icons.Default.KeyboardArrowRight
        action.contains("驶入") -> Icons.Default.CallMerge
        action.contains("环岛") -> Icons.Default.Refresh
        else -> Icons.Default.KeyboardArrowUp
    }
}

// ==================== 解析导航步骤 ====================

private fun parseNavigationSteps(path: DrivePath): List<NavigationStep> {
    return path.steps.map { step ->
        val points = step.polyline.map { LatLng(it.latitude, it.longitude) }

        // 从指令中提取动作类型
        val instruction = step.instruction ?: ""
        val action = when {
            instruction.contains("左转") -> "左转"
            instruction.contains("右转") -> "右转"
            instruction.contains("掉头") -> "掉头"
            instruction.contains("直行") -> "直行"
            instruction.contains("到达") -> "到达"
            instruction.contains("进入") -> "进入"
            instruction.contains("驶入") -> "驶入"
            instruction.contains("环岛") -> "环岛"
            else -> "直行"
        }

        NavigationStep(
            instruction = instruction.ifBlank { "继续前行" },
            distance = step.distance.toInt(),
            duration = step.duration.toInt(),
            action = action,
            roadName = step.road ?: "",
            polylinePoints = points
        )
    }
}

// ==================== 真实导航状态更新 ====================

private fun updateNavigationState(
    currentPosition: LatLng,
    steps: List<NavigationStep>,
    map: AMap?,
    isFollowing: Boolean,  // ⭐ 是否跟随定位
    onStateUpdate: (stepIndex: Int, progress: Float, remainingDistance: Int, remainingDuration: Int) -> Unit,
    onArrived: () -> Unit
) {
    if (steps.isEmpty()) return

    // 找到当前位置最接近的步骤和点
    var minDistance = Double.MAX_VALUE
    var closestStepIndex = 0
    var closestProgress = 0f

    for ((stepIndex, step) in steps.withIndex()) {
        val points = step.polylinePoints
        if (points.isEmpty()) continue

        for ((pointIndex, point) in points.withIndex()) {
            val distance = calculateDistance(currentPosition, point)
            if (distance < minDistance) {
                minDistance = distance
                closestStepIndex = stepIndex
                closestProgress = if (points.size > 1) pointIndex.toFloat() / (points.size - 1) else 0f
            }
        }
    }

    // 计算剩余距离和时间
    var remainingDist = 0
    var remainingTime = 0

    // 当前步骤的剩余部分
    val currentStep = steps.getOrNull(closestStepIndex)
    if (currentStep != null) {
        remainingDist += ((1 - closestProgress) * currentStep.distance).toInt()
        remainingTime += ((1 - closestProgress) * currentStep.duration).toInt()
    }

    // 后续步骤的距离和时间
    for (i in (closestStepIndex + 1) until steps.size) {
        remainingDist += steps[i].distance
        remainingTime += steps[i].duration
    }

    // ⭐ 只有在跟随模式下才更新地图相机
    if (isFollowing) {
        map?.let {
            // 计算朝向（如果有下一个点）
            val bearing = if (currentStep != null && currentStep.polylinePoints.size > 1) {
                val pointIndex = (closestProgress * (currentStep.polylinePoints.size - 1)).toInt()
                val nextIndex = (pointIndex + 1).coerceAtMost(currentStep.polylinePoints.size - 1)
                if (pointIndex != nextIndex) {
                    calculateBearing(currentStep.polylinePoints[pointIndex], currentStep.polylinePoints[nextIndex])
                } else {
                    0f
                }
            } else {
                0f
            }

            it.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(currentPosition)
                        .zoom(18f)
                        .bearing(bearing)
                        .tilt(60f)
                        .build()
                ),
                500,
                null
            )
        }
    }

    // 回调更新UI
    onStateUpdate(closestStepIndex, closestProgress, remainingDist, remainingTime)

    // 判断是否到达目的地（距离终点小于30米）
    val lastStep = steps.lastOrNull()
    val destination = lastStep?.polylinePoints?.lastOrNull()
    if (destination != null) {
        val distanceToDestination = calculateDistance(currentPosition, destination)
        if (distanceToDestination < 30) {  // 30米内认为到达
            onArrived()
        }
    }
}

// ==================== 计算两点间距离（米） ====================

private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
    val earthRadius = 6371000.0 // 地球半径（米）

    val lat1 = Math.toRadians(point1.latitude)
    val lat2 = Math.toRadians(point2.latitude)
    val dLat = Math.toRadians(point2.latitude - point1.latitude)
    val dLng = Math.toRadians(point2.longitude - point1.longitude)

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return earthRadius * c
}

// ==================== 计算方位角 ====================

private fun calculateBearing(start: LatLng, end: LatLng): Float {
    val startLat = Math.toRadians(start.latitude)
    val startLng = Math.toRadians(start.longitude)
    val endLat = Math.toRadians(end.latitude)
    val endLng = Math.toRadians(end.longitude)

    val dLng = endLng - startLng

    val x = Math.sin(dLng) * Math.cos(endLat)
    val y = Math.cos(startLat) * Math.sin(endLat) - Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng)

    var bearing = Math.toDegrees(Math.atan2(x, y)).toFloat()
    if (bearing < 0) bearing += 360f

    return bearing
}

// ==================== 动态POI图层相关函数 ====================

/**
 * POI类型到高德POI分类代码的映射
 */
private fun getPoiTypeCode(poiType: String): String {
    return when (poiType) {
        "加油站" -> "010100"      // 加油站
        "充电站" -> "011100"      // 充电站
        "停车场" -> "150900"      // 停车场
        "餐厅" -> "050000"        // 餐饮服务
        "货运站" -> "150200"      // 货运站
        "仓库" -> "120000"        // 商务住宅（含仓库）
        else -> ""
    }
}

/**
 * 搜索附近POI
 */
private fun searchNearbyPoi(
    context: Context,
    location: LatLng,
    poiType: String,
    radius: Int = 3000,  // 搜索半径，默认3公里
    onResult: (List<PoiItem>) -> Unit
) {
    val typeCode = getPoiTypeCode(poiType)

    // 使用周边搜索
    val query = PoiSearch.Query(poiType, typeCode, "")
    query.pageSize = 20
    query.pageNum = 0

    val search = PoiSearch(context, query)

    // 设置搜索范围
    val searchBound = PoiSearch.SearchBound(
        LatLonPoint(location.latitude, location.longitude),
        radius
    )
    search.bound = searchBound

    search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
        override fun onPoiSearched(result: PoiResult?, code: Int) {
            if (code == AMapException.CODE_AMAP_SUCCESS) {
                val pois = result?.pois ?: emptyList()
                android.util.Log.d("POI_LAYER", "搜索 $poiType 找到 ${pois.size} 个结果")
                onResult(pois)
            } else {
                android.util.Log.e("POI_LAYER", "POI搜索失败，错误码: $code")
                onResult(emptyList())
            }
        }

        override fun onPoiItemSearched(item: PoiItem?, code: Int) {}
    })

    search.searchPOIAsyn()
}

/**
 * 在地图上添加POI标记
 */
private fun addPoiMarkers(
    map: AMap,
    pois: List<PoiItem>,
    poiType: String,
    isProfessional: Boolean
): List<Marker> {
    val markers = mutableListOf<Marker>()

    // 根据POI类型选择标记颜色
    val hue = when (poiType) {
        "加油站" -> BitmapDescriptorFactory.HUE_YELLOW
        "充电站" -> BitmapDescriptorFactory.HUE_GREEN
        "停车场" -> BitmapDescriptorFactory.HUE_BLUE
        "餐厅" -> BitmapDescriptorFactory.HUE_ORANGE
        "货运站" -> BitmapDescriptorFactory.HUE_VIOLET
        "仓库" -> BitmapDescriptorFactory.HUE_CYAN
        else -> BitmapDescriptorFactory.HUE_RED
    }

    pois.forEach { poi ->
        val position = LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude)

        val markerOptions = MarkerOptions()
            .position(position)
            .title(poi.title)
            .snippet(buildPoiSnippet(poi, poiType))
            .icon(BitmapDescriptorFactory.defaultMarker(hue))

        val marker = map.addMarker(markerOptions)
        markers.add(marker)
    }

    // 显示信息窗口
    map.setOnMarkerClickListener { clickedMarker ->
        clickedMarker.showInfoWindow()
        true
    }

    return markers
}

/**
 * 添加POI标记（带点击回调）
 */
private fun addPoiMarkersWithClick(
    map: AMap,
    pois: List<PoiItem>,
    poiType: String,
    isProfessional: Boolean,
    onPoiClick: (PoiItem) -> Unit
): List<Marker> {
    val markers = mutableListOf<Marker>()
    val poiMap = mutableMapOf<String, PoiItem>()  // 用于根据marker找到对应的POI

    // 根据POI类型选择标记颜色
    val hue = when (poiType) {
        "加油站" -> BitmapDescriptorFactory.HUE_YELLOW
        "充电站" -> BitmapDescriptorFactory.HUE_GREEN
        "停车场" -> BitmapDescriptorFactory.HUE_BLUE
        "餐厅" -> BitmapDescriptorFactory.HUE_ORANGE
        "货运站" -> BitmapDescriptorFactory.HUE_VIOLET
        "仓库" -> BitmapDescriptorFactory.HUE_CYAN
        else -> BitmapDescriptorFactory.HUE_RED
    }

    pois.forEach { poi ->
        val position = LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude)
        val markerId = "${poi.latLonPoint.latitude}_${poi.latLonPoint.longitude}"

        val markerOptions = MarkerOptions()
            .position(position)
            .title(poi.title)
            .snippet(buildPoiSnippet(poi, poiType))
            .icon(BitmapDescriptorFactory.defaultMarker(hue))

        val marker = map.addMarker(markerOptions)
        marker.setObject(poi)  // 将POI对象存储在marker中
        markers.add(marker)
        poiMap[markerId] = poi
    }

    // 点击标记时显示详情卡片
    map.setOnMarkerClickListener { clickedMarker ->
        val poi = clickedMarker.`object` as? PoiItem
        if (poi != null) {
            onPoiClick(poi)
        }
        // 同时移动到标记位置
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(clickedMarker.position, 16f))
        true
    }

    return markers
}

/**
 * 构建POI信息摘要
 */
private fun buildPoiSnippet(poi: PoiItem, poiType: String): String {
    val parts = mutableListOf<String>()

    // 地址
    if (!poi.snippet.isNullOrBlank()) {
        parts.add(poi.snippet)
    } else if (!poi.cityName.isNullOrBlank()) {
        val address = listOfNotNull(poi.cityName, poi.adName, poi.businessArea)
            .filter { it.isNotBlank() }
            .joinToString("")
        if (address.isNotBlank()) {
            parts.add(address)
        }
    }

    // 距离
    if (poi.distance > 0) {
        val distanceText = if (poi.distance >= 1000) {
            String.format("%.1fkm", poi.distance / 1000.0)
        } else {
            "${poi.distance}m"
        }
        parts.add("距离: $distanceText")
    }

    return parts.joinToString(" | ")
}