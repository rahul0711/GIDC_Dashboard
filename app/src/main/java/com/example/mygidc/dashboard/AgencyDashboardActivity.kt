package com.example.mygidc.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mygidc.R
import com.example.mygidc.model.Department
import com.example.mygidc.settings.LogoutActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AgencyDashboardActivity : AppCompatActivity() {

    private var agencyId: Int = 0
    private var role: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        // ── Get Data ───────────────────────────────────────────────
        agencyId = intent.getIntExtra("agencyId", 0)
        role     = intent.getStringExtra("role") ?: ""

        println("🏢 AgencyDashboard: agencyId=$agencyId, role=$role")

        // ── Toolbar (same as Admin) ───────────────────────────────
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // ── RecyclerView ──────────────────────────────────────────
        val recyclerView = findViewById<RecyclerView>(R.id.rvDepartments)
        val json = intent.getStringExtra("departments")

        if (json != null) {
            val type = object : TypeToken<List<Department>>() {}.type
            val departmentList: List<Department> = Gson().fromJson(json, type)

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = DepartmentAdapter(departmentList) { dept ->
                val intent = Intent(this, DepartmentStatsActivity::class.java).apply {
                    putExtra("departmentId",   dept.departmentId)
                    putExtra("departmentName", dept.departmentName)
                    putExtra("agencyId",       agencyId)
                    putExtra("role",           role)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        // ── Bottom Navigation (ADDED) ─────────────────────────────
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {

                R.id.nav_home -> {
                    recyclerView.smoothScrollToPosition(0)
                    true
                }

                R.id.nav_settings -> {
                    openLogout()
                    bottomNav.selectedItemId = R.id.nav_home
                    false
                }

                else -> false
            }
        }
    }

    // ── Logout Function (ADDED) ───────────────────────────────────
    private fun openLogout() {
        val intent = Intent(this, LogoutActivity::class.java).apply {
            putExtra("role",     role)
            putExtra("agencyId", agencyId)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}