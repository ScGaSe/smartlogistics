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
            useMock = BuildConfig.DEBUG
        )

        // 定位服务隐私合规
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        // 搜索服务隐私合规
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        // key
       // ServiceSettings.getInstance().setApiKey("25c3f3c88fd0f3a407d7261c1f09301d")
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
