package com.example.mygidc.dashboard

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mygidc.R
import com.example.mygidc.model.ComplaintModel
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

class ComplaintAdapter(
    private var list: MutableList<ComplaintModel>,
    private val role: String = "",
    private val status: String = "",
    private val source: String = ComplaintDetailActivity.SOURCE_STATUS,
    private val onItemClick: (ComplaintModel) -> Unit
) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    private var fullList: MutableList<ComplaintModel> = ArrayList(list)

    private var currentSort: SortOrder = SortOrder.DESC   // ✅ DEFAULT LATEST FIRST
    private var activeIdQuery: String = ""
    private var activeDateFilter: String? = null

    enum class SortOrder { NONE, ASC, DESC }

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvComplaintId: TextView = itemView.findViewById(R.id.tvComplaintId)
        val tvBookingTime: TextView = itemView.findViewById(R.id.tvBookingTime)
        val tvSubtype: TextView     = itemView.findViewById(R.id.tvSubtype)
        val ivView: ImageView       = itemView.findViewById(R.id.ivView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_complaint, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.tvComplaintId.text = "${position + 1}. Complaint ID : ${item.complainFormID}"
        holder.tvBookingTime.text = "Booking Time : ${item.callStartTime ?: "N/A"}"
        holder.tvSubtype.text     = "Subtype : ${item.complainSubType ?: "N/A"}"

        val clickListener = View.OnClickListener { onItemClick(item) }

        holder.itemView.setOnClickListener(clickListener)
        holder.ivView.setOnClickListener(clickListener)
    }

    fun updateData(newList: List<ComplaintModel>) {
        fullList = ArrayList(newList)
        applyFilters()
    }

    // 🔹 FILTER BY ID
    fun filterById(query: String) {
        activeIdQuery = query
        applyFilters()
    }

    // 🔹 FILTER BY DATE
    fun filterByDate(date: String?) {
        activeDateFilter = date
        applyFilters()
    }

    // 🔹 MAIN FILTER + SORT ENGINE
    private fun applyFilters() {
        var result = fullList.toMutableList()

        // ID filter
        if (activeIdQuery.isNotEmpty()) {
            result = result.filter {
                it.complainFormID.toString().contains(activeIdQuery, true)
            }.toMutableList()
        }

        // Date filter
        activeDateFilter?.let { date ->
            result = result.filter {
                it.callStartTime?.startsWith(date) == true
            }.toMutableList()
        }

        // SORTING (SAFE DATE PARSE)
        list = when (currentSort) {
            SortOrder.ASC -> result.sortedBy { parseDateSafe(it.callStartTime) }.toMutableList()
            SortOrder.DESC -> result.sortedByDescending { parseDateSafe(it.callStartTime) }.toMutableList()
            SortOrder.NONE -> result
        }

        notifyDataSetChanged()
    }

    // 🔹 SORT BUTTONS
    fun sortDateAsc() {
        currentSort = SortOrder.ASC
        applyFilters()
    }

    fun sortDateDesc() {
        currentSort = SortOrder.DESC
        applyFilters()
    }

    // 🔹 SAFE DATE PARSER (NO CRASH)
    private fun parseDateSafe(dateStr: String?): Date {
        return try {
            sdf.parse(dateStr ?: "") ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }
}