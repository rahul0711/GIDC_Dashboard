package com.ScriptIndia.GIDC_CMS_APP.dashboard

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ScriptIndia.GIDC_CMS_APP.R
import com.ScriptIndia.GIDC_CMS_APP.api.RetrofitClient
import com.ScriptIndia.GIDC_CMS_APP.model.ComplaintModel
import com.ScriptIndia.GIDC_CMS_APP.model.Department
import com.ScriptIndia.GIDC_CMS_APP.settings.LogoutActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    private val TAG = "AdminDashboard"
    private val allStatuses = listOf("New", "In Process", "Hold", "Resolved", "Cancel", "ReLaunched", "Approved")

    private enum class RangeMode { DAILY, WEEKLY, MONTHLY }

    private data class ComplaintWithDepartment(
        val complaint: ComplaintModel,
        val departmentId: Int,
        val departmentName: String
    )

    private var agencyId: Int = 0
    private var role: String = ""
    private var departmentList: List<Department> = emptyList()
    private var selectedDepartmentId: Int? = null
    private var viewMode: HeadDashboardActivity.ViewMode = HeadDashboardActivity.ViewMode.COMPLAINTS
    private var selectedSubType: String = "All Complaint Subtypes"
    private var selectedArea: String = "All Areas"
    private var selectedDate: String = ""
    private var selectedFromDate: String = ""
    private var selectedToDate: String = ""
    private var rangeMode: RangeMode = RangeMode.DAILY
    private var allComplaints: List<ComplaintWithDepartment> = emptyList()

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val complaintDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    )

    private lateinit var progressChart: ProgressBar
    private lateinit var lineChart: LineChartView
    private lateinit var chartLegend: View
    private lateinit var countSummaryRow: View
    private lateinit var tvError: TextView
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvTotalApproved: TextView
    private lateinit var tvTotalCanceled: TextView
    private lateinit var tvTotalCombined: TextView
    private lateinit var legendComplaints: View
    private lateinit var legendApproved: View
    private lateinit var legendCanceled: View
    private lateinit var legendTotal: View
    private lateinit var summaryComplaints: View
    private lateinit var summaryApproved: View
    private lateinit var summaryCanceled: View
    private lateinit var summaryTotal: View
    private lateinit var btnViewComplaints: TextView
    private lateinit var btnViewApproved: TextView
    private lateinit var btnViewCanceled: TextView
    private lateinit var btnRangeDaily: TextView
    private lateinit var btnRangeWeekly: TextView
    private lateinit var btnRangeMonthly: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvFromDate: TextView
    private lateinit var tvToDate: TextView
    private lateinit var btnToggleFilters: MaterialButton
    private lateinit var layoutFiltersPanel: View
    private lateinit var spinnerDepartmentFilter: Spinner
    private lateinit var spinnerSubtypeFilter: Spinner
    private lateinit var spinnerAreaFilter: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        agencyId = intent.getIntExtra("agencyId", 0)
        role = intent.getStringExtra("role") ?: ""
        if (!role.contains("admin", ignoreCase = true)) {
            Toast.makeText(this, "This dashboard is only for admin users", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        selectedDate = apiDateFormat.format(Calendar.getInstance().time)
        selectedFromDate = selectedDate
        selectedToDate = selectedDate

        bindViews()
        setupViewModeButtons()
        setupRangeButtons()
        setupDatePicker()
        setupFilterPanelToggle()

        val recyclerView = findViewById<RecyclerView>(R.id.rvDepartments)
        val json = intent.getStringExtra("departments")
        if (json != null) {
            val type = object : TypeToken<List<Department>>() {}.type
            departmentList = Gson().fromJson(json, type) ?: emptyList()

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = DepartmentAdapter(departmentList) { dept ->
                val intent = Intent(this, DepartmentStatsActivity::class.java).apply {
                    putExtra("departmentId", dept.departmentId)
                    putExtra("departmentName", dept.departmentName)
                    putExtra("agencyId", agencyId)
                    putExtra("role", role)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        setupDepartmentSpinner()
        setupStaticFilterSpinners()
        loadAdminData()

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { openLogout() }
    }

    private fun bindViews() {
        progressChart = findViewById(R.id.progressChart)
        lineChart = findViewById(R.id.lineChart)
        chartLegend = findViewById(R.id.chartLegend)
        countSummaryRow = findViewById(R.id.countSummaryRow)
        tvError = findViewById(R.id.tvChartError)
        tvTotalComplaints = findViewById(R.id.tvTotalComplaints)
        tvTotalApproved = findViewById(R.id.tvTotalApproved)
        tvTotalCanceled = findViewById(R.id.tvTotalCanceled)
        tvTotalCombined = findViewById(R.id.tvTotalCombined)
        legendComplaints = findViewById(R.id.legendComplaints)
        legendApproved = findViewById(R.id.legendApproved)
        legendCanceled = findViewById(R.id.legendCanceled)
        legendTotal = findViewById(R.id.legendTotal)
        summaryComplaints = findViewById(R.id.summaryComplaints)
        summaryApproved = findViewById(R.id.summaryApproved)
        summaryCanceled = findViewById(R.id.summaryCanceled)
        summaryTotal = findViewById(R.id.summaryTotal)
        btnViewComplaints = findViewById(R.id.btnViewComplaints)
        btnViewApproved = findViewById(R.id.btnViewApproved)
        btnViewCanceled = findViewById(R.id.btnViewCanceled)
        btnRangeDaily = findViewById(R.id.btnRangeDaily)
        btnRangeWeekly = findViewById(R.id.btnRangeWeekly)
        btnRangeMonthly = findViewById(R.id.btnRangeMonthly)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvFromDate = findViewById(R.id.tvFromDate)
        tvToDate = findViewById(R.id.tvToDate)
        btnToggleFilters = findViewById(R.id.btnToggleFilters)
        layoutFiltersPanel = findViewById(R.id.layoutFiltersPanel)
        spinnerDepartmentFilter = findViewById(R.id.spinnerDepartmentFilter)
        spinnerSubtypeFilter = findViewById(R.id.spinnerSubtypeFilter)
        spinnerAreaFilter = findViewById(R.id.spinnerAreaFilter)
    }

    private fun setupFilterPanelToggle() {
        btnToggleFilters.setOnClickListener {
            val expanded = layoutFiltersPanel.visibility == View.VISIBLE
            layoutFiltersPanel.visibility = if (expanded) View.GONE else View.VISIBLE
            btnToggleFilters.text = if (expanded) "Show Filters" else "Hide Filters"
        }
    }

    private fun setupDepartmentSpinner() {
        val names = listOf("All Departments") + departmentList.map { it.departmentName }
        val adapter = FilterSpinnerAdapter(this, names)
        spinnerDepartmentFilter.adapter = adapter
        spinnerDepartmentFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDepartmentId = if (position == 0) null else departmentList.getOrNull(position - 1)?.departmentId
                refreshDependentFilters()
                renderAdminChart()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupStaticFilterSpinners() {
        spinnerSubtypeFilter.adapter = FilterSpinnerAdapter(this, listOf(selectedSubType))
        spinnerSubtypeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSubType = parent?.getItemAtPosition(position)?.toString() ?: selectedSubType
                refreshAreaFilter()
                renderAdminChart()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerAreaFilter.adapter = FilterSpinnerAdapter(this, listOf(selectedArea))
        spinnerAreaFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedArea = parent?.getItemAtPosition(position)?.toString() ?: selectedArea
                renderAdminChart()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupViewModeButtons() {
        fun applyMode(selected: HeadDashboardActivity.ViewMode) {
            viewMode = selected
            listOf(
                btnViewComplaints to HeadDashboardActivity.ViewMode.COMPLAINTS,
                btnViewApproved to HeadDashboardActivity.ViewMode.APPROVED,
                btnViewCanceled to HeadDashboardActivity.ViewMode.CANCELED
            ).forEach { (btn, mode) ->
                if (mode == selected) {
                    val activeColor = when (mode) {
                        HeadDashboardActivity.ViewMode.COMPLAINTS -> "#17A2F3"
                        HeadDashboardActivity.ViewMode.APPROVED -> "#FF4F87"
                        HeadDashboardActivity.ViewMode.CANCELED -> "#F59E0B"
                        HeadDashboardActivity.ViewMode.TOTAL -> "#0B3D91"
                    }
                    btn.setBackgroundColor(Color.parseColor(activeColor))
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#EEF2FF"))
                    btn.setTextColor(Color.parseColor("#8898AA"))
                }
            }
            if (::lineChart.isInitialized) {
                lineChart.setViewMode(viewMode)
                updateLegendAndSummaryVisibility()
            }
        }

        btnViewComplaints.setOnClickListener { applyMode(HeadDashboardActivity.ViewMode.COMPLAINTS) }
        btnViewApproved.setOnClickListener { applyMode(HeadDashboardActivity.ViewMode.APPROVED) }
        btnViewCanceled.setOnClickListener { applyMode(HeadDashboardActivity.ViewMode.CANCELED) }
        applyMode(HeadDashboardActivity.ViewMode.COMPLAINTS)
    }

    private fun setupRangeButtons() {
        fun applyRange(selected: RangeMode) {
            rangeMode = selected
            listOf(
                btnRangeDaily to RangeMode.DAILY,
                btnRangeWeekly to RangeMode.WEEKLY,
                btnRangeMonthly to RangeMode.MONTHLY
            ).forEach { (btn, mode) ->
                if (mode == selected) {
                    btn.setBackgroundColor(Color.parseColor("#0B3D91"))
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#EEF2FF"))
                    btn.setTextColor(Color.parseColor("#8898AA"))
                }
            }
            renderAdminChart()
        }

        btnRangeDaily.setOnClickListener { applyRange(RangeMode.DAILY) }
        btnRangeWeekly.setOnClickListener { applyRange(RangeMode.WEEKLY) }
        btnRangeMonthly.setOnClickListener { applyRange(RangeMode.MONTHLY) }
        applyRange(RangeMode.DAILY)
    }

    private fun setupDatePicker() {
        tvSelectedDate.text = displayDateFormat.format(apiDateFormat.parse(selectedDate) ?: Date())
        tvFromDate.text = displayDateFormat.format(apiDateFormat.parse(selectedFromDate) ?: Date())
        tvToDate.text = displayDateFormat.format(apiDateFormat.parse(selectedToDate) ?: Date())
        updateDateSelectionUi()
        findViewById<View>(R.id.layoutDatePicker).setOnClickListener {
            val parts = selectedDate.split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
            val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
            DatePickerDialog(this, { _, y, m, d ->
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                selectedFromDate = selectedDate
                selectedToDate = selectedDate
                tvSelectedDate.text = displayDateFormat.format(apiDateFormat.parse(selectedDate) ?: Date())
                tvFromDate.text = displayDateFormat.format(apiDateFormat.parse(selectedFromDate) ?: Date())
                tvToDate.text = displayDateFormat.format(apiDateFormat.parse(selectedToDate) ?: Date())
                updateDateSelectionUi()
                renderAdminChart()
            }, year, month, day).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        }
        findViewById<View>(R.id.layoutFromDatePicker).setOnClickListener {
            openDatePicker(selectedFromDate) { picked ->
                selectedFromDate = picked
                tvFromDate.text = displayDateFormat.format(apiDateFormat.parse(selectedFromDate) ?: Date())
                updateDateSelectionUi()
                renderAdminChart()
            }
        }
        findViewById<View>(R.id.layoutToDatePicker).setOnClickListener {
            openDatePicker(selectedToDate) { picked ->
                selectedToDate = picked
                tvToDate.text = displayDateFormat.format(apiDateFormat.parse(selectedToDate) ?: Date())
                updateDateSelectionUi()
                renderAdminChart()
            }
        }
    }

    private fun updateDateSelectionUi() {
        val singleSelected = selectedFromDate == selectedToDate && selectedDate == selectedFromDate
        findViewById<View>(R.id.layoutDatePicker).setBackgroundResource(
            if (singleSelected) R.drawable.bg_date_pill_active else R.drawable.bg_date_pill
        )
        val rangeSelected = selectedFromDate != selectedToDate
        findViewById<View>(R.id.layoutFromDatePicker).setBackgroundResource(
            if (rangeSelected) R.drawable.bg_date_pill_active else R.drawable.bg_date_pill
        )
        findViewById<View>(R.id.layoutToDatePicker).setBackgroundResource(
            if (rangeSelected) R.drawable.badge_bg else R.drawable.bg_date_pill
        )
    }

    private fun openDatePicker(currentDate: String, onPicked: (String) -> Unit) {
        val parts = currentDate.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        DatePickerDialog(this, { _, y, m, d ->
            onPicked(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d))
        }, year, month, day).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    private fun loadAdminData() {
        progressChart.visibility = View.VISIBLE
        lineChart.visibility = View.GONE
        chartLegend.visibility = View.GONE
        countSummaryRow.visibility = View.GONE
        tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                allComplaints = withContext(Dispatchers.IO) {
                    departmentList.map { dept ->
                        async {
                            allStatuses.map { status ->
                                async {
                                    val response = RetrofitClient.api.getComplaintsByDepartmentStatus(dept.departmentId, status)
                                    if (!response.isSuccessful) {
                                        Log.e(TAG, "Complaint list failed for dept=${dept.departmentId} status=$status code=${response.code()}")
                                        emptyList()
                                    } else {
                                        val body = response.body() ?: emptyList()

                                        // Some backends may return mixed department records even when a department-id filter is applied.
                                        // If `ComplaintModel.departmentId` is present in the response, we enforce the match here so that
                                        // the "Complaint Subtype" dropdown is populated correctly for the selected department.
                                        val hasDepartmentIdInfo = body.any { !it.departmentId.isNullOrBlank() }
                                        val filteredBody = if (hasDepartmentIdInfo) {
                                            body.filter { it.departmentId?.toIntOrNull() == dept.departmentId }
                                        } else {
                                            body
                                        }

                                        if (hasDepartmentIdInfo && filteredBody.size != body.size) {
                                            Log.w(
                                                TAG,
                                                "DeptId mismatch in response: dept=${dept.departmentId} status=$status received=${body.size} kept=${filteredBody.size}"
                                            )
                                        }

                                        filteredBody.map {
                                            ComplaintWithDepartment(
                                                complaint = it,
                                                departmentId = dept.departmentId,
                                                departmentName = dept.departmentName
                                            )
                                        }
                                    }
                                }
                            }.awaitAll().flatten()
                        }
                    }.awaitAll().flatten()
                }

                allComplaints = allComplaints.distinctBy { "${it.departmentId}_${it.complaint.complainFormID}_${it.complaint.status}" }
                refreshDependentFilters()
                renderAdminChart()
            } catch (e: Exception) {
                Log.e(TAG, "loadAdminData failed: ${e.message}", e)
                showChartError("Failed to load admin filters")
            }
        }
    }

    private fun refreshDependentFilters() {
        refreshSubtypeFilter()
        refreshAreaFilter()
    }

    private fun refreshSubtypeFilter() {
        val subtypeOptions = listOf("All Complaint Subtypes") + filterByDepartment(allComplaints)
            .mapNotNull { it.complaint.complainSubType?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
        if (selectedSubType !in subtypeOptions) selectedSubType = "All Complaint Subtypes"
        spinnerSubtypeFilter.adapter = FilterSpinnerAdapter(this, subtypeOptions)
        spinnerSubtypeFilter.setSelection(subtypeOptions.indexOf(selectedSubType).coerceAtLeast(0), false)
    }

    private fun refreshAreaFilter() {
        val filtered = filterBySubType(filterByDepartment(allComplaints))
        val areaOptions = listOf("All Areas") + filtered
            .mapNotNull { it.complaint.complainArea?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
        if (selectedArea !in areaOptions) selectedArea = "All Areas"
        spinnerAreaFilter.adapter = FilterSpinnerAdapter(this, areaOptions)
        spinnerAreaFilter.setSelection(areaOptions.indexOf(selectedArea).coerceAtLeast(0), false)
    }

    private fun renderAdminChart() {
        if (!::progressChart.isInitialized || allComplaints.isEmpty()) {
            if (::progressChart.isInitialized) showChartError("No complaint data available")
            return
        }

        val filtered = filterByDateRange(
            filterByArea(
                filterBySubType(
                    filterByDepartment(allComplaints)
                )
            )
        )
        val dates = buildDateRange(selectedDate, rangeMode)
        val grouped = filtered.groupBy { normalizeComplaintDate(it.complaint.callStartTime) }
        val points = dates.map { date ->
            val items = grouped[date].orEmpty()
            LineChartView.DataPoint(
                label = formatPointLabel(date),
                complaints = items.size,
                approved = items.count { it.complaint.status.equals("Approved", ignoreCase = true) },
                canceled = items.count { it.complaint.status.equals("Cancel", ignoreCase = true) }
            )
        }

        progressChart.visibility = View.GONE
        tvError.visibility = View.GONE
        lineChart.visibility = View.VISIBLE
        chartLegend.visibility = View.VISIBLE
        countSummaryRow.visibility = View.VISIBLE
        lineChart.setViewMode(viewMode)
        lineChart.setData(points)
        tvTotalComplaints.text = filtered.size.toString()
        tvTotalApproved.text = filtered.count { it.complaint.status.equals("Approved", ignoreCase = true) }.toString()
        tvTotalCanceled.text = filtered.count { it.complaint.status.equals("Cancel", ignoreCase = true) }.toString()
        tvTotalCombined.text = points.sumOf { it.total }.toString()
        updateLegendAndSummaryVisibility()
    }

    private fun filterByDepartment(source: List<ComplaintWithDepartment>): List<ComplaintWithDepartment> {
        val selectedId = selectedDepartmentId ?: return source
        return source.filter { it.departmentId == selectedId }
    }

    private fun filterBySubType(source: List<ComplaintWithDepartment>): List<ComplaintWithDepartment> {
        if (selectedSubType == "All Complaint Subtypes") return source
        return source.filter { it.complaint.complainSubType?.trim().equals(selectedSubType, ignoreCase = true) }
    }

    private fun filterByArea(source: List<ComplaintWithDepartment>): List<ComplaintWithDepartment> {
        if (selectedArea == "All Areas") return source
        return source.filter { it.complaint.complainArea?.trim().equals(selectedArea, ignoreCase = true) }
    }

    private fun filterByDateRange(source: List<ComplaintWithDepartment>): List<ComplaintWithDepartment> {
        val validDates = buildDateRange().toSet()
        return source.filter { normalizeComplaintDate(it.complaint.callStartTime) in validDates }
    }

    private fun buildDateRange(anchorDate: String = selectedDate, mode: RangeMode = rangeMode): List<String> {
        if (selectedFromDate != selectedToDate) {
            val start = apiDateFormat.parse(minOf(selectedFromDate, selectedToDate)) ?: Date()
            val end = apiDateFormat.parse(maxOf(selectedFromDate, selectedToDate)) ?: start
            val cursor = Calendar.getInstance().apply { time = start }
            val last = Calendar.getInstance().apply { time = end }
            val list = mutableListOf<String>()
            while (!cursor.after(last)) {
                list += apiDateFormat.format(cursor.time)
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
            return list
        }

        val anchor = apiDateFormat.parse(anchorDate) ?: Date()
        val cal = Calendar.getInstance().apply { time = anchor }
        return when (mode) {
            RangeMode.DAILY -> listOf(anchorDate)
            RangeMode.WEEKLY -> {
                val list = mutableListOf<String>()
                repeat(7) {
                    list += apiDateFormat.format(cal.time)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                list.reversed()
            }
            RangeMode.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.time
                val cursor = Calendar.getInstance().apply { time = start }
                val end = Calendar.getInstance().apply { time = anchor }
                val list = mutableListOf<String>()
                while (!cursor.after(end)) {
                    list += apiDateFormat.format(cursor.time)
                    cursor.add(Calendar.DAY_OF_MONTH, 1)
                }
                list
            }
        }
    }

    private fun formatPointLabel(dateStr: String): String {
        return try {
            val date = apiDateFormat.parse(dateStr) ?: Date()
            if (selectedFromDate != selectedToDate) {
                return SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
            }
            when (rangeMode) {
                RangeMode.DAILY -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
                RangeMode.WEEKLY -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
                RangeMode.MONTHLY -> SimpleDateFormat("dd", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            dateStr
        }
    }

    private fun normalizeComplaintDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        complaintDateFormats.forEach { format ->
            try {
                val parsed = format.parse(raw.trim())
                if (parsed != null) return apiDateFormat.format(parsed)
            } catch (_: Exception) {
            }
        }
        return raw.trim().take(10).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    }

    private class FilterSpinnerAdapter(
        activity: AppCompatActivity,
        items: List<String>
    ) : ArrayAdapter<String>(activity, R.layout.item_spinner_filter_selected, items) {

        init {
            setDropDownViewResource(R.layout.item_spinner_filter_dropdown)
        }
    }

    private fun updateLegendAndSummaryVisibility() {
        when (viewMode) {
            HeadDashboardActivity.ViewMode.COMPLAINTS -> {
                legendComplaints.visibility = View.VISIBLE
                legendApproved.visibility = View.GONE
                legendCanceled.visibility = View.GONE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.VISIBLE
                summaryApproved.visibility = View.GONE
                summaryCanceled.visibility = View.GONE
                summaryTotal.visibility = View.GONE
            }
            HeadDashboardActivity.ViewMode.APPROVED -> {
                legendComplaints.visibility = View.GONE
                legendApproved.visibility = View.VISIBLE
                legendCanceled.visibility = View.GONE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.GONE
                summaryApproved.visibility = View.VISIBLE
                summaryCanceled.visibility = View.GONE
                summaryTotal.visibility = View.GONE
            }
            HeadDashboardActivity.ViewMode.CANCELED -> {
                legendComplaints.visibility = View.GONE
                legendApproved.visibility = View.GONE
                legendCanceled.visibility = View.VISIBLE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.GONE
                summaryApproved.visibility = View.GONE
                summaryCanceled.visibility = View.VISIBLE
                summaryTotal.visibility = View.GONE
            }
            HeadDashboardActivity.ViewMode.TOTAL -> {
                legendComplaints.visibility = View.VISIBLE
                legendApproved.visibility = View.VISIBLE
                legendCanceled.visibility = View.VISIBLE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.VISIBLE
                summaryApproved.visibility = View.VISIBLE
                summaryCanceled.visibility = View.VISIBLE
                summaryTotal.visibility = View.VISIBLE
            }
        }
    }

    private fun showChartError(message: String) {
        progressChart.visibility = View.GONE
        lineChart.visibility = View.GONE
        chartLegend.visibility = View.GONE
        countSummaryRow.visibility = View.GONE
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun openLogout() {
        val intent = Intent(this, LogoutActivity::class.java).apply {
            putExtra("role", role)
            putExtra("agencyId", agencyId)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}