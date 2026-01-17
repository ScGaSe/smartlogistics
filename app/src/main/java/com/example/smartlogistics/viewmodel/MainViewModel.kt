package com.example.smartlogistics.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlogistics.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主ViewModel
 * 管理全局状态和通用数据
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository(application.applicationContext)

    // ==================== 认证状态 ====================
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    // 保存用户选择的角色
    private val _userRole = MutableStateFlow("personal")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    // ==================== 车辆状态 ====================
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _vehicleState = MutableStateFlow<VehicleState>(VehicleState.Idle)
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    // ==================== 报备状态 (专业模式) ====================
    private val _reports = MutableStateFlow<List<CargoReport>>(emptyList())
    val reports: StateFlow<List<CargoReport>> = _reports.asStateFlow()

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    // ==================== 行程状态 (个人模式) ====================
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState.asStateFlow()

    // ==================== 导航状态 ====================
    private val _routeResult = MutableStateFlow<RouteResult?>(null)
    val routeResult: StateFlow<RouteResult?> = _routeResult.asStateFlow()

    private val _trafficData = MutableStateFlow<TrafficData?>(null)
    val trafficData: StateFlow<TrafficData?> = _trafficData.asStateFlow()

    // ==================== AI助手状态 ====================
    private val _aiResponse = MutableStateFlow<AskResponse?>(null)
    val aiResponse: StateFlow<AskResponse?> = _aiResponse.asStateFlow()

    private val _aiState = MutableStateFlow<AIState>(AIState.Idle)
    val aiState: StateFlow<AIState> = _aiState.asStateFlow()

    // ==================== 拥堵预测状态 ====================
    private val _congestionData = MutableStateFlow<CongestionResponse?>(null)
    val congestionData: StateFlow<CongestionResponse?> = _congestionData.asStateFlow()

    // ==================== 停车预测 ====================
    private val _parkingPredictions = MutableStateFlow<List<ParkingPrediction>>(emptyList())
    val parkingPredictions: StateFlow<List<ParkingPrediction>> = _parkingPredictions.asStateFlow()

    // ==================== POI数据 ====================
    private val _pois = MutableStateFlow<List<POI>>(emptyList())
    val pois: StateFlow<List<POI>> = _pois.asStateFlow()

    // ==================== 认证方法 ====================

    fun register(phoneNumber: String, password: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.register(phoneNumber, password, role)) {
                is NetworkResult.Success -> {
                    _authState.value = AuthState.RegisterSuccess
                }
                is NetworkResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _authState.value = AuthState.Error(result.throwable.message ?: "注册失败")
                }
                else -> {}
            }
        }
    }

    /**
     * 登录方法 - 使用用户选择的角色
     */
    fun login(username: String, password: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _userRole.value = role

            when (val result = repository.login(username, password)) {
                is NetworkResult.Success -> {
                    _isLoggedIn.value = true
                    _userInfo.value = result.data.userInfo
                    val targetHome = if (role == "professional") "truck_home" else "car_home"
                    _authState.value = AuthState.LoginSuccess(targetHome)
                }
                is NetworkResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _authState.value = AuthState.Error(result.throwable.message ?: "登录失败")
                }
                else -> {}
            }
        }
    }

    fun logout() {
        // ⭐ 登出时断开用户通知WebSocket
        NotificationService.getInstance().disconnect()

        repository.logout()
        _isLoggedIn.value = false
        _userInfo.value = null
        _userRole.value = "personal"
        _vehicles.value = emptyList()
        _reports.value = emptyList()
        _trips.value = emptyList()
        _authState.value = AuthState.Idle
    }

    fun fetchCurrentUser() {
        viewModelScope.launch {
            when (val result = repository.getCurrentUser()) {
                is NetworkResult.Success -> {
                    _userInfo.value = result.data
                }
                else -> {}
            }
        }
    }

    fun isProfessionalMode(): Boolean = _userRole.value == "professional"
    fun getUserName(): String = repository.getUserName()

    // ==================== 车辆方法 ====================

    fun fetchVehicles() {
        viewModelScope.launch {
            _vehicleState.value = VehicleState.Loading
            when (val result = repository.getVehicles()) {
                is NetworkResult.Success -> {
                    _vehicles.value = result.data
                    _vehicleState.value = VehicleState.Success
                }
                is NetworkResult.Error -> {
                    _vehicleState.value = VehicleState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _vehicleState.value = VehicleState.Error(result.throwable.message ?: "获取车辆失败")
                }
                else -> {}
            }
        }
    }

    fun bindVehicle(
        plateNumber: String,
        vehicleType: String,
        heightM: Double? = null,
        weightT: Double? = null,
        axleCount: Int? = null
    ) {
        viewModelScope.launch {
            _vehicleState.value = VehicleState.Loading
            when (val result = repository.bindVehicle(plateNumber, vehicleType, heightM, weightT, axleCount)) {
                is NetworkResult.Success -> {
                    _vehicles.value = _vehicles.value + result.data
                    _vehicleState.value = VehicleState.BindSuccess
                }
                is NetworkResult.Error -> {
                    _vehicleState.value = VehicleState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _vehicleState.value = VehicleState.Error(result.throwable.message ?: "绑定失败")
                }
                else -> {}
            }
        }
    }

    fun unbindVehicle(vehicleId: String) {
        viewModelScope.launch {
            if (repository.unbindVehicle(vehicleId)) {
                _vehicles.value = _vehicles.value.filter { it.vehicleId != vehicleId }
            }
        }
    }

    // ==================== 报备方法 (专业模式) ====================

    fun fetchReports(page: Int = 1) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            when (val result = repository.getCargoReports(page)) {
                is NetworkResult.Success -> {
                    _reports.value = result.data.items
                    _reportState.value = ReportState.Success
                }
                is NetworkResult.Error -> {
                    _reportState.value = ReportState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _reportState.value = ReportState.Error(result.throwable.message ?: "获取报备记录失败")
                }
                else -> {}
            }
        }
    }

    fun submitReport(
        vehicleId: String,
        destinationId: String,
        cargoType: String,
        isHazardous: Boolean,
        hazardClass: String? = null,
        weight: Double? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            when (val result = repository.submitCargoReport(
                vehicleId, destinationId, cargoType, isHazardous, hazardClass, weight, description
            )) {
                is NetworkResult.Success -> {
                    _reports.value = listOf(result.data) + _reports.value
                    _reportState.value = ReportState.SubmitSuccess
                }
                is NetworkResult.Error -> {
                    _reportState.value = ReportState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _reportState.value = ReportState.Error(result.throwable.message ?: "提交报备失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 行程方法 (个人模式) ====================

    fun fetchTrips() {
        viewModelScope.launch {
            _tripState.value = TripState.Loading
            when (val result = repository.getTrips()) {
                is NetworkResult.Success -> {
                    _trips.value = result.data
                    _tripState.value = TripState.Success
                }
                is NetworkResult.Error -> {
                    _tripState.value = TripState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _tripState.value = TripState.Error(result.throwable.message ?: "获取行程失败")
                }
                else -> {}
            }
        }
    }

    fun createTrip(tripType: String, tripNumber: String, tripDate: String) {
        viewModelScope.launch {
            _tripState.value = TripState.Loading
            when (val result = repository.createTrip(tripType, tripNumber, tripDate)) {
                is NetworkResult.Success -> {
                    _trips.value = listOf(result.data) + _trips.value
                    _tripState.value = TripState.CreateSuccess
                }
                is NetworkResult.Error -> {
                    _tripState.value = TripState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _tripState.value = TripState.Error(result.throwable.message ?: "创建行程失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 导航方法 ====================

    fun getRoute(startPoiId: String, endPoiId: String, vehicleId: Int? = null) {
        viewModelScope.launch {
            when (val result = repository.getRoute(startPoiId, endPoiId, vehicleId)) {
                is NetworkResult.Success -> {
                    _routeResult.value = result.data
                }
                else -> {}
            }
        }
    }

    fun fetchTrafficData() {
        viewModelScope.launch {
            when (val result = repository.getTrafficData()) {
                is NetworkResult.Success -> {
                    _trafficData.value = result.data
                }
                else -> {}
            }
        }
    }

    // ==================== AI助手方法 ★★★ ====================

    /**
     * AI智能问答
     * @param query 用户问题
     * @param role 用户角色 (professional/personal)
     */
    fun askAI(query: String, role: String? = null) {
        viewModelScope.launch {
            _aiState.value = AIState.Loading
            val userRole = role ?: if (isProfessionalMode()) "professional" else "personal"

            when (val result = repository.askAI(query, userRole)) {
                is NetworkResult.Success -> {
                    _aiResponse.value = result.data
                    _aiState.value = AIState.Success
                }
                is NetworkResult.Error -> {
                    _aiState.value = AIState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _aiState.value = AIState.Error(result.throwable.message ?: "AI请求失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 拥堵预测方法 ★★★ ====================

    fun predictCongestion(roadId: String, hours: Int = 2) {
        viewModelScope.launch {
            when (val result = repository.predictCongestion(roadId, hours)) {
                is NetworkResult.Success -> {
                    _congestionData.value = result.data
                }
                else -> {}
            }
        }
    }

    // ==================== POI方法 ====================

    fun fetchPOIs(type: String? = null) {
        viewModelScope.launch {
            val mode = if (isProfessionalMode()) "pro" else "personal"
            when (val result = repository.getPOIs(type, mode)) {
                is NetworkResult.Success -> {
                    _pois.value = result.data
                }
                else -> {}
            }
        }
    }

    // ==================== 停车预测方法 ====================

    fun fetchParkingPrediction(arrivalTime: String) {
        viewModelScope.launch {
            when (val result = repository.getParkingPrediction(arrivalTime)) {
                is NetworkResult.Success -> {
                    _parkingPredictions.value = result.data
                }
                else -> {}
            }
        }
    }

    // ==================== 重置状态 ====================

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun resetVehicleState() {
        _vehicleState.value = VehicleState.Idle
    }

    fun resetReportState() {
        _reportState.value = ReportState.Idle
    }

    fun resetTripState() {
        _tripState.value = TripState.Idle
    }

    fun resetAIState() {
        _aiState.value = AIState.Idle
    }
}

// ==================== 状态类定义 ====================

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object RegisterSuccess : AuthState()
    data class LoginSuccess(val targetHome: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class VehicleState {
    object Idle : VehicleState()
    object Loading : VehicleState()
    object Success : VehicleState()
    object BindSuccess : VehicleState()
    data class Error(val message: String) : VehicleState()
}

sealed class ReportState {
    object Idle : ReportState()
    object Loading : ReportState()
    object Success : ReportState()
    object SubmitSuccess : ReportState()
    data class Error(val message: String) : ReportState()
}

sealed class TripState {
    object Idle : TripState()
    object Loading : TripState()
    object Success : TripState()
    object CreateSuccess : TripState()
    data class Error(val message: String) : TripState()
}

sealed class AIState {
    object Idle : AIState()
    object Loading : AIState()
    object Success : AIState()
    data class Error(val message: String) : AIState()
}