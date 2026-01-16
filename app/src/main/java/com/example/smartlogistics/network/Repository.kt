package com.example.smartlogistics.network

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 数据仓库
 * 封装所有网络请求逻辑
 */
class Repository(private val context: Context) {
    
    private val api: ApiService = RetrofitClient.apiService
    private val tokenManager = TokenManager(context)
    
    // ==================== 认证相关 ====================
    
    suspend fun register(phoneNumber: String, password: String, role: String): NetworkResult<RegisterResponse> {
        return try {
            val response = api.register(RegisterRequest(phoneNumber, password, role))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("注册失败: ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    suspend fun login(phoneNumber: String, password: String): NetworkResult<LoginResponse> {
        return try {
            val response = api.login(
                username = phoneNumber,
                password = password,
                grantType = "password",
                scope = "",
                clientId = "string",
                clientSecret = "string"
            )
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                // 保存Token
                tokenManager.saveToken(loginResponse.accessToken)
                tokenManager.saveUserName(phoneNumber)
                loginResponse.userInfo?.role?.let { tokenManager.saveUserRole(it) }
                NetworkResult.Success(loginResponse)
            } else {
                NetworkResult.Error("登录失败: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkResult.Exception(e)
        }
    }
    
    fun logout() {
        tokenManager.clearToken()
    }
    
    fun isLoggedIn(): Boolean = tokenManager.getToken() != null
    
    fun getUserName(): String = tokenManager.getUserName() ?: "用户"
    
    fun isProfessionalMode(): Boolean = tokenManager.getUserRole() == "professional"
    
    suspend fun getCurrentUser(): NetworkResult<UserInfo> {
        return try {
            val response = api.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取用户信息失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 车辆管理 ====================
    
    suspend fun getVehicles(): NetworkResult<List<Vehicle>> {
        return try {
            val response = api.getVehicles()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取车辆列表失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun bindVehicle(
        plateNumber: String,
        vehicleType: String,
        heightM: Double? = null,
        weightT: Double? = null,
        axleCount: Int? = null
    ): NetworkResult<Vehicle> {
        return try {
            val specs = if (heightM != null || weightT != null || axleCount != null) {
                VehicleSpecs(heightM, weightT, axleCount)
            } else null
            
            val request = BindVehicleRequest(plateNumber, vehicleType, specs = specs)
            val response = api.createVehicle(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("绑定车辆失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun unbindVehicle(vehicleId: String): Boolean {
        return try {
            val response = api.deleteVehicle(vehicleId.toInt())
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== 货物报备 (专业模式) ====================
    
    suspend fun getCargoReports(page: Int = 1): NetworkResult<ReportListResponse> {
        return try {
            val response = api.getReports(page)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取报备记录失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun submitCargoReport(
        vehicleId: String,
        destinationId: String,
        cargoType: String,
        isHazardous: Boolean,
        hazardClass: String? = null,
        weight: Double? = null,
        description: String? = null
    ): NetworkResult<CargoReport> {
        return try {
            val cargoInfo = CargoInfo(
                cargoType = cargoType,
                isHazardous = isHazardous,
                hazardClass = hazardClass,
                weight = weight,
                description = description
            )
            val request = SubmitReportRequest(
                vehicleId = vehicleId.toInt(),
                destinationPoiId = destinationId,
                cargoInfo = cargoInfo
            )
            val response = api.createReport(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("提交报备失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 行程管理 (个人模式) ====================
    
    suspend fun getTrips(): NetworkResult<List<Trip>> {
        return try {
            val response = api.getTrips()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取行程失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun createTrip(tripType: String, tripNumber: String, tripDate: String): NetworkResult<Trip> {
        return try {
            val request = CreateTripRequest(tripType, tripNumber, tripDate)
            val response = api.createTrip(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("创建行程失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== POI查询 ====================
    
    suspend fun getPOIs(type: String? = null, mode: String? = null): NetworkResult<List<POI>> {
        return try {
            val response = api.getPois(type, mode)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取POI失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 停车管理 ====================
    
    suspend fun startParking(vehicleId: Int, parkingLotId: String): NetworkResult<ParkingSession> {
        return try {
            val request = StartParkingRequest(vehicleId, parkingLotId)
            val response = api.startParking(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("开始停车失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun getParkingHistory(): NetworkResult<List<ParkingSession>> {
        return try {
            val response = api.getParkingHistory()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取停车记录失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 导航路线 ====================
    
    suspend fun getRoute(startPoiId: String, endPoiId: String, vehicleId: Int? = null): NetworkResult<RouteResult> {
        return try {
            val request = RouteRequest(startPoiId, endPoiId, vehicleId)
            val response = api.planRoute(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("路径规划失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== AI智能问答 (新增) ====================
    
    suspend fun askAI(query: String, role: String? = null): NetworkResult<AskResponse> {
        return try {
            val request = AskRequest(query, role)
            val response = api.askAI(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("AI请求失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 拥堵预测 (新增) ====================
    
    suspend fun predictCongestion(roadId: String, hours: Int = 2): NetworkResult<CongestionResponse> {
        return try {
            val response = api.predictCongestion(roadId, hours)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("拥堵预测失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 视觉检测 (新增) ====================
    
    suspend fun analyzeVehicleImage(imageFile: File): NetworkResult<VisionResponse> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
            val response = api.analyzeVehicle(body)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("图像分析失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 路况查询 ====================
    
    suspend fun getTrafficData(): NetworkResult<TrafficData> {
        return try {
            val response = api.getTrafficStatus()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取路况失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun getRoadTraffic(): NetworkResult<List<RoadTraffic>> {
        return try {
            val response = api.getRoadTraffic()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取道路路况失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 停车预测 ====================
    
    suspend fun getParkingPrediction(arrivalTime: String): NetworkResult<List<ParkingPrediction>> {
        // TODO: 实现停车预测API调用
        return NetworkResult.Success(emptyList())
    }

    // ==================== 智能停车助手 ====================

    suspend fun registerParkingPhoto(imageFile: File, floor: String? = null, zone: String? = null): NetworkResult<ParkingRegisterResponse> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

            val floorPart = floor?.toRequestBody("text/plain".toMediaTypeOrNull())
            val zonePart = zone?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.registerParking(body, floorPart, zonePart)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("停车记录失败: ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    suspend fun findParkingByPhoto(imageFile: File): NetworkResult<ParkingFindResponse> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

            val response = api.findParking(body)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("寻车失败: ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
}

// ==================== 网络结果封装 ====================

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()
    data class Exception(val throwable: Throwable) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
