package com.example.smartlogistics.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 用户通知 WebSocket 服务
 *
 * 后端接口: ws://localhost:8000/ws/user/{user_id}
 *
 * 功能：
 * 1. 接收航班/火车状态变化提醒
 * 2. 接收系统通知
 * 3. 接收位置共享邀请
 * 4. 显示本地通知
 *
 * 消息格式示例:
 * {
 *   "type": "flight_update",
 *   "title": "航班状态更新",
 *   "message": "MU5521 已开始登机，登机口 A12",
 *   "data": {
 *     "trip_id": 1,
 *     "status": "boarding",
 *     "gate": "A12"
 *   }
 * }
 */
class NotificationService private constructor() {

    companion object {
        private const val TAG = "NotificationService"

        // ⭐ 后端WebSocket地址（部署时修改）
        private const val WS_BASE_URL = "ws://localhost:8000/ws/user"

        // 通知渠道
        private const val CHANNEL_ID_TRIP = "trip_notifications"
        private const val CHANNEL_ID_SYSTEM = "system_notifications"
        private const val CHANNEL_ID_SHARE = "share_notifications"

        // 重连配置
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        // ⭐⭐⭐ 模拟模式开关 - 设为 true 可在无后端时测试通知 ⭐⭐⭐
        private const val USE_MOCK_DATA = false  // 正式对接后端，设为 false
        private const val MOCK_NOTIFICATION_INTERVAL_MS = 15000L  // 模拟通知间隔（15秒）

        @Volatile
        private var instance: NotificationService? = null

        fun getInstance(): NotificationService {
            return instance ?: synchronized(this) {
                instance ?: NotificationService().also { instance = it }
            }
        }
    }

    // OkHttp客户端
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    // WebSocket实例
    private var webSocket: WebSocket? = null

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 状态
    private var currentUserId: Int? = null
    private var reconnectAttempts = 0
    private var isManuallyDisconnected = false
    private var applicationContext: Context? = null

    // 模拟通知Job
    private var mockNotificationJob: Job? = null
    private var mockNotificationIndex = 0

    // ==================== 状态流 ====================

    /** 连接状态 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /** 最新通知（用于UI即时显示）*/
    private val _latestNotification = MutableStateFlow<UserNotification?>(null)
    val latestNotification: StateFlow<UserNotification?> = _latestNotification

    /** 通知事件流（用于监听所有通知）*/
    private val _notificationEvents = MutableSharedFlow<UserNotification>(replay = 0)
    val notificationEvents: SharedFlow<UserNotification> = _notificationEvents

    /** 未读通知数 */
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    /** 错误信息 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ==================== 枚举和数据类 ====================

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    /**
     * 通知类型
     */
    enum class NotificationType {
        FLIGHT_UPDATE,      // 航班状态更新
        TRAIN_UPDATE,       // 火车状态更新
        LOCATION_SHARE,     // 位置共享邀请
        SYSTEM,             // 系统通知
        PARKING,            // 停车提醒
        CONGESTION_ALERT,   // 拥堵预警
        UNKNOWN
    }

    /**
     * 用户通知数据
     */
    data class UserNotification(
        val id: String = System.currentTimeMillis().toString(),
        val type: NotificationType,
        val title: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val data: Map<String, Any>? = null,
        val isRead: Boolean = false
    )

    // ==================== 公开方法 ====================

