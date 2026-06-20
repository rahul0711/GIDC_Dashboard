package com.ScriptIndia.GIDC_CMS_APP.dashboard

import android.os.Bundle
import android.util.Log
import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ScriptIndia.GIDC_CMS_APP.R
import com.ScriptIndia.GIDC_CMS_APP.api.RetrofitClient
import com.ScriptIndia.GIDC_CMS_APP.model.Department
import com.ScriptIndia.GIDC_CMS_APP.model.ComplaintModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ComplaintListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ComplaintAdapter
    private lateinit var btnPickDate: MaterialButton
    private lateinit var btnClearDate: MaterialButton
    private lateinit var btnViewAll: MaterialButton
    private lateinit var progressList: ProgressBar

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var rawStatus: String
    private var departmentId: Int = 0
    private var agencyId: Int = 0
    private lateinit var role: String
    private lateinit var source: String
    private var departmentList: List<Department> = emptyList()
    private var anyUpdateHappened: Boolean = false
    private var allFetchedComplaints: List<ComplaintModel> = emptyList()
    private var isViewAllClicked = false

    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val updated = result.data?.getBooleanExtra("complaint_updated", false) == true
                if (updated) {
                    anyUpdateHappened = true
                    fetchComplaints(rawStatus, departmentId, agencyId, role, source)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complaint_list)

        rawStatus = intent.getStringExtra("status") ?: "new"
        departmentId = intent.getIntExtra("departmentId", 0)
        agencyId = intent.getIntExtra("agencyId", 0)
        role = intent.getStringExtra("role") ?: ""
        source = intent.getStringExtra("source")
            ?: ComplaintDetailActivity.SOURCE_STATUS

        val deptsJson = intent.getStringExtra("departments")
        if (deptsJson != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<Department>>() {}.type
            departmentList = com.google.gson.Gson().fromJson(deptsJson, type) ?: emptyList()
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finishWithResultIfNeeded() }

        findViewById<TextView>(R.id.tvTitle).text    = rawStatus
        findViewById<TextView>(R.id.tvSubtitle).text = "Loading..."

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnPickDate  = findViewById(R.id.btnPickDate)
        btnClearDate = findViewById(R.id.btnClearDate)
        btnViewAll   = findViewById(R.id.btnViewAll)
        progressList = findViewById(R.id.progressList)

        val isFromDashboard = intent.getBooleanExtra("is_from_dashboard", false)
        btnViewAll.visibility = if (isFromDashboard && !isViewAllClicked) View.VISIBLE else View.GONE

        btnViewAll.setOnClickListener {
            if (!::adapter.isInitialized) return@setOnClickListener
            isViewAllClicked = true

            adapter.filterByDateRange(null)
            btnPickDate.text = "📅 Pick Date"
            btnClearDate.visibility = View.GONE
            adapter.filterByDate(null)

            findViewById<TextInputEditText>(R.id.etSearch).setText("")
            adapter.filterById("")

            findViewById<TextView>(R.id.tvSubtitle).text = "Loading..."
            btnViewAll.visibility = View.GONE

            fetchComplaints(rawStatus, departmentId, agencyId, role, source)
        }

        fetchComplaints(rawStatus, departmentId, agencyId, role, source)
    }

    override fun onBackPressed() {
        finishWithResultIfNeeded()
    }

    private fun finishWithResultIfNeeded() {
        if (anyUpdateHappened) {
            setResult(Activity.RESULT_OK, Intent().putExtra("complaint_updated", true))
        }
        finish()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FETCH  —  Agency uses AgencyStatus endpoint; everyone else uses
    //           GetComplainByDepartmentWise (or the alert-time endpoint for
    //           alertCount / resolveCount).
    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchComplaints(
        status: String,
        departmentId: Int,
        agencyId: Int,
        role: String,
        source: String
    ) {
        val roleLower = role.trim().lowercase()
        val isAgency  = roleLower == "agency" || roleLower.contains("agency")

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                progressList.visibility = View.VISIBLE
            }
            try {
                val list = if (status.equals("Complaints", ignoreCase = true) || status.equals("all", ignoreCase = true)) {
                    val allStatuses = listOf("New", "In Process", "Hold", "Resolved", "Cancel", "ReLaunched", "Approved")
                    val deferreds = allStatuses.map { st ->
                        async(Dispatchers.IO) {
                            try {
                                val resp = when {
                                    isAgency -> RetrofitClient.api.getComplaintsByAgencyStatus(agencyId, st)
                                    departmentId == 0 && departmentList.isNotEmpty() -> {
                                        val deptDeferreds = departmentList.map { dept ->
                                            async(Dispatchers.IO) {
                                                try {
                                                    val r = RetrofitClient.api.getComplaintsByDepartmentStatus(dept.departmentId, st)
                                                    if (r.isSuccessful) r.body().orEmpty() else emptyList()
                                                } catch (_: Exception) {
                                                    emptyList()
                                                }
                                            }
                                        }
                                        val combined = deptDeferreds.awaitAll().flatten()
                                        retrofit2.Response.success(combined)
                                    }
                                    else -> RetrofitClient.api.getComplaintsByDepartmentStatus(departmentId, st)
                                }
                                if (resp.isSuccessful) resp.body().orEmpty() else emptyList()
                            } catch (e: Exception) {
                                Log.e("ComplaintList", "Failed to fetch status $st: ${e.message}")
                                emptyList()
                            }
                        }
                    }
                    deferreds.awaitAll().flatten().distinctBy { it.complainFormID }
                } else {
                    val response = when {
                        status == "alertCount" || status == "resolveCount" ->
                            RetrofitClient.api.getComplaintsByDepartment(departmentId)
                        isAgency ->
                            RetrofitClient.api.getComplaintsByAgencyStatus(
                                agencyId,
                                normalizeStatusForApi(status)
                            )
                        departmentId == 0 && departmentList.isNotEmpty() -> {
                            val deptDeferreds = departmentList.map { dept ->
                                async(Dispatchers.IO) {
                                    try {
                                        val r = RetrofitClient.api.getComplaintsByDepartmentStatus(dept.departmentId, normalizeStatusForApi(status))
                                        if (r.isSuccessful) r.body().orEmpty() else emptyList()
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                            }
                            val combinedList = deptDeferreds.awaitAll().flatten().distinctBy { it.complainFormID }
                            retrofit2.Response.success(combinedList)
                        }
                        else ->
                            RetrofitClient.api.getComplaintsByDepartmentStatus(
                                departmentId,
                                normalizeStatusForApi(status)
                            )
                    }
                    if (response.isSuccessful) response.body().orEmpty() else throw Exception("HTTP ${response.code()}")
                }

                // Default sort → latest first
                val rawSortedList = list.sortedByDescending { parseDateSafe(it.callStartTime) }
                allFetchedComplaints = rawSortedList

                var sortedList = rawSortedList

                if (!isViewAllClicked) {
                    // Apply Subtype and Area filters if passed from Admin Dashboard
                    val subtype = intent.getStringExtra("subtype")
                    val area = intent.getStringExtra("area")
                    
                    if (subtype != null && subtype != "All Complaint Subtypes") {
                        sortedList = sortedList.filter { it.complainSubType?.trim().equals(subtype, ignoreCase = true) }
                    }
                    if (area != null && area != "All Areas") {
                        sortedList = sortedList.filter { it.complainArea?.trim().equals(area, ignoreCase = true) }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressList.visibility = View.GONE
                    val dates = intent.getStringArrayListExtra("dates")
                    // If dates filter is present, filter the display count correctly
                    val displayList = if (!dates.isNullOrEmpty() && !isViewAllClicked) {
                        sortedList.filter { item ->
                            val start = item.callStartTime?.trim() ?: ""
                            dates.any { start.startsWith(it) }
                        }
                    } else {
                        sortedList
                    }

                    findViewById<TextView>(R.id.tvSubtitle).text =
                        "Showing ${displayList.size} complaints"

                    if (::adapter.isInitialized) {
                        adapter.updateData(sortedList)
                    } else {
                        adapter = ComplaintAdapter(
                            sortedList.toMutableList(),
                            role,
                            status,
                            source
                        ) { item ->
                            val intent = Intent(this@ComplaintListActivity, ComplaintDetailActivity::class.java).apply {
                                putExtra("complaint", com.google.gson.Gson().toJson(item))
                                putExtra("role", role)
                                putExtra("status", status)
                                putExtra("source", source)
                            }
                            detailLauncher.launch(intent)
                        }
                        if (!dates.isNullOrEmpty() && !isViewAllClicked) {
                            adapter.filterByDateRange(dates)
                        }
                        recyclerView.adapter = adapter
                        setupControls()
                    }
                }
            } catch (e: Exception) {
                Log.e("ComplaintList", "fetchComplaints error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressList.visibility = View.GONE
                }
                showError(e.message ?: "Error")
            }
        }
    }

    /** Maps UI status strings → API-expected values. */
    private fun normalizeStatusForApi(status: String): String {
        return when (status.trim().lowercase(Locale.getDefault())) {
            "new"        -> "New"
            "in process" -> "In Process"
            "hold"       -> "Hold"
            "resolved"   -> "Resolved"
            "cancel"     -> "Cancel"
            "relaunched" -> "ReLaunched"
            "approved"   -> "Approved"
            else         -> status
        }
    }

    private fun parseDateSafe(dateStr: String?): Date {
        return try {
            sdf.parse(dateStr ?: "") ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }

    private fun setupControls() {
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.doOnTextChanged { text, _, _, _ ->
            adapter.filterById(text.toString())
        }

        btnPickDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            picker.addOnPositiveButtonClickListener { millis ->
                val sdfUTC = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdfUTC.timeZone = TimeZone.getTimeZone("UTC")
                val dateStr = sdfUTC.format(Date(millis))

                btnPickDate.text        = "📅 $dateStr"
                btnClearDate.visibility = View.VISIBLE
                adapter.filterByDate(dateStr)
            }

            picker.show(supportFragmentManager, "DATE_PICKER")
        }

        btnClearDate.setOnClickListener {
            btnPickDate.text        = "📅 Pick Date"
            btnClearDate.visibility = View.GONE
            adapter.filterByDate(null)
        }

        // ASC → oldest first
        findViewById<MaterialButton>(R.id.btnAsc).setOnClickListener  { adapter.sortDateAsc()  }
        // DESC → latest first
        findViewById<MaterialButton>(R.id.btnDesc).setOnClickListener { adapter.sortDateDesc() }
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            findViewById<TextView>(R.id.tvSubtitle).text = msg
        }
    }
}