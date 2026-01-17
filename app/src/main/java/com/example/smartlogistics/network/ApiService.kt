package com.example.smartlogistics.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API服务接口
 * 严格按照 OpenAPI 规范定义所有接口
 * API版本: 2.1.0
 */
interface ApiService {

    // ==================== 认证相关 ====================

    /**
     * 用户注册
     * POST /auth/register
     */
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    /**
     * 密码登录 (OAuth2表单格式)
     * POST /auth/login
     */
    @POST("auth/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password",
        @Field("scope") scope: String = "",
        @Field("client_id") clientId: String = "string",
        @Field("client_secret") clientSecret: String = "string"
    ): Response<LoginResponse>

    /**
     * 指纹登录
     * POST /auth/biometric-login
     */
    @POST("auth/biometric-login")
    suspend fun biometricLogin(@Body request: BiometricLoginRequest): Response<LoginResponse>

    /**
     * 绑定设备（用于指纹登录）
     * POST /auth/bind-device
     */
    @POST("auth/bind-device")
    suspend fun bindDevice(@Body request: BiometricBindRequest): Response<UserInfo>

    /**
     * 解绑设备
     * DELETE /auth/unbind-device
     */
    @DELETE("auth/unbind-device")
    suspend fun unbindDevice(): Response<UserInfo>

    /**
     * 发送短信验证码
     * POST /auth/send-code
     */
    @POST("auth/send-code")
    suspend fun sendVerificationCode(@Body request: SendCodeRequest): Response<SendCodeResponse>

    /**
     * 重置密码
     * POST /auth/reset-password
     */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ResetPasswordResponse>

    /**
     * 获取当前用户信息
     * GET /auth/me
     */
    @GET("auth/me")
    suspend fun getCurrentUser(): Response<UserInfo>

    // ==================== 车辆管理 ====================

    /**
     * 获取我的车辆列表
     * GET /vehicles/
     */
    @GET("vehicles/")
    suspend fun getVehicles(): Response<List<Vehicle>>

    /**
     * 绑定新车辆
     * POST /vehicles/
     */
    @POST("vehicles/")
    suspend fun createVehicle(@Body request: BindVehicleRequest): Response<Vehicle>

    /**
     * 解绑车辆
     * DELETE /vehicles/{vehicle_id}
     */
    @DELETE("vehicles/{vehicle_id}")
    suspend fun deleteVehicle(@Path("vehicle_id") vehicleId: Int): Response<Unit>

    // ==================== 货物报备 (专业模式) ====================

