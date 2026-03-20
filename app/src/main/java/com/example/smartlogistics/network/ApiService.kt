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

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

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

    @POST("auth/biometric-login")
    suspend fun biometricLogin(@Body request: BiometricLoginRequest): Response<LoginResponse>

    @POST("auth/bind-device")
    suspend fun bindDevice(@Body request: BiometricBindRequest): Response<UserInfo>

    @DELETE("auth/unbind-device")
    suspend fun unbindDevice(): Response<UserInfo>

    @POST("auth/send-code")
    suspend fun sendVerificationCode(@Body request: SendCodeRequest): Response<SendCodeResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ResetPasswordResponse>

    @GET("auth/me")
    suspend fun getCurrentUser(): Response<UserInfo>

    // ==================== 车辆管理 ====================

    @GET("vehicles/")
    suspend fun getVehicles(): Response<List<Vehicle>>

    @POST("vehicles/")
    suspend fun createVehicle(@Body request: BindVehicleRequest): Response<Vehicle>

    @DELETE("vehicles/{vehicle_id}")
    suspend fun deleteVehicle(@Path("vehicle_id") vehicleId: Int): Response<Unit>

    // ==================== 货物报备 (专业模式) ====================

    @GET("reports/")
    suspend fun getReports(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<ReportListResponse>

    @POST("reports/")
    suspend fun createReport(@Body request: SubmitReportRequest): Response<CargoReport>

    @GET("reports/{report_id}")
    suspend fun getReport(@Path("report_id") reportId: Int): Response<CargoReport>

    @POST("reports/parse")
    suspend fun parseReport(@Body request: ReportParseRequest): Response<ReportParseResponse>

    // ==================== 行程管理 (个人模式) ====================

    @GET("trips/")
    suspend fun getTrips(): Response<List<Trip>>

    @POST("trips/")
    suspend fun createTrip(@Body request: CreateTripRequest): Response<Trip>

    @GET("trips/{trip_id}")
    suspend fun getTrip(@Path("trip_id") tripId: Int): Response<Trip>

    @GET("trips/{trip_id}/detail")
    suspend fun getTripDetail(@Path("trip_id") tripId: Int): Response<TripDetailResponse>

    @DELETE("trips/{trip_id}")
    suspend fun deleteTrip(@Path("trip_id") tripId: Int): Response<Unit>

    // ==================== 位置共享 ====================

    @POST("trips/{trip_id}/share")
    suspend fun createLocationShare(
        @Path("trip_id") tripId: Int,
        @Query("expires_in_hours") expiresInHours: Int = 24
    ): Response<LocationShareResponse>

    @GET("trips/{trip_id}/share")
    suspend fun getLocationShareStatus(@Path("trip_id") tripId: Int): Response<LocationShareResponse>

    @DELETE("trips/{trip_id}/share")
    suspend fun stopLocationShare(@Path("trip_id") tripId: Int): Response<Unit>

    // ==================== POI查询 ====================

    @GET("pois")
    suspend fun getPois(
        @Query("type") type: String? = null
    ): Response<PoisResponse>

    @GET("pois/nearby")
    suspend fun getNearbyPois(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int = 1000,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<PoisResponse>

    @GET("pois/{poi_id}")
    suspend fun getPoi(@Path("poi_id") poiId: String): Response<POI>

    // ==================== 停车管理 ====================

    @GET("parking/nearby")
    suspend fun getNearbyParking(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int = 2000
    ): Response<ParkingListResponse>

    /**
     * 注册停车位置（拍照记录）
     * POST /parking/register
     * ⭐ 新增 spot_code 和 user_id 参数
     */
    @Multipart
    @POST("parking/register")
    suspend fun registerParking(
        @Part file: MultipartBody.Part,
        @Part("floor") floor: RequestBody? = null,
        @Part("zone") zone: RequestBody? = null,
        @Part("spot_code") spotCode: RequestBody? = null,
        @Part("user_id") userId: RequestBody? = null
    ): Response<ParkingRegisterResponse>

    @Multipart
    @POST("parking/find")
    suspend fun findParking(@Part file: MultipartBody.Part): Response<ParkingFindResponse>

    @GET("parking/history/{user_id}")
    suspend fun getParkingHistory(@Path("user_id") userId: Int): Response<List<ParkingSession>>

    // ==================== 导航路线 ====================

    @POST("navigate/route")
    suspend fun planRoute(@Body request: RouteRequest): Response<RouteResponse>

    @POST("navigate/ask")
    suspend fun askAI(@Body request: AskRequest): Response<AskResponse>

    // ==================== 预测服务 ====================

    @GET("predict/congestion")
    suspend fun predictCongestion(
        @Query("road_id") roadId: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("hours") hours: Int = 5
    ): Response<CongestionResponse>

    @GET("parking/predict/{lot_id}")
    suspend fun predictParking(
        @Path("lot_id") lotId: String,
        @Query("hours") hours: Int = 3
    ): Response<ParkingPredictResponse>

    // ==================== 视觉检测 ====================

    @Multipart
    @POST("vision/analyze")
    suspend fun analyzeVehicle(@Part file: MultipartBody.Part): Response<VisionResponse>

    // ==================== 历史记录 ====================

    @GET("history/trips")
    suspend fun getTripHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TripHistoryResponse>

    @GET("history/parking")
    suspend fun getParkingHistoryList(): Response<List<ParkingSession>>

    @GET("history/reports")
    suspend fun getReportHistory(): Response<ReportListResponse>

    // ==================== 交通实况 ====================

    @GET("traffic/gates")
    suspend fun getGateQueues(): Response<GateQueuesResponse>

    /**
     * 获取推荐闸口
     * GET /traffic/gates/recommend?lat=xx&lng=xx
     * ⭐ 新增接口
     */
    @GET("traffic/gates/recommend")
    suspend fun getGateRecommend(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("destination_poi") destinationPoi: String? = null
    ): Response<GateRecommendResponse>

    @GET("traffic/current/{road_id}")
    suspend fun getCurrentCongestion(@Path("road_id") roadId: String): Response<CongestionResponse>

    // ==================== POI列表 ====================

    @GET("pois/list")
    suspend fun getPoisList(
        @Query("poi_type") poiType: String? = null
    ): Response<List<POI>>
}