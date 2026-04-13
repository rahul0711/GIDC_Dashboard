package com.example.mygidc.dashboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mygidc.R
import com.example.mygidc.api.RetrofitClient
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*

class DepartmentStatsActivity : AppCompatActivity() {

    private var deptId: Int = 0
    private var agencyId: Int = 0
    private var role: String = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_department_stats)

        deptId   = intent.getIntExtra("departmentId", 0)
        agencyId = intent.getIntExtra("agencyId", 0)
        role     = intent.getStringExtra("role") ?: ""
        val deptName = intent.getStringExtra("departmentName")

        findViewById<TextView>(R.id.tvTitle).text = deptName ?: "Department"

        val roleLower = role.trim().lowercase()

        when {
            roleLower.contains("engineer") || roleLower.contains("head") -> {
                showCards(
                    showResolved      = true,
                    showReLaunched    = true,
                    showApproved      = true,
                    showTimeAlerts    = true
                )
                fetchStats()
            }
            roleLower.contains("admin") -> {
                showCards(
                    showResolved      = false,
                    showReLaunched    = true,
                    showApproved      = true,
                    showTimeAlerts    = true
                )
                fetchStats()
            }
            roleLower.contains("agency") -> {
                showCards(
                    showResolved      = true,
                    showReLaunched    = true,
                    showApproved      = false,
                    showTimeAlerts    = false   // ← hides entire Time Alerts section
                )
                fetchStats()
            }
            else -> {
                showCards(
                    showResolved      = true,
                    showReLaunched    = false,
                    showApproved      = false,
                    showTimeAlerts    = false
                )
                fetchStats()
            }
        }

        // ── Status cards → SOURCE_STATUS ──────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardNew).setOnClickListener {
            openComplaintList("New", ComplaintDetailActivity.SOURCE_STATUS)
        }
        findViewById<MaterialCardView>(R.id.cardInProcess).setOnClickListener {
            openComplaintList("In Process", ComplaintDetailActivity.SOURCE_STATUS)
        }
        findViewById<MaterialCardView>(R.id.cardHold).setOnClickListener {
            openComplaintList("Hold", ComplaintDetailActivity.SOURCE_STATUS)
        }
        findViewById<MaterialCardView>(R.id.cardResolved).setOnClickListener {
            openComplaintList("Resolved", ComplaintDetailActivity.SOURCE_STATUS)
        }
        findViewById<MaterialCardView>(R.id.cardCancel).setOnClickListener {
            openComplaintList("Cancel", ComplaintDetailActivity.SOURCE_STATUS)
        }
        findViewById<MaterialCardView>(R.id.cardReLaunched).setOnClickListener {
            openComplaintList("ReLaunched", ComplaintDetailActivity.SOURCE_STATUS)
        }
        findViewById<MaterialCardView>(R.id.cardApproved).setOnClickListener {
            openComplaintList("Approved", ComplaintDetailActivity.SOURCE_STATUS)
        }

        // ── Alert / Resolve cards → SOURCE_ALERT_RESOLVE ──────────────────────
        // Safe: these are inside sectionTimeAlerts so clicks only fire when visible
        findViewById<MaterialCardView>(R.id.cardAlertCount).setOnClickListener {
            openComplaintList("alertCount", ComplaintDetailActivity.SOURCE_ALERT_RESOLVE)
        }
        findViewById<MaterialCardView>(R.id.cardResolveCount).setOnClickListener {
            openComplaintList("resolveCount", ComplaintDetailActivity.SOURCE_ALERT_RESOLVE)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // showCards — simplified: Time Alerts section controlled by one toggle
    // ─────────────────────────────────────────────────────────────────────────
    private fun showCards(
        showResolved:   Boolean,
        showReLaunched: Boolean,
        showApproved:   Boolean,
        showTimeAlerts: Boolean
    ) {
        // Always visible for all roles
        findViewById<MaterialCardView>(R.id.cardNew).visibility       = View.VISIBLE
        findViewById<MaterialCardView>(R.id.cardInProcess).visibility = View.VISIBLE
        findViewById<MaterialCardView>(R.id.cardHold).visibility      = View.VISIBLE
        findViewById<MaterialCardView>(R.id.cardCancel).visibility    = View.VISIBLE

        // Role-conditional individual cards
        findViewById<MaterialCardView>(R.id.cardResolved).visibility =
            if (showResolved) View.VISIBLE else View.GONE
        findViewById<MaterialCardView>(R.id.cardReLaunched).visibility =
            if (showReLaunched) View.VISIBLE else View.GONE
        findViewById<MaterialCardView>(R.id.cardApproved).visibility =
            if (showApproved) View.VISIBLE else View.GONE

        // Single toggle hides BOTH the red header label AND the two alert cards
        findViewById<View>(R.id.sectionTimeAlerts).visibility =
            if (showTimeAlerts) View.VISIBLE else View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // fetchStats
    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchStats() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.api.getStatsByDepartment(deptId)
                val data = if (response.isSuccessful) response.body() else null

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tvNewCount).text        = "${data?.newCount     ?: 0}"
                    findViewById<TextView>(R.id.tvInProcessCount).text  = "${data?.inProcess    ?: 0}"
                    findViewById<TextView>(R.id.tvHoldCount).text       = "${data?.hold         ?: 0}"
                    findViewById<TextView>(R.id.tvResolvedCount).text   = "${data?.resolved     ?: 0}"
                    findViewById<TextView>(R.id.tvCancelCount).text     = "${data?.cancel       ?: 0}"
                    findViewById<TextView>(R.id.tvReLaunchedCount).text = "${data?.reLaunched   ?: 0}"
                    findViewById<TextView>(R.id.tvApproved).text        = "${data?.approved     ?: 0}"
                    findViewById<TextView>(R.id.tvAlertCountNum).text   = "${data?.alertCount   ?: 0}"
                    findViewById<TextView>(R.id.tvResolveCountNum).text = "${data?.resolveCount ?: 0}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // openComplaintList
    // ─────────────────────────────────────────────────────────────────────────
    private fun openComplaintList(status: String, source: String) {
        val intent = Intent(this, ComplaintListActivity::class.java)
        intent.putExtra("status",       status)
        intent.putExtra("departmentId", deptId)
        intent.putExtra("agencyId",     agencyId)
        intent.putExtra("role",         role)
        intent.putExtra("source",       source)
        startActivity(intent)
    }
}