    /**
     * 获取我的报备历史
     * GET /reports/
     */
    @GET("reports/")
    suspend fun getReports(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<ReportListResponse>

    /**
     * 创建报备
     * POST /reports/
     */
    @POST("reports/")
    suspend fun createReport(@Body request: SubmitReportRequest): Response<CargoReport>

    /**
     * 获取报备详情
     * GET /reports/{report_id}
     */
    @GET("reports/{report_id}")
    suspend fun getReport(@Path("report_id") reportId: Int): Response<CargoReport>

    /**
     * 语音报备解析
     * POST /reports/parse
     */
    @POST("reports/parse")
    suspend fun parseReport(@Body request: ReportParseRequest): Response<ReportParseResponse>

    // ==================== 行程管理 (个人模式) ====================

    /**
     * 获取行程列表
     * GET /trips/
     */
    @GET("trips/")
    suspend fun getTrips(): Response<List<Trip>>

    /**
     * 创建行程（航班/列车关联）
     * POST /trips/
     */
    @POST("trips/")
    suspend fun createTrip(@Body request: CreateTripRequest): Response<Trip>

    /**
     * 获取行程详情
     * GET /trips/{trip_id}
     */
    @GET("trips/{trip_id}")
    suspend fun getTrip(@Path("trip_id") tripId: Int): Response<Trip>

    /**
     * 获取行程详细信息（含航班/列车动态）
     * GET /trips/{trip_id}/detail
     */
    @GET("trips/{trip_id}/detail")
    suspend fun getTripDetail(@Path("trip_id") tripId: Int): Response<TripDetailResponse>

    /**
     * 删除行程
     * DELETE /trips/{trip_id}
     */
    @DELETE("trips/{trip_id}")
    suspend fun deleteTrip(@Path("trip_id") tripId: Int): Response<Unit>

    // ==================== 位置共享 ====================

    /**
     * 发起位置共享
     * POST /trips/{trip_id}/share
     */
    @POST("trips/{trip_id}/share")
    suspend fun createLocationShare(
        @Path("trip_id") tripId: Int,
        @Query("expires_in_hours") expiresInHours: Int = 24
    ): Response<LocationShareResponse>

    /**
     * 获取位置共享状态
     * GET /trips/{trip_id}/share
     */
    @GET("trips/{trip_id}/share")
    suspend fun getLocationShareStatus(@Path("trip_id") tripId: Int): Response<LocationShareResponse>

    /**
     * 停止位置共享
     * DELETE /trips/{trip_id}/share
     */
    @DELETE("trips/{trip_id}/share")
    suspend fun stopLocationShare(@Path("trip_id") tripId: Int): Response<Unit>

    // ==================== POI查询 ====================

    /**
     * 兴趣点查询（支持角色筛选）
     * GET /pois
     */
    @GET("pois")
    suspend fun getPois(
        @Query("role") role: String? = null,  // car=私家车, truck=货车
        @Query("type") type: String? = null
    ): Response<PoisResponse>

    /**
     * 附近POI（空间查询）
     * GET /pois/nearby
     */
    @GET("pois/nearby")
    suspend fun getNearbyPois(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int = 1000,
        @Query("role") role: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<PoisResponse>

    /**
     * POI详情
     * GET /pois/{poi_id}
     */
    @GET("pois/{poi_id}")
    suspend fun getPoi(@Path("poi_id") poiId: String): Response<POI>

    // ==================== 停车管理 ====================

    /**
     * 附近停车场（通过经纬度）
     * GET /parking/nearby
     */
    @GET("parking/nearby")
    suspend fun getNearbyParking(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int = 2000
    ): Response<ParkingListResponse>

    /**
     * 注册停车位置（拍照记录）
     * POST /parking/register
     */
    @Multipart
    @POST("parking/register")
    suspend fun registerParking(
        @Part file: MultipartBody.Part,
        @Part("floor") floor: RequestBody? = null,
        @Part("zone") zone: RequestBody? = null
    ): Response<ParkingRegisterResponse>

    /**
     * 寻找车辆（图像匹配）
     * POST /parking/find
     */
    @Multipart
    @POST("parking/find")
    suspend fun findParking(@Part file: MultipartBody.Part): Response<ParkingFindResponse>

    /**
     * 停车历史（算法服务）
     * GET /parking/history/{user_id}
     */
    @GET("parking/history/{user_id}")
    suspend fun getParkingHistory(@Path("user_id") userId: Int): Response<List<ParkingSession>>

    // ==================== 导航路线 ====================

    /**
     * 路径规划（支持货车/私家车）
     * POST /navigate/route
     */
    @POST("navigate/route")
    suspend fun planRoute(@Body request: RouteRequest): Response<RouteResponse>

    /**
     * AI智能问答
     * POST /navigate/ask
     */
    @POST("navigate/ask")
    suspend fun askAI(@Body request: AskRequest): Response<AskResponse>

    // ==================== 预测服务 ====================

    /**
     * 拥堵预测
     * GET /predict/congestion
     */
    @GET("predict/congestion")
    suspend fun predictCongestion(
        @Query("road_id") roadId: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("hours") hours: Int = 5
    ): Response<CongestionResponse>

    /**
     * 停车预测（通过lot_id）
     * GET /parking/predict/{lot_id}
     */
    @GET("parking/predict/{lot_id}")
    suspend fun predictParking(
        @Path("lot_id") lotId: String,
        @Query("hours") hours: Int = 3
    ): Response<ParkingPredictResponse>

    // ==================== 视觉检测 ====================

    /**
     * 车辆视觉检测
     * POST /vision/analyze
     */
    @Multipart
    @POST("vision/analyze")
    suspend fun analyzeVehicle(@Part file: MultipartBody.Part): Response<VisionResponse>

    // ==================== 历史记录 ====================

    /**
     * 行程历史
     * GET /history/trips
     */
    @GET("history/trips")
    suspend fun getTripHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TripHistoryResponse>

    /**
     * 停车历史
     * GET /history/parking
     */
    @GET("history/parking")
    suspend fun getParkingHistoryList(): Response<List<ParkingSession>>

    /**
     * 报备历史
     * GET /history/reports
     */
    @GET("history/reports")
    suspend fun getReportHistory(): Response<ReportListResponse>

    // ==================== 交通实况 (HTTP备选接口) ====================

    /**
     * 获取闸口排队数据
     * GET /traffic/gates
     */
    @GET("traffic/gates")
    suspend fun getGateQueues(): Response<GateQueuesResponse>

    /**
     * 获取当前路段拥堵状况
     * GET /traffic/current/{road_id}
     */
    @GET("traffic/current/{road_id}")
    suspend fun getCurrentCongestion(@Path("road_id") roadId: String): Response<CongestionResponse>

    // ==================== POI列表 ====================

    /**
     * 获取POI列表（分页）
     * GET /pois/list
     */
    @GET("pois/list")
    suspend fun getPoisList(
        @Query("role") role: String? = null,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50
    ): Response<PoisResponse>
}