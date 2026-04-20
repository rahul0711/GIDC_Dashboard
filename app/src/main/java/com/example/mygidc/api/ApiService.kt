package com.example.mygidc.api

import com.example.mygidc.model.ApprovedResolvedComplainRequest
import com.example.mygidc.model.ApprovedResolvedComplainResponse
import com.example.mygidc.model.ComplaintModel
import com.example.mygidc.model.DailyCountResponse
import com.example.mygidc.model.DepartmentStats
import com.example.mygidc.model.LoginRequest
import com.example.mygidc.model.LoginResponse
import com.example.mygidc.model.UpdateComplainRequest
import com.example.mygidc.model.UpdateComplainResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    // 🔐 Login
    @POST("API/MobileApp/AppUserLogin")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    // 📊 Complaints by Department (no status)
    @GET("API/MobileApp/ComplainByAlertTimeDepartmentId")
    suspend fun getComplaintsByDepartment(
        @Query("DepartmentId") departmentId: Int
    ): Response<List<ComplaintModel>>

    // 📈 Counts by department (no agency)
    @GET("API/MobileApp/ComplainCountByDepartmentIdWise")
    suspend fun getStatsByDepartment(
        @Query("DepartmentId") deptId: Int
    ): Response<DepartmentStats>

    // 📈 Counts by department + agency (status breakdown)
    @GET("API/MobileApp/ComplainCountByStatus")
    suspend fun getStatsByAgency(
        @Query("AgencyId") agencyId: Int,
        @Query("DepartmentId") deptId: Int
    ): Response<DepartmentStats>

    // ✅ Complaints by Agency + Status (e.g. …/ComplainAlertByAgency?AgencyId=30&Status=New)
    @GET("API/MobileApp/ComplainAlertByAgency")
    suspend fun getComplaintsByAgencyStatus(
        @Query("AgencyId") agencyId: Int,
        @Query("Status") status: String
    ): Response<List<ComplaintModel>>

    // 🔄 Update Complaint Status & Notes (Agency only)
    @POST("API/MobileApp/UpdateComplainByStatusWise")
    suspend fun updateComplain(
        @Body request: UpdateComplainRequest
    ): Response<UpdateComplainResponse>

    // 🔄 Update Complaint Status & Notes + optional image (Agency only)
    // Matches Postman multipart/form-data keys, including `ComplainResolvedImage`
    @Multipart
    @POST("API/MobileApp/UpdateComplainByStatusWise")
    suspend fun updateComplainMultipart(
        @Part("ComplainFormID") complainFormID: RequestBody,
        @Part("AgencyId") agencyId: RequestBody,
        @Part("Status") status: RequestBody,
        @Part("CallDuration") callDuration: RequestBody,
        @Part("ComplainType") complainType: RequestBody,
        @Part("CallStartTime") callStartTime: RequestBody,
        @Part("CallMobileNumber") callMobileNumber: RequestBody,
        @Part("CallRecordingLink") callRecordingLink: RequestBody,
        @Part("ComplainSpecialNotes") complainSpecialNotes: RequestBody,
        @Part complainResolvedImage: MultipartBody.Part?
    ): Response<UpdateComplainResponse>

    // ✔️ Approve / Resolve Complaint (Engineer/Head only)
    @POST("API/MobileApp/ApprovedResolvedComplainId")
    suspend fun approvedResolvedComplain(
        @Body request: ApprovedResolvedComplainRequest
    ): Response<ApprovedResolvedComplainResponse>


    @GET("API/MobileApp/GetDailyComplainCountDepartmentWise")
    suspend fun getDailyComplainCount(
        @Query("complainTypeId") complainTypeId: Int,
        @Query("selectedDate")   selectedDate: String? = null
    ): Response<DailyCountResponse>

    @GET("API/MobileApp/GetDailyResolvedCountDepartmentWise")
    suspend fun getDailyResolvedCount(
        @Query("complainTypeId") complainTypeId: Int,
        @Query("selectedDate")   selectedDate: String? = null
    ): Response<DailyCountResponse>


    @GET("API/MobileApp/GetComplainByDepartmentWise")
    suspend fun getComplaintsByDepartmentStatus(
        @Query("DepartmentId") departmentId: Int,
        @Query("Status") status: String
    ): Response<List<ComplaintModel>>



}