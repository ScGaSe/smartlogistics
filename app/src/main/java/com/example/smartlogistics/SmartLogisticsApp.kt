package com.example.smartlogistics

import android.app.Application
import com.example.smartlogistics.network.RetrofitClient
import com.amap.api.location.AMapLocationClient
import com.amap.api.services.core.ServiceSettings
/**
 * SmartLogistics Application
 * 应用级初始化
 */
class SmartLogisticsApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化网络客户端
        RetrofitClient.init(
            context = this,
            useMock = BuildConfig.DEBUG // Debug模式使用Mock
        )

        // 定位服务隐私合规
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        // 搜索服务隐私合规 - 添加这两行！
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        
        // TODO: 初始化其他SDK
        // - 高德/百度地图SDK
        // - ML Kit
        // - 崩溃收集
        // - 推送服务
    }
}

/**
 * 构建配置
 * 实际项目中由Gradle自动生成
 */
object BuildConfig {
    const val DEBUG = true
    const val APPLICATION_ID = "com.example.smartlogistics"
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1
}
