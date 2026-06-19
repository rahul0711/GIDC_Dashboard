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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HeadDashboardActivity : AppCompatActivity() {

    private val TAG = "HeadDashboard"
    private val allStatuses = listOf("New", "In Process", "Hold", "Resolved", "Cancel", "ReLaunched", "Approved")

    // ── View mode ────────────────────────────────────────────────
    enum class ViewMode { COMPLAINTS, APPROVED, CANCELED, TOTAL }
    enum class RangeMode { DAILY, WEEKLY, MONTHLY }
    private var viewMode: ViewMode = ViewMode.COMPLAINTS
    private var rangeMode: RangeMode = RangeMode.DAILY

    // ── User/session data ─────────────────────────────────────────
    private var agencyId: Int     = 0
    private var role: String      = ""
    private var departmentId: Int = 0

    // ── Filter state ──────────────────────────────────────────────
    private var selectedDate: String   = ""
    private var selectedFromDate: String = ""
    private var selectedToDate: String = ""
    private var selectedDeptId: Int    = 0
    private var departmentList: List<Department> = emptyList()

    // ── Date formatters ───────────────────────────────────────────
    private val apiFmt     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val chipFmt    = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val complaintDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    )

    // ── View references (set in onCreate) ─────────────────────────
    private lateinit var progressChart:    ProgressBar
    private lateinit var lineChart:        LineChartView
    private lateinit var chartLegend:      View
    private lateinit var countSummaryRow:  View
    private lateinit var tvError:          TextView
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvTotalApproved:   TextView
    private lateinit var tvTotalCanceled:   TextView
    private lateinit var tvTotalCombined:   TextView
    private lateinit var legendComplaints:  View
    private lateinit var legendApproved:    View
    private lateinit var legendCanceled:    View
    private lateinit var legendTotal:       View
    private lateinit var summaryComplaints: View
    private lateinit var summaryApproved:   View
    private lateinit var summaryCanceled:   View
    private lateinit var summaryTotal:      View
    private lateinit var btnViewComplaints: TextView
    private lateinit var btnViewApproved:   TextView
    private lateinit var btnViewCanceled:   TextView
    private lateinit var btnRangeDaily:     TextView
    private lateinit var btnRangeWeekly:    TextView
    private lateinit var btnRangeMonthly:   TextView

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_head_dashboard)

        agencyId       = intent.getIntExtra("agencyId",     0)
        role           = intent.getStringExtra("role")      ?: ""
        departmentId   = intent.getIntExtra("departmentId", 0)
        selectedDeptId = departmentId

        // Default date: today
        selectedDate = apiFmt.format(Calendar.getInstance().time)
        selectedFromDate = selectedDate
        selectedToDate = selectedDate

        supportActionBar?.setDisplayShowTitleEnabled(false)

        Log.d(TAG, "agencyId=$agencyId role=$role departmentId=$departmentId")

        // ── Bind views ────────────────────────────────────────────
        progressChart    = findViewById(R.id.progressChart)
        lineChart        = findViewById(R.id.lineChart)
        chartLegend      = findViewById(R.id.chartLegend)
        countSummaryRow  = findViewById(R.id.countSummaryRow)
        tvError          = findViewById(R.id.tvChartError)
        tvTotalComplaints = findViewById(R.id.tvTotalComplaints)
        tvTotalApproved   = findViewById(R.id.tvTotalApproved)
        tvTotalCanceled   = findViewById(R.id.tvTotalCanceled)
        tvTotalCombined   = findViewById(R.id.tvTotalCombined)
        legendComplaints  = findViewById(R.id.legendComplaints)
        legendApproved    = findViewById(R.id.legendApproved)
        legendCanceled    = findViewById(R.id.legendCanceled)
        legendTotal       = findViewById(R.id.legendTotal)
        summaryComplaints = findViewById(R.id.summaryComplaints)
        summaryApproved   = findViewById(R.id.summaryApproved)
        summaryCanceled   = findViewById(R.id.summaryCanceled)
        summaryTotal      = findViewById(R.id.summaryTotal)
        btnViewComplaints = findViewById(R.id.btnViewComplaints)
        btnViewApproved   = findViewById(R.id.btnViewApproved)
        btnViewCanceled   = findViewById(R.id.btnViewCanceled)
        btnRangeDaily     = findViewById(R.id.btnRangeDaily)
        btnRangeWeekly    = findViewById(R.id.btnRangeWeekly)
        btnRangeMonthly   = findViewById(R.id.btnRangeMonthly)

        // ── RecyclerView ──────────────────────────────────────────
        val recyclerView = findViewById<RecyclerView>(R.id.rvDepartments)
        val json = intent.getStringExtra("departments")
        if (json != null) {
            val type = object : TypeToken<List<Department>>() {}.type
            departmentList = Gson().fromJson(json, type)

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = DepartmentAdapter(departmentList) { dept ->
                val i = Intent(this, DepartmentStatsActivity::class.java).apply {
                    putExtra("departmentId",   dept.departmentId)
                    putExtra("departmentName", dept.departmentName)
                    putExtra("agencyId",       agencyId)
                    putExtra("role",           role)
                }
                startActivity(i)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            setupDepartmentSpinner()
        }

        setupDatePicker()
        setupRangeButtons()
        setupViewModeButtons()

        val deptsJson = intent.getStringExtra("departments")

        summaryComplaints.setOnClickListener {
            val datesList = ArrayList(buildDateRange(selectedDate, rangeMode))
            val i = Intent(this, ComplaintListActivity::class.java).apply {
                putExtra("status", "Complaints")
                putExtra("departmentId", selectedDeptId)
                putExtra("agencyId", agencyId)
                putExtra("role", role)
                putExtra("departments", deptsJson)
                putStringArrayListExtra("dates", datesList)
                putExtra("is_from_dashboard", true)
            }
            startActivity(i)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        summaryApproved.setOnClickListener {
            val datesList = ArrayList(buildDateRange(selectedDate, rangeMode))
            val i = Intent(this, ComplaintListActivity::class.java).apply {
                putExtra("status", "Approved")
                putExtra("departmentId", selectedDeptId)
                putExtra("agencyId", agencyId)
                putExtra("role", role)
                putExtra("departments", deptsJson)
                putStringArrayListExtra("dates", datesList)
                putExtra("is_from_dashboard", true)
            }
            startActivity(i)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        summaryCanceled.setOnClickListener {
            val datesList = ArrayList(buildDateRange(selectedDate, rangeMode))
            val i = Intent(this, ComplaintListActivity::class.java).apply {
                putExtra("status", "Cancel")
                putExtra("departmentId", selectedDeptId)
                putExtra("agencyId", agencyId)
                putExtra("role", role)
                putExtra("departments", deptsJson)
                putStringArrayListExtra("dates", datesList)
                putExtra("is_from_dashboard", true)
            }
            startActivity(i)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { openLogout() }

    }

    // ─────────────────────────────────────────────────────────────
    // DEPARTMENT SPINNER
    // ─────────────────────────────────────────────────────────────
    private fun setupDepartmentSpinner() {
        val spinner      = findViewById<Spinner>(R.id.spinnerDepartment)
        val displayNames = departmentList.map { it.departmentName }
        val adapter = ArrayAdapter(
            this,
            R.layout.item_spinner_department_selected,
            displayNames
        )
        adapter.setDropDownViewResource(R.layout.item_spinner_department_dropdown)
        spinner.adapter = adapter

        val defaultIndex = departmentList.indexOfFirst { it.departmentId == departmentId }
        if (defaultIndex >= 0) spinner.setSelection(defaultIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val dept = departmentList[pos]
                if (selectedDeptId != dept.departmentId) {
                    selectedDeptId = dept.departmentId
                    loadChartData()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Make spinner behavior explicit: tapping anywhere, including right arrow area, opens dropdown.
        spinner.setOnTouchListener { _, _ ->
            spinner.performClick()
            false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DATE PICKER — single fixed date, tappable to change
    // ─────────────────────────────────────────────────────────────
    private fun setupDatePicker() {
        val tvDate = findViewById<TextView>(R.id.tvSelectedDate)
        val tvFromDate = findViewById<TextView>(R.id.tvFromDate)
        val tvToDate = findViewById<TextView>(R.id.tvToDate)
        val layout = findViewById<View>(R.id.layoutDatePicker)
        val fromLayout = findViewById<View>(R.id.layoutFromDatePicker)
        val toLayout = findViewById<View>(R.id.layoutToDatePicker)

        updateDateChip(tvDate, selectedDate)
        updateDateChip(tvFromDate, selectedFromDate)
        updateDateChip(tvToDate, selectedToDate)
        updateDateSelectionUi()

        layout.setOnClickListener {
            val (y, m, d) = parseDateParts(selectedDate)
            DatePickerDialog(this, { _, yr, mo, dy ->
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", yr, mo + 1, dy)
                selectedFromDate = selectedDate
                selectedToDate = selectedDate
                updateDateChip(tvDate, selectedDate)
                updateDateChip(tvFromDate, selectedFromDate)
                updateDateChip(tvToDate, selectedToDate)
                updateDateSelectionUi()
                loadChartData()
            }, y, m, d).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        }
        fromLayout.setOnClickListener {
            openDatePicker(selectedFromDate) {
                selectedFromDate = it
                updateDateChip(tvFromDate, selectedFromDate)
                updateDateSelectionUi()
                loadChartData()
            }
        }
        toLayout.setOnClickListener {
            openDatePicker(selectedToDate) {
                selectedToDate = it
                updateDateChip(tvToDate, selectedToDate)
                updateDateSelectionUi()
                loadChartData()
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
            if (rangeSelected) R.drawable.bg_date_pill_active else R.drawable.bg_date_pill
        )
    }

    private fun openDatePicker(currentDate: String, onPicked: (String) -> Unit) {
        val (y, m, d) = parseDateParts(currentDate)
        DatePickerDialog(this, { _, yr, mo, dy ->
            onPicked(String.format(Locale.getDefault(), "%04d-%02d-%02d", yr, mo + 1, dy))
        }, y, m, d).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
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
            loadChartData()
        }

        btnRangeDaily.setOnClickListener { applyRange(RangeMode.DAILY) }
        btnRangeWeekly.setOnClickListener { applyRange(RangeMode.WEEKLY) }
        btnRangeMonthly.setOnClickListener { applyRange(RangeMode.MONTHLY) }
        applyRange(RangeMode.DAILY)
    }

    private fun updateDateChip(tv: TextView, dateStr: String) {
        try { tv.text = chipFmt.format(apiFmt.parse(dateStr) ?: Date()) }
        catch (e: Exception) { tv.text = dateStr }
    }

    private fun parseDateParts(dateStr: String): Triple<Int, Int, Int> {
        val p = dateStr.split("-")
        return Triple(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
    }

    // ─────────────────────────────────────────────────────────────
    // VIEW MODE BUTTONS — Complaints / Approved / Canceled
    // ─────────────────────────────────────────────────────────────
    private fun setupViewModeButtons() {
        fun applyMode(selected: ViewMode) {
            viewMode = selected
            listOf(
                btnViewComplaints to ViewMode.COMPLAINTS,
                btnViewApproved   to ViewMode.APPROVED,
                btnViewCanceled   to ViewMode.CANCELED
            ).forEach { (btn, mode) ->
                if (mode == selected) {
                    val activeColor = when (mode) {
                        ViewMode.COMPLAINTS -> "#17A2F3"
                        ViewMode.APPROVED -> "#FF4F87"
                        ViewMode.CANCELED -> "#F59E0B"
                        ViewMode.TOTAL -> "#0B3D91"
                    }
                    btn.setBackgroundColor(Color.parseColor(activeColor))
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#EEF2FF"))
                    btn.setTextColor(Color.parseColor("#8898AA"))
                }
            }
            // If chart data is already loaded, just update the view mode on the chart
            // without re-fetching from server
            if (lineChart.visibility == View.VISIBLE) {
                lineChart.setViewMode(viewMode)
                updateLegendAndSummaryVisibility()
            }
        }

        btnViewComplaints.setOnClickListener { applyMode(ViewMode.COMPLAINTS) }
        btnViewApproved.setOnClickListener   { applyMode(ViewMode.APPROVED)   }
        btnViewCanceled.setOnClickListener   { applyMode(ViewMode.CANCELED)   }

        // Default: Complaints
        applyMode(ViewMode.COMPLAINTS)
    }

    // ─────────────────────────────────────────────────────────────
    // LEGEND & SUMMARY VISIBILITY based on current view mode
    // ─────────────────────────────────────────────────────────────
    private fun updateLegendAndSummaryVisibility() {
        when (viewMode) {
            ViewMode.COMPLAINTS -> {
                legendComplaints.visibility = View.VISIBLE
                legendApproved.visibility = View.GONE
                legendCanceled.visibility = View.GONE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.VISIBLE
                summaryApproved.visibility = View.GONE
                summaryCanceled.visibility = View.GONE
                summaryTotal.visibility = View.GONE
            }
            ViewMode.APPROVED -> {
                legendComplaints.visibility = View.GONE
                legendApproved.visibility = View.VISIBLE
                legendCanceled.visibility = View.GONE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.GONE
                summaryApproved.visibility = View.VISIBLE
                summaryCanceled.visibility = View.GONE
                summaryTotal.visibility = View.GONE
            }
            ViewMode.CANCELED -> {
                legendComplaints.visibility = View.GONE
                legendApproved.visibility = View.GONE
                legendCanceled.visibility = View.VISIBLE
                legendTotal.visibility = View.GONE
                summaryComplaints.visibility = View.GONE
                summaryApproved.visibility = View.GONE
                summaryCanceled.visibility = View.VISIBLE
                summaryTotal.visibility = View.GONE
            }
            ViewMode.TOTAL -> {
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

    // ─────────────────────────────────────────────────────────────
    // CHART DATA — based on selected range mode
    // ─────────────────────────────────────────────────────────────
    private fun loadChartData() {
        progressChart.visibility   = View.VISIBLE
        lineChart.visibility       = View.GONE
        chartLegend.visibility     = View.GONE
        countSummaryRow.visibility = View.GONE
        tvError.visibility         = View.GONE

        Log.d(TAG, "loadChartData departmentId=$selectedDeptId date=$selectedDate range=$rangeMode")

        lifecycleScope.launch {
            try {
                val dates = buildDateRange(selectedDate, rangeMode)
                val complaints = mutableListOf<ComplaintModel>()

                allStatuses.forEach { status ->
                    val resp = RetrofitClient.api.getComplaintsByDepartmentStatus(selectedDeptId, status)
                    if (resp.isSuccessful) {
                        complaints += resp.body().orEmpty()
                    } else {
                        Log.w(TAG, "Status fetch failed dept=$selectedDeptId status=$status code=${resp.code()}")
                    }
                }

                val deduped = complaints.distinctBy { "${it.complainFormID}_${it.status}" }
                val groupedByDate = deduped.groupBy { normalizeComplaintDate(it.callStartTime) }

                val points = dates.map { dateStr ->
                    val dayItems = groupedByDate[dateStr].orEmpty()
                    LineChartView.DataPoint(
                        label = formatPointLabel(dateStr),
                        complaints = dayItems.size,
                        approved = dayItems.count { it.status.equals("Approved", ignoreCase = true) },
                        canceled = dayItems.count { it.status.equals("Cancel", ignoreCase = true) }
                    )
                }

                progressChart.visibility   = View.GONE
                lineChart.visibility       = View.VISIBLE
                chartLegend.visibility     = View.VISIBLE
                countSummaryRow.visibility = View.VISIBLE

                lineChart.setViewMode(viewMode)
                lineChart.setData(points)

                val totalComplaints = points.sumOf { it.complaints }
                val totalApproved = points.sumOf { it.approved }
                val totalCanceled = points.sumOf { it.canceled }
                val totalCombined = points.sumOf { it.total }
                tvTotalComplaints.text = totalComplaints.toString()
                tvTotalApproved.text = totalApproved.toString()
                tvTotalCanceled.text = totalCanceled.toString()
                tvTotalCombined.text = totalCombined.toString()

                updateLegendAndSummaryVisibility()

            } catch (e: Exception) {
                Log.e(TAG, "Chart load exception: ${e.message}", e)
                showChartError("Failed to load: ${e.message}")
            }
        }
    }

    private fun normalizeComplaintDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        complaintDateFormats.forEach { format ->
            try {
                val parsed = format.parse(raw.trim())
                if (parsed != null) return apiFmt.format(parsed)
            } catch (_: Exception) {
            }
        }
        return raw.trim().take(10).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    }

    private fun buildDateRange(anchorDate: String, mode: RangeMode): List<String> {
        if (selectedFromDate != selectedToDate) {
            val start = apiFmt.parse(minOf(selectedFromDate, selectedToDate)) ?: Date()
            val end = apiFmt.parse(maxOf(selectedFromDate, selectedToDate)) ?: start
            val cursor = Calendar.getInstance().apply { time = start }
            val last = Calendar.getInstance().apply { time = end }
            val list = mutableListOf<String>()
            while (!cursor.after(last)) {
                list += apiFmt.format(cursor.time)
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
            return list
        }
        val anchor = apiFmt.parse(anchorDate) ?: Date()
        val cal = Calendar.getInstance().apply { time = anchor }

        return when (mode) {
            RangeMode.DAILY -> listOf(anchorDate)
            RangeMode.WEEKLY -> {
                val list = mutableListOf<String>()
                repeat(7) {
                    list += apiFmt.format(cal.time)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                list.reversed()
            }
            RangeMode.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val first = cal.time
                val cursor = Calendar.getInstance().apply { time = first }
                val end = Calendar.getInstance().apply { time = anchor }
                val list = mutableListOf<String>()
                while (!cursor.after(end)) {
                    list += apiFmt.format(cursor.time)
                    cursor.add(Calendar.DAY_OF_MONTH, 1)
                }
                list
            }
        }
    }

    private fun formatPointLabel(dateStr: String): String {
        return try {
            val dt = apiFmt.parse(dateStr) ?: Date()
            if (selectedFromDate != selectedToDate) {
                return SimpleDateFormat("dd MMM", Locale.getDefault()).format(dt)
            }
            when (rangeMode) {
                RangeMode.DAILY -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(dt)
                RangeMode.WEEKLY -> SimpleDateFormat("EEE", Locale.getDefault()).format(dt)
                RangeMode.MONTHLY -> SimpleDateFormat("dd", Locale.getDefault()).format(dt)
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun showChartError(msg: String) {
        progressChart.visibility = View.GONE
        tvError.text             = msg
        tvError.visibility       = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────
    private fun openLogout() {
        val i = Intent(this, LogoutActivity::class.java).apply {
            putExtra("role",     role)
            putExtra("agencyId", agencyId)
        }
        startActivity(i)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}