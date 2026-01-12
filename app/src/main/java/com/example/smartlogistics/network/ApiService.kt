package com.example.smartlogistics.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API服务接口
 * 根据后端API文档定义所有接口
 */
interface ApiService {
    
    // ==================== 认证相关 ====================
    
    @POST("register")
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
    
    @GET("pois/")
    suspend fun getPois(
        @Query("poi_type") poiType: String? = null,
        @Query("mode") mode: String? = null
    ): Response<List<POI>>
    
    @GET("pois/{poi_id}")
    suspend fun getPoi(@Path("poi_id") poiId: String): Response<POI>
    
    // ==================== 停车管理 ====================
    
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
    
    @POST("navigation/route")
    suspend fun planRoute(@Body request: RouteRequest): Response<RouteResult>
    
    // ==================== AI智能问答 (新增) ====================
    
    @POST("navigate/ask")
    suspend fun askAI(@Body request: AskRequest): Response<AskResponse>
    
    // ==================== 拥堵预测 (新增) ====================
    
    @GET("predict/congestion/{road_id}")
    suspend fun predictCongestion(
        @Path("road_id") roadId: String,
        @Query("hours") hours: Int = 2
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
}
