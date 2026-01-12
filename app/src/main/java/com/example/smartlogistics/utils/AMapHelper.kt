package com.example.smartlogistics.utils

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 高德地图位置服务帮助类
 */
class AMapLocationHelper(private val context: Context) {
    
    private var locationClient: AMapLocationClient? = null
    
    init {
        // 设置隐私合规
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
    }
    
    /**
     * 获取单次定位
     */
    fun getLocationOnce(): Flow<AMapLocation> = callbackFlow {
        val client = AMapLocationClient(context)
        
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isOnceLocation = true
            isOnceLocationLatest = true
        }
        
        client.setLocationOption(option)
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                trySend(location)
            }
            client.stopLocation()
            close()
        }
        
        client.startLocation()
        
        awaitClose {
            client.stopLocation()
            client.onDestroy()
        }
    }
    
    /**
     * 获取连续定位
     */
    fun getLocationUpdates(intervalMs: Long = 2000): Flow<AMapLocation> = callbackFlow {
        val client = AMapLocationClient(context)
        locationClient = client
        
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isOnceLocation = false
            interval = intervalMs
        }
        
        client.setLocationOption(option)
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                trySend(location)
            }
        }
        
        client.startLocation()
        
        awaitClose {
            client.stopLocation()
            client.onDestroy()
            locationClient = null
        }
    }
    
    /**
     * 停止定位
     */
    fun stopLocation() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }
}
