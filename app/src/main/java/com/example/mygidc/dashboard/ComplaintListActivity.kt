package com.example.mygidc.dashboard

import android.os.Bundle
import android.util.Log
import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mygidc.R
import com.example.mygidc.api.RetrofitClient
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

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var rawStatus: String
    private var departmentId: Int = 0
    private var agencyId: Int = 0
    private lateinit var role: String
    private lateinit var source: String
    private var anyUpdateHappened: Boolean = false

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
            try {
                val response = when {
                    // Alert / resolve counts → department-alert endpoint (unchanged)
                    status == "alertCount" || status == "resolveCount" ->
                        RetrofitClient.api.getComplaintsByDepartment(departmentId)

                    // Agency → existing agency+status endpoint
                    isAgency ->
                        RetrofitClient.api.getComplaintsByAgencyStatus(
                            agencyId,
                            normalizeStatusForApi(status)
                        )

                    // Admin / Engineer / Head → new department+status endpoint
                    else ->
                        RetrofitClient.api.getComplaintsByDepartmentStatus(
                            departmentId,
                            normalizeStatusForApi(status)
                        )
                }

                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()

                    // Default sort → latest first
                    val sortedList = list.sortedByDescending { parseDateSafe(it.callStartTime) }

                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.tvSubtitle).text =
                            "Showing ${sortedList.size} complaints"

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
                            recyclerView.adapter = adapter
                            setupControls()
                        }
                    }
                } else {
                    showError("Failed: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e("ComplaintList", "fetchComplaints error: ${e.message}", e)
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