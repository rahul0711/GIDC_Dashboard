package com.example.mygidc.api

import com.example.mygidc.model.ApprovedResolvedComplainRequest
import com.example.mygidc.model.ApprovedResolvedComplainResponse
import com.example.mygidc.model.ComplaintModel
import com.example.mygidc.model.DepartmentStats
import com.example.mygidc.model.LoginRequest
import com.example.mygidc.model.LoginResponse
import com.example.mygidc.model.UpdateComplainRequest
import com.example.mygidc.model.UpdateComplainResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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

    // 📈 For Admin & Agency only
    @GET("API/MobileApp/ComplainCountByDepartmentIdWise")
    suspend fun getStatsByDepartment(
        @Query("DepartmentId") deptId: Int
    ): Response<DepartmentStats>

    // ✅ Complaints by Department + Status
    @GET("API/MobileApp/ComplainByDepartment")
    suspend fun getComplaintsByDepartmentStatus(
        @Query("DepartmentId") departmentId: Int,
        @Query("Status") status: String
    ): Response<List<ComplaintModel>>

    // 🔄 Update Complaint Status & Notes (Agency only)
    @POST("API/MobileApp/UpdateComplainByStatusWise")
    suspend fun updateComplain(
        @Body request: UpdateComplainRequest
    ): Response<UpdateComplainResponse>

    // ✔️ Approve / Resolve Complaint (Engineer/Head only)
    @POST("API/MobileApp/ApprovedResolvedComplainId")
    suspend fun approvedResolvedComplain(
        @Body request: ApprovedResolvedComplainRequest
    ): Response<ApprovedResolvedComplainResponse>
}