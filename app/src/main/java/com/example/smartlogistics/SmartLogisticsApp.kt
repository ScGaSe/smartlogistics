package com.example.smartlogistics

import android.app.Application
import com.example.smartlogistics.network.RetrofitClient
import com.example.smartlogistics.network.OppoPushManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.services.core.ServiceSettings
/**
 * SmartLogistics Application
 * 应用级初始化
 */
class SmartLogisticsApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化网络客户端 - 使用真实后端
        RetrofitClient.init(
            context = this,
            useMock = false  // false = 使用真实后端
        )

        // 定位服务隐私合规
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        // 搜索服务隐私合规
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        // 初始化 OPPO 推送（SDK aar 导入后自动生效）
        // 注册成功后自动获取 registerId 并上报给后端
        OppoPushManager.init(this)
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