package com.example.mygidc.dashboard

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
import com.example.mygidc.R
import com.example.mygidc.api.RetrofitClient
import com.example.mygidc.model.Department
import com.example.mygidc.settings.LogoutActivity
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

    // ── View mode ────────────────────────────────────────────────
    enum class ViewMode { COMPLAINTS, RESOLVED, BOTH }
    enum class RangeMode { DAILY, WEEKLY, MONTHLY }
    private var viewMode: ViewMode = ViewMode.BOTH
    private var rangeMode: RangeMode = RangeMode.DAILY

    // ── User/session data ─────────────────────────────────────────
    private var agencyId: Int     = 0
    private var role: String      = ""
    private var departmentId: Int = 0

    // ── Filter state ──────────────────────────────────────────────
    private var selectedDate: String   = ""
    private var selectedDeptId: Int    = 0
    private var departmentList: List<Department> = emptyList()

    // ── Date formatters ───────────────────────────────────────────
    private val apiFmt     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val chipFmt    = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ── View references (set in onCreate) ─────────────────────────
    private lateinit var progressChart:    ProgressBar
    private lateinit var lineChart:        LineChartView
    private lateinit var chartLegend:      View
    private lateinit var countSummaryRow:  View
    private lateinit var tvError:          TextView
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvTotalResolved:   TextView
    private lateinit var legendComplaints:  View
    private lateinit var legendResolved:    View
    private lateinit var summaryComplaints: View
    private lateinit var summaryResolved:   View
    private lateinit var btnViewComplaints: TextView
    private lateinit var btnViewResolved:   TextView
    private lateinit var btnViewBoth:       TextView
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

        supportActionBar?.setDisplayShowTitleEnabled(false)

        Log.d(TAG, "agencyId=$agencyId role=$role departmentId=$departmentId")

        // ── Bind views ────────────────────────────────────────────
        progressChart    = findViewById(R.id.progressChart)
        lineChart        = findViewById(R.id.lineChart)
        chartLegend      = findViewById(R.id.chartLegend)
        countSummaryRow  = findViewById(R.id.countSummaryRow)
        tvError          = findViewById(R.id.tvChartError)
        tvTotalComplaints = findViewById(R.id.tvTotalComplaints)
        tvTotalResolved   = findViewById(R.id.tvTotalResolved)
        legendComplaints  = findViewById(R.id.legendComplaints)
        legendResolved    = findViewById(R.id.legendResolved)
        summaryComplaints = findViewById(R.id.summaryComplaints)
        summaryResolved   = findViewById(R.id.summaryResolved)
        btnViewComplaints = findViewById(R.id.btnViewComplaints)
        btnViewResolved   = findViewById(R.id.btnViewResolved)
        btnViewBoth       = findViewById(R.id.btnViewBoth)
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
        val layout = findViewById<View>(R.id.layoutDatePicker)

        updateDateChip(tvDate, selectedDate)

        layout.setOnClickListener {
            val (y, m, d) = parseDateParts(selectedDate)
            DatePickerDialog(this, { _, yr, mo, dy ->
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", yr, mo + 1, dy)
                updateDateChip(tvDate, selectedDate)
                loadChartData()
            }, y, m, d).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
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
    // VIEW MODE BUTTONS — Complaints / Resolved / Both
    // ─────────────────────────────────────────────────────────────
    private fun setupViewModeButtons() {
        fun applyMode(selected: ViewMode) {
            viewMode = selected
            listOf(
                btnViewComplaints to ViewMode.COMPLAINTS,
                btnViewResolved   to ViewMode.RESOLVED,
                btnViewBoth       to ViewMode.BOTH
            ).forEach { (btn, mode) ->
                if (mode == selected) {
                    val activeColor = when (mode) {
                        ViewMode.COMPLAINTS -> "#17A2F3"
                        ViewMode.RESOLVED -> "#FF4F87"
                        ViewMode.BOTH -> "#0B3D91"
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
        btnViewResolved.setOnClickListener   { applyMode(ViewMode.RESOLVED)   }
        btnViewBoth.setOnClickListener       { applyMode(ViewMode.BOTH)       }

        // Default: Both
        applyMode(ViewMode.BOTH)
    }

    // ─────────────────────────────────────────────────────────────
    // LEGEND & SUMMARY VISIBILITY based on current view mode
    // ─────────────────────────────────────────────────────────────
    private fun updateLegendAndSummaryVisibility() {
        when (viewMode) {
            ViewMode.COMPLAINTS -> {
                legendComplaints.visibility  = View.VISIBLE
                legendResolved.visibility    = View.GONE
                summaryComplaints.visibility = View.VISIBLE
                summaryResolved.visibility   = View.GONE
            }
            ViewMode.RESOLVED -> {
                legendComplaints.visibility  = View.GONE
                legendResolved.visibility    = View.VISIBLE
                summaryComplaints.visibility = View.GONE
                summaryResolved.visibility   = View.VISIBLE
            }
            ViewMode.BOTH -> {
                legendComplaints.visibility  = View.VISIBLE
                legendResolved.visibility    = View.VISIBLE
                summaryComplaints.visibility = View.VISIBLE
                summaryResolved.visibility   = View.VISIBLE
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CHART DATA — based on selected range mode
    // ─────────────────────────────────────────────────────────────
    private fun loadChartData() {
        val typeId = if (selectedDeptId > 0) selectedDeptId else agencyId

        progressChart.visibility   = View.VISIBLE
        lineChart.visibility       = View.GONE
        chartLegend.visibility     = View.GONE
        countSummaryRow.visibility = View.GONE
        tvError.visibility         = View.GONE

        Log.d(TAG, "loadChartData typeId=$typeId date=$selectedDate range=$rangeMode")

        lifecycleScope.launch {
            try {
                val dates = buildDateRange(selectedDate, rangeMode)
                val points = mutableListOf<LineChartView.DataPoint>()

                for (dateStr in dates) {
                    val cResp = RetrofitClient.api.getDailyComplainCount(
                        complainTypeId = typeId,
                        selectedDate = dateStr
                    )
                    val rResp = RetrofitClient.api.getDailyResolvedCount(
                        complainTypeId = typeId,
                        selectedDate = dateStr
                    )

                    val complaints = if (cResp.isSuccessful) cResp.body()?.count ?: 0 else 0
                    val resolved = if (rResp.isSuccessful) rResp.body()?.count ?: 0 else 0

                    points += LineChartView.DataPoint(
                        label = formatPointLabel(dateStr),
                        complaints = complaints,
                        resolved = resolved
                    )
                }

                progressChart.visibility   = View.GONE
                lineChart.visibility       = View.VISIBLE
                chartLegend.visibility     = View.VISIBLE
                countSummaryRow.visibility = View.VISIBLE

                lineChart.setViewMode(viewMode)
                lineChart.setData(points)

                val totalComplaints = points.sumOf { it.complaints }
                val totalResolved = points.sumOf { it.resolved }
                tvTotalComplaints.text = totalComplaints.toString()
                tvTotalResolved.text = totalResolved.toString()

                updateLegendAndSummaryVisibility()

            } catch (e: Exception) {
                Log.e(TAG, "Chart load exception: ${e.message}", e)
                showChartError("Failed to load: ${e.message}")
            }
        }
    }

    private fun buildDateRange(anchorDate: String, mode: RangeMode): List<String> {
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