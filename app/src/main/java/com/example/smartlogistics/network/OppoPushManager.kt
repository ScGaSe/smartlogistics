package com.example.smartlogistics.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * OPPO 消息推送管理器
 *
 * 职责：
 * 1. App 启动时初始化并注册 OPPO 推送 SDK
 * 2. 获取设备唯一 registerId，上报给后端
 * 3. 接收推送通知，点击后跳转到对应页面
 *
 * 推送场景：
 * - 航班出发前提醒（个人端）
 * - 机场高速拥堵预警（个人端 / 专业端）
 * - 货物报备状态变更（专业端）
 *
 * ————————————————————————————————————————
 * OPPO 开放平台密钥（大赛申请，勿泄漏）
 * AppID     : 37000445
 * AppKey    : 16c13a8cff8d40609ea3e2599dd7445a
 * AppSecret : 05aaa06a1fac4b518ff4f65bc714502e
 * ————————————————————————————————————————
 *
 * SDK 导入步骤（代码之外还需要做）：
 * 1. 从 OPPO 开放平台下载推送 SDK 的 .aar 文件
 * 2. 放入 app/libs/ 目录
 * 3. app/build.gradle 添加：
 *      implementation(name: 'push-3.x.x', ext: 'aar')
 *      implementation 'com.google.code.gson:gson:2.6.2'
 *      implementation 'commons-codec:commons-codec:1.6'
 * 4. 将本文件中注释的代码取消注释即可生效
 */
object OppoPushManager {

    private const val TAG = "OppoPushManager"

    const val APP_KEY    = "16c13a8cff8d40609ea3e2599dd7445a"
    const val APP_SECRET = "05aaa06a1fac4b518ff4f65bc714502e"

    // ==================== 推送通知类型（与后端约定）====================
    // 后端发推送时在通知的 extra/userdata 字段里携带 type 值
    // 前端收到后根据 type 跳转到对应页面
    const val TYPE_FLIGHT_REMINDER = "flight_reminder"  // 航班出发提醒 → 跳转我的行程
    const val TYPE_TRAFFIC_ALERT   = "traffic_alert"    // 机场高速拥堵预警 → 跳转拥堵预测
    const val TYPE_CARGO_REPORT    = "cargo_report"     // 货物报备状态变更 → 跳转货物报备

    // ==================== 初始化 ====================

    /**
     * 在 Application.onCreate() 中调用
     * SDK aar 导入后，将注释的代码取消注释即可
     */
    fun init(context: Context) {
        try {
            /* ——— 取消注释以启用 OPPO 推送 ———

            // ① 仅 ColorOS 设备支持，非 OPPO 设备会返回 false
            if (!HeytapPushManager.isSupportPush()) {
                Log.w(TAG, "当前设备不支持 OPPO 推送，跳过初始化")
                return
            }

            // ② 初始化 SDK（true = 开启调试日志，上线前改为 false）
            HeytapPushManager.init(context, BuildConfig.DEBUG)

            // ③ 注册推送服务，异步回调返回 registerId
            HeytapPushManager.register(
                context,
                APP_KEY,
                APP_SECRET,
                object : ICallBackResultService {
                    override fun onRegister(code: Int, registerId: String?) {
                        if (code == 0 && !registerId.isNullOrBlank()) {
                            Log.d(TAG, "注册成功，registerId = $registerId")
                            // 将 registerId 上报给后端，后端用它向本设备发推送
                            uploadRegisterId(registerId)
                        } else {
                            Log.e(TAG, "注册失败，code = $code")
                        }
                    }
                    override fun onUnRegister(code: Int) {}
                    override fun onSetPushTime(code: Int, s: String?) {}
                    override fun onGetPushStatus(code: Int, status: Int) {}
                    override fun onGetNotificationStatus(code: Int, status: Int) {}
                }
            )

            ——— 注释结束 ——— */

            Log.d(TAG, "OppoPushManager.init() 占位调用（SDK aar 导入后取消注释）")

        } catch (e: Exception) {
            Log.e(TAG, "OPPO 推送初始化异常: ${e.message}")
        }
    }

    // ==================== registerId 上报 ====================

    /**
     * 将 registerId 上报给后端
     * 后端存储 registerId，在需要推送时调用 OPPO 服务端 API 发送通知
     */
    private fun uploadRegisterId(registerId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.uploadPushRegisterId(
                    PushRegisterRequest(registerId = registerId, platform = "oppo")
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "registerId 上报成功")
                } else {
                    Log.e(TAG, "registerId 上报失败: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerId 上报异常: ${e.message}")
            }
        }
    }

    // ==================== 点击通知跳转 ====================

    /**
     * 用户点击推送通知后调用
     * 根据通知携带的 type 字段，返回对应的页面路由
     *
     * 在 MainActivity 的 onNewIntent 中调用：
     *   val type = intent.getStringExtra("type")
     *   OppoPushManager.resolveRoute(type)?.let { navController.navigate(it) }
     */
    fun resolveRoute(type: String?): String? = when (type) {
        TYPE_FLIGHT_REMINDER -> "my_trips"       // 个人端：我的行程
        TYPE_TRAFFIC_ALERT   -> "car_congestion" // 个人端/专业端：拥堵预测
        TYPE_CARGO_REPORT    -> "cargo_report"   // 专业端：货物报备
        else                 -> null
    }
}

// ==================== 数据类 ====================

data class PushRegisterRequest(
    val registerId: String,
    val platform: String = "oppo"
)

data class PushRegisterResponse(
    val code: Int = 0,
    val message: String? = null
)