    /**
     * 初始化通知服务
     * @param context Application Context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        createNotificationChannels()
    }

    /**
     * 连接用户通知WebSocket
     * @param userId 用户ID
     */
    fun connect(userId: Int) {
        if (_connectionState.value == ConnectionState.CONNECTED && currentUserId == userId) {
            Log.d(TAG, "已连接到相同用户，跳过")
            return
        }

        // 断开旧连接
        disconnect()

        currentUserId = userId
        isManuallyDisconnected = false
        _connectionState.value = ConnectionState.CONNECTING

        // ⭐ 模拟模式：不连接真实WebSocket，使用模拟通知
        if (USE_MOCK_DATA) {
            Log.d(TAG, "🔧 模拟模式已启用，将发送模拟通知")
            startMockNotifications()
            return
        }

        val url = "$WS_BASE_URL/$userId"
        Log.d(TAG, "连接用户通知: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "断开用户通知连接")
        isManuallyDisconnected = true

        // 停止模拟通知
        mockNotificationJob?.cancel()
        mockNotificationJob = null

        webSocket?.close(1000, "用户断开")
        webSocket = null
        currentUserId = null
        _connectionState.value = ConnectionState.DISCONNECTED
        reconnectAttempts = 0
    }

    /**
     * ⭐ 启动模拟通知（用于无后端时测试）
     */
    private fun startMockNotifications() {
        mockNotificationJob?.cancel()
        mockNotificationJob = scope.launch {
            // 模拟连接延迟
            delay(500)
            _connectionState.value = ConnectionState.CONNECTED
            _error.value = null
            Log.d(TAG, "✅ 模拟通知服务已启动")

            // 立即发送一条欢迎通知
            delay(2000)
            sendMockNotification()

            // 然后定期发送模拟通知
            while (isActive && !isManuallyDisconnected) {
                delay(MOCK_NOTIFICATION_INTERVAL_MS)
                sendMockNotification()
            }
        }
    }

    /**
     * ⭐ 发送模拟通知
     */
    private fun sendMockNotification() {
        // 模拟通知列表
        val mockNotifications = listOf(
            Triple(NotificationType.FLIGHT_UPDATE, "航班状态更新", "MU5521 已开始登机，登机口 A12"),
            Triple(NotificationType.FLIGHT_UPDATE, "航班延误提醒", "CA1234 预计延误30分钟，请关注后续通知"),
            Triple(NotificationType.TRAIN_UPDATE, "列车状态更新", "G1234 已到达长沙南站，请准备下车"),
            Triple(NotificationType.PARKING, "停车提醒", "您的车辆已停放超过2小时，当前费用: ¥10"),
            Triple(NotificationType.CONGESTION_ALERT, "拥堵预警", "北1号闸口当前排队较长，建议绕行北2号闸口"),
            Triple(NotificationType.LOCATION_SHARE, "位置共享邀请", "张三 邀请您查看他的实时位置"),
            Triple(NotificationType.SYSTEM, "系统通知", "枢纽停车场P2区今日维护，请前往P1或P3区停车")
        )

        val (type, title, message) = mockNotifications[mockNotificationIndex % mockNotifications.size]
        mockNotificationIndex++

        val notification = UserNotification(
            type = type,
            title = title,
            message = message
        )

        // 更新状态
        _latestNotification.value = notification
        _unreadCount.value = _unreadCount.value + 1

        // 发送事件
        scope.launch {
            _notificationEvents.emit(notification)
        }

        // 显示系统通知
        showSystemNotification(notification)

        Log.d(TAG, "🔔 模拟通知: $type - $title")
    }

    /**
     * ⭐ 手动触发一条测试通知（供调试使用）
     */
    fun sendTestNotification() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            scope.launch {
                sendMockNotification()
            }
        }
    }

    /**
     * 标记通知已读
     */
    fun markAsRead() {
        _unreadCount.value = 0
    }

    /**
     * 清除最新通知
     */
    fun clearLatestNotification() {
        _latestNotification.value = null
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _error.value = null
    }

    // ==================== 私有方法 ====================

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ 用户通知WebSocket已连接")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                _error.value = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "🔔 收到通知: $text")
                parseNotificationMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "用户通知WebSocket正在关闭: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "用户通知WebSocket已关闭: $code - $reason")
                _connectionState.value = ConnectionState.DISCONNECTED

                if (!isManuallyDisconnected) {
                    tryReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ 用户通知WebSocket连接失败", t)
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "连接失败: ${t.message}"

                if (!isManuallyDisconnected) {
                    tryReconnect()
                }
            }
        }
    }

    private fun parseNotificationMessage(text: String) {
        try {
            val json = JSONObject(text)

            val typeStr = json.optString("type", "unknown")
            val type = parseNotificationType(typeStr)
            val title = json.optString("title", "通知")
            val message = json.optString("message", "")

            // 解析附加数据
            val dataJson = json.optJSONObject("data")
            val data = dataJson?.let { parseDataObject(it) }

            val notification = UserNotification(
                type = type,
                title = title,
                message = message,
                data = data
            )

            // 更新状态
            _latestNotification.value = notification
            _unreadCount.value = _unreadCount.value + 1

            // 发送事件
            scope.launch {
                _notificationEvents.emit(notification)
            }

            // 显示系统通知
            showSystemNotification(notification)

            Log.d(TAG, "处理通知: $type - $title")

        } catch (e: Exception) {
            Log.e(TAG, "解析通知消息失败", e)
        }
    }

    private fun parseNotificationType(type: String): NotificationType {
        return when (type.lowercase()) {
            "flight_update" -> NotificationType.FLIGHT_UPDATE
            "train_update" -> NotificationType.TRAIN_UPDATE
            "location_share" -> NotificationType.LOCATION_SHARE
            "system" -> NotificationType.SYSTEM
            "parking" -> NotificationType.PARKING
            "congestion_alert" -> NotificationType.CONGESTION_ALERT
            else -> NotificationType.UNKNOWN
        }
    }

    private fun parseDataObject(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            map[key] = json.get(key)
        }
        return map
    }

    private fun tryReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "重连次数已达上限")
            _error.value = "连接失败，请检查网络"
            return
        }

        val userId = currentUserId ?: return
        reconnectAttempts++
        _connectionState.value = ConnectionState.RECONNECTING

        Log.d(TAG, "尝试重连 ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!isManuallyDisconnected && _connectionState.value == ConnectionState.RECONNECTING) {
                connect(userId)
            }
        }
    }

    // ==================== 本地通知 ====================

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = applicationContext ?: return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 行程通知渠道（高优先级）
            val tripChannel = NotificationChannel(
                CHANNEL_ID_TRIP,
                "行程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "航班/火车状态变化提醒"
                enableVibration(true)
            }

            // 系统通知渠道（默认优先级）
            val systemChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM,
                "系统通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "系统消息和公告"
            }

            // 位置共享通知渠道
            val shareChannel = NotificationChannel(
                CHANNEL_ID_SHARE,
                "位置共享",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "位置共享邀请和更新"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(tripChannel, systemChannel, shareChannel))
        }
    }

    private fun showSystemNotification(notification: UserNotification) {
        val context = applicationContext ?: return

        val channelId = when (notification.type) {
            NotificationType.FLIGHT_UPDATE, NotificationType.TRAIN_UPDATE -> CHANNEL_ID_TRIP
            NotificationType.LOCATION_SHARE -> CHANNEL_ID_SHARE
            else -> CHANNEL_ID_SYSTEM
        }

        val icon = when (notification.type) {
            NotificationType.FLIGHT_UPDATE -> android.R.drawable.ic_menu_compass
            NotificationType.TRAIN_UPDATE -> android.R.drawable.ic_menu_directions
            NotificationType.LOCATION_SHARE -> android.R.drawable.ic_menu_mylocation
            NotificationType.PARKING -> android.R.drawable.ic_menu_mapmode
            NotificationType.CONGESTION_ALERT -> android.R.drawable.ic_dialog_alert
            else -> android.R.drawable.ic_dialog_info
        }

        // 创建点击Intent（打开APP）
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notification.id.hashCode(), builder.build())
    }

    // ==================== 生命周期 ====================

    fun onDestroy() {
        disconnect()
        scope.cancel()
    }
}