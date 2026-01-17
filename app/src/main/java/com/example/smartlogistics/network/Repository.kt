package com.example.smartlogistics.network

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 数据仓库
 * 封装所有网络请求逻辑 - 与后端API真实对接版本
 *
 * 注意：所有模拟数据已移除，全部通过API获取真实数据
 */
class Repository(private val context: Context) {

    companion object {
        private const val TAG = "Repository"
        // 是否使用本地模拟数据（设为false以使用真实API）
        const val USE_LOCAL_MOCK = false
    }

    private val api: ApiService = RetrofitClient.apiService
    private val tokenManager = TokenManager(context)

    // ==================== 认证相关 ====================

    /**
     * 用户注册
     */
    suspend fun register(phoneNumber: String, password: String, role: String): NetworkResult<RegisterResponse> {
        return try {
            val response = api.register(RegisterRequest(phoneNumber, password, role))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "注册失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 用户登录
     */
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
                NetworkResult.Success(loginResponse)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "登录失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 指纹登录
     */
    suspend fun biometricLogin(deviceId: String, signature: String? = null): NetworkResult<LoginResponse> {
        return try {
            val response = api.biometricLogin(BiometricLoginRequest(deviceId, signature))
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                tokenManager.saveToken(loginResponse.accessToken)
                NetworkResult.Success(loginResponse)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "指纹登录失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Biometric login error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 绑定设备
     */
    suspend fun bindDevice(deviceId: String): NetworkResult<UserInfo> {
        return try {
            val response = api.bindDevice(BiometricBindRequest(deviceId))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "设备绑定失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bind device error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 解绑设备
     */
    suspend fun unbindDevice(): NetworkResult<UserInfo> {
        return try {
            val response = api.unbindDevice()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "设备解绑失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unbind device error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 发送验证码
     */
    suspend fun sendVerificationCode(phoneNumber: String): NetworkResult<SendCodeResponse> {
        return try {
            val response = api.sendVerificationCode(SendCodeRequest(phoneNumber, "reset_password"))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.code == 0) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error(body.message)
                }
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "发送验证码失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send code error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 重置密码
     */
    suspend fun resetPassword(phoneNumber: String, code: String, newPassword: String): NetworkResult<ResetPasswordResponse> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(phoneNumber, code, newPassword))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.code == 0) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error(body.message)
                }
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "密码重置失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reset password error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUser(): NetworkResult<UserInfo> {
        return try {
            val response = api.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                val userInfo = response.body()!!
                // 保存用户角色
                tokenManager.saveUserRole(userInfo.role)
                NetworkResult.Success(userInfo)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取用户信息失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get current user error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    fun logout() {
        tokenManager.clearToken()
    }

    fun isLoggedIn(): Boolean = tokenManager.getToken() != null

    fun getUserName(): String = tokenManager.getUserName() ?: "用户"

    fun isProfessionalMode(): Boolean = tokenManager.getUserRole() == "professional"

    fun getSavedUserRole(): String = tokenManager.getUserRole()

    // ==================== 车辆管理 ====================

    /**
     * 获取车辆列表
     */
    suspend fun getVehicles(): NetworkResult<List<Vehicle>> {
        return try {
            val response = api.getVehicles()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取车辆列表失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get vehicles error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 绑定车辆
     */
    suspend fun bindVehicle(
        plateNumber: String,
        vehicleType: String,
        brand: String = "",
        heightM: Double? = null,
        weightT: Double? = null,
        axleCount: Int? = null
    ): NetworkResult<Vehicle> {
        return try {
            val specs = if (heightM != null || weightT != null || axleCount != null) {
                VehicleSpecs(heightM, weightT, axleCount)
            } else null

            val request = BindVehicleRequest(
                plateNumber = plateNumber,
                vehicleType = vehicleType,
                brand = brand.ifBlank { "未知品牌" },
                specs = specs
            )
            val response = api.createVehicle(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "绑定车辆失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bind vehicle error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 解绑车辆
     */
    suspend fun unbindVehicle(vehicleId: Int): NetworkResult<Boolean> {
        return try {
            val response = api.deleteVehicle(vehicleId)
            if (response.isSuccessful) {
                NetworkResult.Success(true)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "解绑车辆失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unbind vehicle error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 货物报备 (专业模式) ====================

    /**
     * 获取报备列表
     */
    suspend fun getCargoReports(page: Int = 1): NetworkResult<ReportListResponse> {
        return try {
            val response = api.getReports(page)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取报备记录失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get reports error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 提交货物报备
     */
    suspend fun submitCargoReport(
        vehicleId: Int,
        destinationPoiId: String,
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
                vehicleId = vehicleId,
                destinationPoiId = destinationPoiId,
                cargoInfo = cargoInfo
            )
            val response = api.createReport(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "提交报备失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Submit report error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 解析语音报备
     */
    suspend fun parseVoiceReport(text: String): NetworkResult<ReportParseResponse> {
        return try {
            val response = api.parseReport(ReportParseRequest(text))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "语音解析失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse report error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 行程管理 (个人模式) ====================

    /**
     * 获取行程列表
     */
    suspend fun getTrips(): NetworkResult<List<Trip>> {
        return try {
            val response = api.getTrips()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取行程失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get trips error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 创建行程
     */
    suspend fun createTrip(tripType: String, tripNumber: String, tripDate: String): NetworkResult<Trip> {
        return try {
            val request = CreateTripRequest(tripType, tripNumber, tripDate)
            val response = api.createTrip(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "创建行程失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create trip error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取行程详情
     */
    suspend fun getTripDetail(tripId: Int): NetworkResult<TripDetailResponse> {
        return try {
            val response = api.getTripDetail(tripId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取行程详情失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get trip detail error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 删除行程
     */
    suspend fun deleteTrip(tripId: Int): NetworkResult<Boolean> {
        return try {
            val response = api.deleteTrip(tripId)
            if (response.isSuccessful) {
                NetworkResult.Success(true)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "删除行程失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete trip error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 位置共享 ====================

    /**
     * 发起位置共享
     */
    suspend fun createLocationShare(tripId: Int, expiresInHours: Int = 24): NetworkResult<LocationShareResponse> {
        return try {
            val response = api.createLocationShare(tripId, expiresInHours)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "发起位置共享失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create location share error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取位置共享状态
     */
    suspend fun getLocationShareStatus(tripId: Int): NetworkResult<LocationShareResponse> {
        return try {
            val response = api.getLocationShareStatus(tripId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取共享状态失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get location share status error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 通过分享码获取位置共享详情
     */
    suspend fun getLocationShareDetail(shareId: String): NetworkResult<LocationShareDetail> {
        return try {
            // 构造基本的共享详情（通过WebSocket连接获取实时位置）
            val detail = LocationShareDetail(
                shareId = shareId,
                tripId = null,
                isActive = true,
                ownerName = null,
                tripInfo = null,
                wsUrl = getWebSocketBaseUrl() + "ws/location/$shareId",
                expiredAt = null
            )
            NetworkResult.Success(detail)
        } catch (e: Exception) {
            Log.e(TAG, "Get location share detail error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 停止位置共享
     */
    suspend fun stopLocationShare(tripId: Int): NetworkResult<Boolean> {
        return try {
            val response = api.stopLocationShare(tripId)
            if (response.isSuccessful) {
                NetworkResult.Success(true)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "停止位置共享失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop location share error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== POI查询 ====================

    /**
     * 获取POI列表
     */
    suspend fun getPOIs(role: String? = null, type: String? = null): NetworkResult<List<POI>> {
        return try {
            val queryRole = role ?: if (isProfessionalMode()) "truck" else "car"
            val response = api.getPois(queryRole, type)
            if (response.isSuccessful && response.body() != null) {
                val pois = response.body()!!.data?.pois ?: emptyList()
                NetworkResult.Success(pois)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取POI失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get POIs error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取附近POI
     */
    suspend fun getNearbyPOIs(lat: Double, lng: Double, radius: Int = 1000, type: String? = null): NetworkResult<List<POI>> {
        return try {
            val role = if (isProfessionalMode()) "truck" else "car"
            val response = api.getNearbyPois(lat, lng, radius, role, type)
            if (response.isSuccessful && response.body() != null) {
                val pois = response.body()!!.data?.pois ?: emptyList()
                NetworkResult.Success(pois)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取附近POI失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get nearby POIs error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取POI详情
     */
    suspend fun getPoiDetail(poiId: String): NetworkResult<POI> {
        return try {
            val response = api.getPoi(poiId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取POI详情失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get POI detail error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 停车管理 ====================

    /**
     * 获取附近停车场
     */
    suspend fun getNearbyParking(lat: Double, lng: Double, radius: Int = 2000): NetworkResult<List<ParkingInfo>> {
        return try {
            val response = api.getNearbyParking(lat, lng, radius)
            if (response.isSuccessful && response.body() != null) {
                val parkings = response.body()!!.data?.parkings ?: emptyList()
                NetworkResult.Success(parkings)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取附近停车场失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get nearby parking error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 停车预测
     */
    suspend fun predictParking(lotId: String, hours: Int = 3): NetworkResult<ParkingPredictResponse> {
        return try {
            val response = api.predictParking(lotId, hours)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "停车预测失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Predict parking error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 注册停车位置（拍照记录）
     */
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
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "停车记录失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register parking error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 寻找车辆（图像匹配）
     */
    suspend fun findParkingByPhoto(imageFile: File): NetworkResult<ParkingFindResponse> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
            val response = api.findParking(body)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "寻车失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Find parking error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取停车历史
     */
    suspend fun getParkingHistory(userId: Int): NetworkResult<List<ParkingSession>> {
        return try {
            val response = api.getParkingHistory(userId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取停车记录失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get parking history error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 导航路线 ====================

    /**
     * 路径规划
     */
    suspend fun planRoute(startPoiId: String, endPoiId: String, vehicleId: Int? = null): NetworkResult<RouteResponse> {
        return try {
            val request = RouteRequest(startPoiId, endPoiId, vehicleId)
            val response = api.planRoute(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "路径规划失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Plan route error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== AI智能问答 ====================

    /**
     * AI问答
     */
    suspend fun askAI(query: String, role: String? = null): NetworkResult<AskResponse> {
        return try {
            val userRole = role ?: if (isProfessionalMode()) "truck" else "car"
            val request = AskRequest(query, userRole)
            val response = api.askAI(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "AI请求失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ask AI error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 拥堵预测 ====================

    /**
     * 拥堵预测
     */
    suspend fun predictCongestion(
        roadId: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        radius: Int? = null,
        hours: Int = 5
    ): NetworkResult<CongestionResponse> {
        return try {
            val response = api.predictCongestion(roadId, lat, lng, radius, hours)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "拥堵预测失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Predict congestion error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 视觉检测 ====================

    /**
     * 车辆图像分析
     */
    suspend fun analyzeVehicleImage(imageFile: File): NetworkResult<VisionResponse> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
            val response = api.analyzeVehicle(body)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "图像分析失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analyze vehicle error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 历史记录 ====================

    /**
     * 获取行程历史
     */
    suspend fun getTripHistory(page: Int = 1, limit: Int = 20): NetworkResult<TripHistoryResponse> {
        return try {
            val response = api.getTripHistory(page, limit)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取行程历史失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get trip history error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取停车历史列表
     */
    suspend fun getParkingHistoryList(): NetworkResult<List<ParkingSession>> {
        return try {
            val response = api.getParkingHistoryList()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取停车历史失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get parking history list error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取报备历史
     */
    suspend fun getReportHistory(): NetworkResult<ReportListResponse> {
        return try {
            val response = api.getReportHistory()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取报备历史失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get report history error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== 交通实况 (HTTP备选接口) ====================

    /**
     * 获取闸口排队数据
     */
    suspend fun getGateQueues(): NetworkResult<GateQueuesResponse> {
        return try {
            val response = api.getGateQueues()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取闸口数据失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get gate queues error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    /**
     * 获取当前路段拥堵状况
     */
    suspend fun getCurrentCongestion(roadId: String): NetworkResult<CongestionResponse> {
        return try {
            val response = api.getCurrentCongestion(roadId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string(), "获取路段拥堵数据失败")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get current congestion error: ${e.message}", e)
            NetworkResult.Exception(e)
        }
    }

    // ==================== WebSocket相关 ====================

    /**
     * 获取WebSocket基础URL
     */
    fun getWebSocketBaseUrl(): String {
        return RetrofitClient.getWebSocketBaseUrl()
    }

    /**
     * 获取Token（用于WebSocket认证）
     */
    fun getToken(): String? {
        return tokenManager.getToken()
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析错误消息
     */
    private fun parseErrorMessage(errorBody: String?, defaultMessage: String): String {
        if (errorBody.isNullOrBlank()) return defaultMessage
        return try {
            val errorJson = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
            when {
                errorJson.has("detail") -> {
                    val detail = errorJson.get("detail")
                    when {
                        detail.isJsonObject -> detail.asJsonObject.get("message")?.asString ?: defaultMessage
                        detail.isJsonPrimitive -> detail.asString
                        else -> defaultMessage
                    }
                }
                errorJson.has("message") -> errorJson.get("message").asString
                else -> defaultMessage
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error message failed: ${e.message}")
            errorBody.take(100)  // 返回原始错误内容的前100字符
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