package com.example.smartlogistics.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API服务接口
 * 根据后端API文档定义所有接口
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

    @GET("vehicles/{vehicle_id}")
    suspend fun getVehicle(@Path("vehicle_id") vehicleId: Int): Response<Vehicle>

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

    // ==================== 行程管理 (个人模式) ====================

    @GET("trips/")
    suspend fun getTrips(): Response<List<Trip>>

    @POST("trips/")
    suspend fun createTrip(@Body request: CreateTripRequest): Response<Trip>

    @GET("trips/{trip_id}")
    suspend fun getTrip(@Path("trip_id") tripId: Int): Response<Trip>

    @DELETE("trips/{trip_id}")
    suspend fun deleteTrip(@Path("trip_id") tripId: Int): Response<Unit>

    // ==================== POI查询 ====================

    @GET("pois")
    suspend fun getPois(
        @Query("type") type: String? = null,  // car=私家车, truck=货车
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null
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

    @GET("parking/all")
    suspend fun getAllParking(): Response<ParkingListResponse>

    @GET("parking/best")
    suspend fun getBestParking(): Response<ParkingListResponse>

    @POST("parking/")
    suspend fun startParking(@Body request: StartParkingRequest): Response<ParkingSession>

    @GET("parking/history")
    suspend fun getParkingHistory(): Response<List<ParkingSession>>

    @PUT("parking/{session_id}/end")
    suspend fun endParking(@Path("session_id") sessionId: Int): Response<ParkingSession>

    @Multipart
    @POST("parking/{session_id}/photo")
    suspend fun uploadParkingPhoto(
        @Path("session_id") sessionId: Int,
        @Part file: MultipartBody.Part
    ): Response<ParkingSession>

    // ==================== 导航路线 ====================

    @POST("navigate/route")
    suspend fun planRoute(@Body request: RouteRequest): Response<RouteResponse>

    // ==================== AI智能问答 (新增) ====================

    @POST("navigate/ask")
    suspend fun askAI(@Body request: AskRequest): Response<AskResponse>

    // ==================== 拥堵预测 (新增) ====================

    @GET("predict/congestion")
    suspend fun predictCongestion(
        @Query("road_id") roadId: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("hours") hours: Int = 5
    ): Response<CongestionResponse>

    // ==================== 视觉检测 (新增) ====================

    @Multipart
    @POST("vision/analyze")
    suspend fun analyzeVehicle(@Part file: MultipartBody.Part): Response<VisionResponse>

    // ==================== 路况查询 ====================

    @GET("traffic/status")
    suspend fun getTrafficStatus(): Response<TrafficData>

    @GET("traffic/roads")
    suspend fun getRoadTraffic(): Response<List<RoadTraffic>>

    @GET("traffic/gates")
    suspend fun getGateQueues(): Response<GateQueuesResponse>

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

    // ==================== 智能停车助手 ====================

    @Multipart
    @POST("parking/register")
    suspend fun registerParking(
        @Part file: MultipartBody.Part,
        @Part("floor") floor: RequestBody? = null,
        @Part("zone") zone: RequestBody? = null
    ): Response<ParkingRegisterResponse>

    @Multipart
    @POST("parking/find")
    suspend fun findParking(
        @Part file: MultipartBody.Part
    ): Response<ParkingFindResponse>

    // ==================== 位置共享 ====================

    /**
     * 发起位置共享
     * POST /trips/{trip_id}/share?expires_in_hours=24
     */
    @POST("trips/{trip_id}/share")
    suspend fun createLocationShare(
        @Path("trip_id") tripId: Int,
        @Query("expires_in_hours") expiresInHours: Int = 24
    ): Response<LocationShareResponse>

    /**
     * 获取位置共享详情（加入共享时使用）
     * GET /share/{share_id}
     */
    @GET("share/{share_id}")
    suspend fun getLocationShareDetail(
        @Path("share_id") shareId: String
    ): Response<LocationShareDetail>

    /**
     * 停止位置共享
     * DELETE /share/{share_id}
     */
    @DELETE("share/{share_id}")
    suspend fun stopLocationShare(
        @Path("share_id") shareId: String
    ): Response<Unit>
}