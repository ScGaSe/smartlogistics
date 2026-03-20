package com.example.smartlogistics.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 道路实况 WebSocket
 *
 * 后端接口: ws://{host}:{port}/ws/traffic
 * 推送频率: 每30秒自动推送
 *
 * 修复记录：
 * - 2026-01-17: 修复 WS_URL 硬编码问题，改为从 RetrofitClient 动态获取
 */
class TrafficWebSocket private constructor() {

    companion object {
        private const val TAG = "SL_TrafficWebSocket"

        // 重连配置
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        // ⭐⭐⭐ 模拟模式开关 - 设为 true 可在无后端时测试 UI ⭐⭐⭐
        // 注意：此开关应与 Repository.USE_LOCAL_MOCK 保持一致
        private const val USE_MOCK_DATA = false
        private const val MOCK_UPDATE_INTERVAL_MS = 5000L

        @Volatile
        private var instance: TrafficWebSocket? = null

        fun getInstance(): TrafficWebSocket {
            return instance ?: synchronized(this) {
                instance ?: TrafficWebSocket().also { instance = it }
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

    // 重连计数
    private var reconnectAttempts = 0
    private var isManuallyDisconnected = false

    // 模拟数据Job
    private var mockDataJob: Job? = null

    // ==================== 状态流（供UI观察）====================

    /** 连接状态 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /** 闸口排队数据 */
    private val _gateQueues = MutableStateFlow<Map<String, Int>>(emptyMap())
    val gateQueues: StateFlow<Map<String, Int>> = _gateQueues

    /** 最后更新时间 */
    private val _lastUpdateTime = MutableStateFlow<String?>(null)
    val lastUpdateTime: StateFlow<String?> = _lastUpdateTime

    /** 路段拥堵数据（TTI值）*/
    private val _roadCongestion = MutableStateFlow<Map<String, Float>>(emptyMap())
    val roadCongestion: StateFlow<Map<String, Float>> = _roadCongestion

    /** 错误信息 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ==================== 枚举 ====================

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    // ==================== 数据类 ====================

    data class GateInfo(
        val gateId: String,
        val gateName: String,
        val queueCount: Int,
        val status: GateStatus
    )

    enum class GateStatus {
        SMOOTH,
        NORMAL,
        BUSY,
        CONGESTED
    }

    data class RoadSegment(
        val roadId: String,
        val roadName: String,
        val tti: Float,
        val speed: Float,
        val congestionLevel: CongestionLevel
    )

    enum class CongestionLevel {
        SMOOTH,
        SLOW,
        CONGESTED,
        BLOCKED
    }

    // ==================== 公开方法 ====================

    /**
     * 获取 WebSocket URL
     * 从 RetrofitClient 动态获取基础地址
     */
    private fun getWsUrl(): String {
        return try {
            "${RetrofitClient.getWebSocketBaseUrl()}/ws/traffic"
        } catch (e: Exception) {
            // RetrofitClient 未初始化时的降级处理
            Log.w(TAG, "RetrofitClient 未初始化，使用默认地址")
            "ws://172.20.10.3:8000/ws/traffic"
        }
    }

    /**
     * 连接道路实况WebSocket
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "已连接，跳过")
            return
        }

        isManuallyDisconnected = false
        _connectionState.value = ConnectionState.CONNECTING

        // ⭐ 模拟模式：不连接真实WebSocket，使用模拟数据
        if (USE_MOCK_DATA) {
            Log.d(TAG, "🔧 模拟模式已启用，使用模拟数据")
            startMockDataGeneration()
            return
        }

        val wsUrl = getWsUrl()
        Log.d(TAG, "连接道路实况: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "断开道路实况连接")
        isManuallyDisconnected = true

        mockDataJob?.cancel()
        mockDataJob = null

        webSocket?.close(1000, "用户断开")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        reconnectAttempts = 0
    }

    /**
     * 启动模拟数据生成
     */
    private fun startMockDataGeneration() {
        mockDataJob?.cancel()
        mockDataJob = scope.launch {
            delay(500)
            _connectionState.value = ConnectionState.CONNECTED
            _error.value = null
            Log.d(TAG, "✅ 模拟连接成功")

            while (isActive && !isManuallyDisconnected) {
                generateMockTrafficData()
                delay(MOCK_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * 生成模拟闸口数据
     */
    private fun generateMockTrafficData() {
        val random = java.util.Random()

        val mockGates = mapOf(
            "Gate_N1" to random.nextInt(8),
            "Gate_N2" to random.nextInt(5),
            "Gate_S1" to random.nextInt(12),
            "Gate_E1" to random.nextInt(6),
            "Gate_E2" to random.nextInt(4),
            "Gate_W1" to random.nextInt(10)
        )

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        _gateQueues.value = mockGates
        _lastUpdateTime.value = timestamp

        Log.d(TAG, "🔄 模拟数据更新: $mockGates")
    }

    /**
     * 获取闸口状态
     */
    fun getGateStatus(queueCount: Int): GateStatus {
        return when {
            queueCount <= 2 -> GateStatus.SMOOTH
            queueCount <= 5 -> GateStatus.NORMAL
            queueCount <= 10 -> GateStatus.BUSY
            else -> GateStatus.CONGESTED
        }
    }

    /**
     * 获取拥堵等级
     */
    fun getCongestionLevel(tti: Float): CongestionLevel {
        return when {
            tti < 1.5f -> CongestionLevel.SMOOTH
            tti < 2.0f -> CongestionLevel.SLOW
            tti < 3.0f -> CongestionLevel.CONGESTED
            else -> CongestionLevel.BLOCKED
        }
    }

    /**
     * 获取闸口列表（带状态）
     */
    fun getGateInfoList(): List<GateInfo> {
        return _gateQueues.value.map { (gateId, queueCount) ->
            GateInfo(
                gateId = gateId,
                gateName = getGateName(gateId),
                queueCount = queueCount,
                status = getGateStatus(queueCount)
            )
        }.sortedBy { it.gateId }
    }

    /**
     * 获取推荐闸口（排队最少）
     */
    fun getRecommendedGate(): GateInfo? {
        val gates = _gateQueues.value
        if (gates.isEmpty()) return null

        val minEntry = gates.minByOrNull { it.value }
        return minEntry?.let { (gateId, queueCount) ->
            GateInfo(
                gateId = gateId,
                gateName = getGateName(gateId),
                queueCount = queueCount,
                status = getGateStatus(queueCount)
            )
        }
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
                Log.d(TAG, "✅ 道路实况WebSocket已连接")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                _error.value = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "🚦 收到路况数据: $text")
                parseTrafficMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "道路实况WebSocket正在关闭: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "道路实况WebSocket已关闭: $code - $reason")
                _connectionState.value = ConnectionState.DISCONNECTED

                if (!isManuallyDisconnected) {
                    tryReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ 道路实况WebSocket连接失败", t)
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "连接失败: ${t.message}"

                if (!isManuallyDisconnected) {
                    tryReconnect()
                }
            }
        }
    }

    private fun parseTrafficMessage(text: String) {
        try {
            val json = JSONObject(text)

            when (json.optString("type")) {
                "traffic" -> {
                    _lastUpdateTime.value = json.optString("timestamp")

                    val gatesJson = json.optJSONObject("gates")
                    if (gatesJson != null) {
                        val gates = mutableMapOf<String, Int>()
                        gatesJson.keys().forEach { key ->
                            gates[key] = gatesJson.getInt(key)
                        }
                        _gateQueues.value = gates
                        Log.d(TAG, "更新闸口数据: $gates")
                    }

                    val roadsJson = json.optJSONObject("roads")
                    if (roadsJson != null) {
                        val roads = mutableMapOf<String, Float>()
                        roadsJson.keys().forEach { key ->
                            roads[key] = roadsJson.getDouble(key).toFloat()
                        }
                        _roadCongestion.value = roads
                    }
                }

                "error" -> {
                    _error.value = json.optString("message")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析路况消息失败", e)
        }
    }

    private fun tryReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "重连次数已达上限")
            _error.value = "连接失败，请检查网络后重试"
            return
        }

        reconnectAttempts++
        _connectionState.value = ConnectionState.RECONNECTING

        Log.d(TAG, "尝试重连 ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!isManuallyDisconnected && _connectionState.value == ConnectionState.RECONNECTING) {
                connect()
            }
        }
    }

    private fun getGateName(gateId: String): String {
        return when (gateId) {
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
    }

    // ==================== 生命周期 ====================

    fun onDestroy() {
        disconnect()
        scope.cancel()
    }
}