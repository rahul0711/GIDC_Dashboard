package com.GIDC.app.model

data class Department(
    val departmentId: Int,
    val departmentName: String,
    val departmentNotes: String?,
    val userMasters: Any?,
    val complainTypeMasters: Any?
)