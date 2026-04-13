package com.example.mygidc.dashboard

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complaint_list)

        val rawStatus    = intent.getStringExtra("status")  ?: "new"
        val departmentId = intent.getIntExtra("departmentId", 0)
        val role         = intent.getStringExtra("role") ?: ""
        val source       = intent.getStringExtra("source")
            ?: ComplaintDetailActivity.SOURCE_STATUS

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.tvTitle).text = rawStatus
        findViewById<TextView>(R.id.tvSubtitle).text = "Loading..."

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnPickDate  = findViewById(R.id.btnPickDate)
        btnClearDate = findViewById(R.id.btnClearDate)

        fetchComplaints(rawStatus, departmentId, role, source)
    }

    private fun fetchComplaints(
        status: String,
        departmentId: Int,
        role: String,
        source: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = when (status) {
                    "alertCount", "resolveCount" ->
                        RetrofitClient.api.getComplaintsByDepartment(departmentId)

                    else ->
                        RetrofitClient.api.getComplaintsByDepartmentStatus(departmentId, status)
                }

                if (response.isSuccessful) {

                    val list = response.body() ?: emptyList()

                    // ✅ DEFAULT SORT → LATEST FIRST
                    val sortedList = list.sortedByDescending {
                        parseDateSafe(it.callStartTime)
                    }

                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.tvSubtitle).text =
                            "Showing ${sortedList.size} complaints"

                        adapter = ComplaintAdapter(
                            sortedList.toMutableList(),
                            role,
                            status,
                            source
                        )
                        recyclerView.adapter = adapter

                        setupControls()
                    }

                } else {
                    showError("Failed: ${response.code()}")
                }

            } catch (e: Exception) {
                showError(e.message ?: "Error")
            }
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

                btnPickDate.text = "📅 $dateStr"
                btnClearDate.visibility = View.VISIBLE

                adapter.filterByDate(dateStr)
            }

            picker.show(supportFragmentManager, "DATE_PICKER")
        }

        btnClearDate.setOnClickListener {
            btnPickDate.text = "📅 Pick Date"
            btnClearDate.visibility = View.GONE
            adapter.filterByDate(null)
        }

        // ✅ ASC → OLDEST FIRST
        findViewById<MaterialButton>(R.id.btnAsc).setOnClickListener {
            adapter.sortDateAsc()
        }

        // ✅ DESC → LATEST FIRST
        findViewById<MaterialButton>(R.id.btnDesc).setOnClickListener {
            adapter.sortDateDesc()
        }
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            findViewById<TextView>(R.id.tvSubtitle).text = msg
        }
    }
}