package com.example.smartlogistics.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView // ★★★ 关键修改：导入 TextureMapView
import com.amap.api.maps.model.*
import com.amap.api.maps.AMapOptions

/**
 * 高德地图 Compose 组件
 * 修改注：已将 MapView 替换为 TextureMapView 以解决 Compose 中的闪烁/白屏问题
 */
@Composable
fun AMapView(
    modifier: Modifier = Modifier,
    onMapReady: ((AMap) -> Unit)? = null,
    onLocationChanged: ((AMapLocation) -> Unit)? = null,
    showMyLocation: Boolean = true,
    showTraffic: Boolean = true,
    markers: List<MarkerData> = emptyList(),
    polylinePoints: List<LatLng>? = null,
    polylineColor: Int = Color.BLUE
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ★★★ 关键修改：类型改为 TextureMapView
    var mapView by remember { mutableStateOf<TextureMapView?>(null) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }

    // 检查定位权限
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // onCreate 在 factory 中手动调用了，这里不需要重复调用，否则可能重置地图
                // Lifecycle.Event.ON_CREATE -> mapView?.onCreate(Bundle())
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        locationClient?.stopLocation()
                        locationClient?.onDestroy()
                        mapView?.onDestroy()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Compose销毁时再次确保清理
            try {
                mapView?.onDestroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 更新标记点 (保持原有逻辑)
    LaunchedEffect(markers, aMap) {
        aMap?.let { map ->
            map.clear()
            markers.forEach { markerData ->
                val markerOptions = MarkerOptions()
                    .position(markerData.position)
                    .title(markerData.title)
                    .snippet(markerData.snippet)

                markerData.iconResId?.let {
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(it))
                }

                map.addMarker(markerOptions)
            }
        }
    }

    // 绘制路线 (保持原有逻辑)
    LaunchedEffect(polylinePoints, aMap) {
        aMap?.let { map ->
            polylinePoints?.let { points ->
                if (points.size >= 2) {
                    val polylineOptions = PolylineOptions()
                        .addAll(points)
                        .width(18f) //稍微加宽一点
                        .color(polylineColor)
                        .geodesic(true)
                    map.addPolyline(polylineOptions)

                    val boundsBuilder = LatLngBounds.Builder()
                    points.forEach { boundsBuilder.include(it) }
                    try {
                        val bounds = boundsBuilder.build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // 更新路况显示
    LaunchedEffect(showTraffic, aMap) {
        aMap?.isTrafficEnabled = showTraffic
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // ★★★ 关键修改：实例化 TextureMapView
            TextureMapView(ctx).apply {
                mapView = this
                // 必须在主线程立即调用 onCreate，否则可能初始化失败
                onCreate(Bundle())

                aMap = map.also { mapObj ->
                    // 基础设置
                    mapObj.uiSettings.apply {
                        isZoomControlsEnabled = false // 建议在 Compose 中关闭自带缩放按钮，位置可能会乱
                        isCompassEnabled = true
                        isMyLocationButtonEnabled = false // 建议关闭自带定位按钮，使用你 UI 里的 FloatingActionButton
                        isScaleControlsEnabled = true

                        //  隐藏 Logo
                        // 1. 先定在这个位置
                        logoPosition = AMapOptions.LOGO_POSITION_BOTTOM_LEFT
                        setLogoLeftMargin(100)

                        setLogoBottomMargin(180)


                    }

                    mapObj.isTrafficEnabled = showTraffic
                    mapObj.mapType = AMap.MAP_TYPE_NORMAL

                    // 显示我的位置
                    if (showMyLocation && hasLocationPermission) {
                        setupMyLocation(ctx, mapObj, locationClient) { client, location ->
                            locationClient = client
                            onLocationChanged?.invoke(location)
                        }
                    }

                    onMapReady?.invoke(mapObj)
                }
            }
        },
        onRelease = {
            // AndroidView 释放时的回调 (Compose 1.4+ 新增，可选)
            it.onDestroy()
        }
    )
}

/**
 * 设置定位功能
 */
private fun setupMyLocation(
    context: Context,
    map: AMap,
    existingClient: AMapLocationClient?,
    onLocationResult: (AMapLocationClient, AMapLocation) -> Unit
) {
    val myLocationStyle = MyLocationStyle().apply {
        myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
        interval(2000)
        strokeColor(Color.TRANSPARENT)
        radiusFillColor(Color.parseColor("#1A0066FF"))
    }

    map.myLocationStyle = myLocationStyle
    map.isMyLocationEnabled = true

    try {
        // 再次确保隐私合规被调用（双重保险）
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)

        val client = existingClient ?: AMapLocationClient(context)

        val locationOption = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isOnceLocation = false
            interval = 2000
        }

        client.setLocationOption(locationOption)
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                // 注意：不要每次定位都移动相机，否则用户滑不动地图
                // 只有第一次定位时才移动相机，或者由外部按钮控制
                // val latLng = LatLng(location.latitude, location.longitude)
                // map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

                onLocationResult(client, location)
            } else {
                // 打印错误日志
                android.util.Log.e("AMap", "定位失败: ${location?.errorCode} ${location?.errorInfo}")
            }
        }
        client.startLocation()

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// MarkerData 类不需要动
data class MarkerData(
    val position: LatLng,
    val title: String,
    val snippet: String? = null,
    val iconResId: Int? = null
)