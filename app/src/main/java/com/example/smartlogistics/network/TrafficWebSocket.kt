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
 * 后端接口: ws://localhost:8000/ws/traffic
 * 推送频率: 每30秒自动推送
 *
 * 推送数据格式:
 * {
 *   "type": "traffic",
 *   "timestamp": "2026-01-16T12:15:06",
 *   "gates": {"Gate_N1": 3, "Gate_N2": 0, "Gate_S1": 1, "Gate_E1": 2}
 * }
 */
class TrafficWebSocket private constructor() {

    companion object {
        private const val TAG = "TrafficWebSocket"

        // ⭐ 后端WebSocket地址（部署时修改）
        private const val WS_URL = "ws://localhost:8000/ws/traffic"

        // 重连配置
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        // ⭐⭐⭐ 模拟模式开关 - 设为 true 可在无后端时测试 UI ⭐⭐⭐
        private const val USE_MOCK_DATA = false  // 正式对接后端，设为 false
        private const val MOCK_UPDATE_INTERVAL_MS = 5000L  // 模拟数据更新间隔（毫秒）

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
        DISCONNECTED,   // 已断开
        CONNECTING,     // 连接中
        CONNECTED,      // 已连接
        RECONNECTING    // 重连中
    }

    // ==================== 数据类 ====================

    /**
     * 闸口信息
     */
    data class GateInfo(
        val gateId: String,
        val gateName: String,
        val queueCount: Int,
        val status: GateStatus
    )

    enum class GateStatus {
        SMOOTH,     // 畅通 (0-2辆)
        NORMAL,     // 正常 (3-5辆)
        BUSY,       // 繁忙 (6-10辆)
        CONGESTED   // 拥堵 (>10辆)
    }

    /**
     * 路段拥堵信息
     */
    data class RoadSegment(
        val roadId: String,
        val roadName: String,
        val tti: Float,          // 交通指数 1.0=畅通, >2.0=拥堵
        val speed: Float,        // 当前速度 km/h
        val congestionLevel: CongestionLevel
    )

    enum class CongestionLevel {
        SMOOTH,     // 畅通 TTI < 1.5
        SLOW,       // 缓行 1.5 <= TTI < 2.0
        CONGESTED,  // 拥堵 2.0 <= TTI < 3.0
        BLOCKED     // 严重拥堵 TTI >= 3.0
    }

    // ==================== 公开方法 ====================

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

        Log.d(TAG, "连接道路实况: $WS_URL")

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "断开道路实况连接")
        isManuallyDisconnected = true

        // 停止模拟数据生成
        mockDataJob?.cancel()
        mockDataJob = null

        webSocket?.close(1000, "用户断开")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        reconnectAttempts = 0
    }

    /**
     * ⭐ 启动模拟数据生成（用于无后端时测试UI）
     */
    private fun startMockDataGeneration() {
        mockDataJob?.cancel()
        mockDataJob = scope.launch {
            // 模拟连接延迟
            delay(500)
            _connectionState.value = ConnectionState.CONNECTED
            _error.value = null
            Log.d(TAG, "✅ 模拟连接成功")

            // 持续生成模拟数据
            while (isActive && !isManuallyDisconnected) {
                generateMockTrafficData()
                delay(MOCK_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * ⭐ 生成模拟闸口数据
     */
    private fun generateMockTrafficData() {
        val random = java.util.Random()

        // 模拟各闸口排队数量（0-15辆随机）
        val mockGates = mapOf(
            "Gate_N1" to random.nextInt(8),      // 北1号：0-7辆
            "Gate_N2" to random.nextInt(5),      // 北2号：0-4辆（较少）
            "Gate_S1" to random.nextInt(12),     // 南1号：0-11辆
            "Gate_E1" to random.nextInt(6),      // 东1号：0-5辆
            "Gate_E2" to random.nextInt(4),      // 东2号：0-3辆
            "Gate_W1" to random.nextInt(10)      // 西1号：0-9辆
        )

        // 生成当前时间戳
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        // 更新状态
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

                // 非主动断开则尝试重连
                if (!isManuallyDisconnected) {
                    tryReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ 道路实况WebSocket连接失败", t)
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "连接失败: ${t.message}"

                // 尝试重连
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
                    // 解析时间戳
                    _lastUpdateTime.value = json.optString("timestamp")

                    // 解析闸口数据
                    val gatesJson = json.optJSONObject("gates")
                    if (gatesJson != null) {
                        val gates = mutableMapOf<String, Int>()
                        gatesJson.keys().forEach { key ->
                            gates[key] = gatesJson.getInt(key)
                        }
                        _gateQueues.value = gates
                        Log.d(TAG, "更新闸口数据: $gates")
                    }

                    // 解析路段拥堵数据（如果有）
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

    /**
     * 闸口ID转名称
     */
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