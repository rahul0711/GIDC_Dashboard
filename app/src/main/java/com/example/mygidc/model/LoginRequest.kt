package com.example.mygidc.model

data class LoginRequest(
    val UserName: String,
    val Password: String,
    val Designation: String = "Admin",
    val Email: String = "contact@softyoug.com",
    val Mobile: String = "9979857311,8511170666",
    val Notes: String = "Admin",
    val Status: String = "Active",
    val Type: String = "Admin",
    val UserID: String = "7",
    val DepartmentId: List<Int> = listOf(2)
)