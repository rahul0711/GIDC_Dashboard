package com.example.mygidc.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mygidc.R
import com.example.mygidc.model.Department
import com.example.mygidc.settings.LogoutActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EngineerDashboardActivity : AppCompatActivity() {

    private var agencyId: Int = 0
    private var role: String = ""

    private lateinit var recyclerView: RecyclerView   // ✅ FIXED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        // ── Get Data ───────────────────────────────────────────────
        agencyId = intent.getIntExtra("agencyId", 0)
        role     = intent.getStringExtra("role") ?: ""

        println("👷 EngineerDashboard: agencyId=$agencyId, role=$role")

        // ── RecyclerView ──────────────────────────────────────────
        recyclerView = findViewById(R.id.rvDepartments)

        val json = intent.getStringExtra("departments")
        val dept = intent.getStringExtra("department")

        if (json != null) {
            val type = object : TypeToken<List<Department>>() {}.type
            val departmentList: List<Department> = Gson().fromJson(json, type)

            val finalList = if (!dept.isNullOrEmpty()) {
                departmentList.filter { it.departmentName.equals(dept, true) }
            } else {
                departmentList
            }

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = DepartmentAdapter(finalList) { dept ->
                val intent = Intent(this, DepartmentStatsActivity::class.java).apply {
                    putExtra("departmentId", dept.departmentId)
                    putExtra("departmentName", dept.departmentName)
                    putExtra("agencyId", agencyId)
                    putExtra("role", role)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }

        } else {
            // ✅ Prevent crash if departments missing
            Toast.makeText(this, "No departments found", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { openLogout() }
    }

    // ── Logout Function ───────────────────────────────────────────
    private fun openLogout() {
        val intent = Intent(this, LogoutActivity::class.java).apply {
            putExtra("role", role)
            putExtra("agencyId", agencyId)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}