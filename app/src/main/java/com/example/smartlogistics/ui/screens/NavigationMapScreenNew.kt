package com.example.smartlogistics.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
    
    // 模式判断
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    // 权限
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
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
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_DESTROY -> {
                    locationClient?.stopLocation()
                    locationClient?.onDestroy()
                    mapView?.onDestroy()
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
                                currentLocation = LatLng(location.latitude, location.longitude)
                            }
                        }
                        
                        mapObj.setOnMapClickListener { showSearchResults = false }
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
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
                                    Icon(Icons.Rounded.Search, null, Modifier.size(20.dp))
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
                                            
                                            // 绘制路线
                                            aMap?.let { map ->
                                                clearOverlays(currentMarkers, currentPolylines)
                                                currentPolylines = drawRoute(map, paths[0], isProfessional)
                                                
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
                                    Icon(Icons.Rounded.Route, null, Modifier.size(20.dp))
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
                                        Icon(Icons.Rounded.LocationOn, null, tint = primaryColor, modifier = Modifier.size(24.dp))
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

        // ==================== 右侧按钮 ====================
        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
            FloatingActionButton(
                onClick = { showTraffic = !showTraffic },
                modifier = Modifier.size(48.dp),
                containerColor = if (showTraffic) primaryColor else Color.White,
                contentColor = if (showTraffic) Color.White else TextPrimary
            ) {
                Icon(Icons.Rounded.Traffic, "路况")
            }

            Spacer(modifier = Modifier.height(16.dp))

            FloatingActionButton(
                onClick = {
                    currentLocation?.let { loc ->
                        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                        Toast.makeText(context, "已定位到当前位置", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = primaryColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Rounded.MyLocation, "定位", Modifier.size(28.dp))
            }
        }

        // ==================== 底部路线信息 ====================
        AnimatedVisibility(
            visible = showRouteInfo && routePaths.isNotEmpty(),
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
                        
                        // 多路线选择
                        if (routePaths.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                routePaths.forEachIndexed { index, _ ->
                                    FilterChip(
                                        selected = index == selectedRouteIndex,
                                        onClick = { 
                                            selectedRouteIndex = index
                                            aMap?.let { map ->
                                                currentPolylines.forEach { it.remove() }
                                                currentPolylines = drawRoute(map, routePaths[index], isProfessional)
                                                zoomToRoute(map, routePaths[index])
                                            }
                                        },
                                        label = { Text("方案${index + 1}", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                                            selectedLabelColor = primaryColor
                                        )
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
                            onClick = { Toast.makeText(context, "开始导航", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Rounded.Navigation, null, Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始导航", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        
        // 底部路况图例
        if (!showRouteInfo) {
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

private fun searchDriveRoute(context: Context, start: LatLonPoint, end: LatLonPoint, onResult: (List<DrivePath>) -> Unit) {
    val routeSearch = RouteSearch(context)
    val fromAndTo = RouteSearch.FromAndTo(start, end)
    val query = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "")
    
    routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
        override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) {
            if (code == AMapException.CODE_AMAP_SUCCESS && result?.paths != null) {
                onResult(result.paths)
            } else {
                onResult(emptyList())
            }
        }
        override fun onBusRouteSearched(result: BusRouteResult?, code: Int) {}
        override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) {}
        override fun onRideRouteSearched(result: RideRouteResult?, code: Int) {}
    })
    routeSearch.calculateDriveRouteAsyn(query)
}

// ==================== 绘制路线 ====================

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
