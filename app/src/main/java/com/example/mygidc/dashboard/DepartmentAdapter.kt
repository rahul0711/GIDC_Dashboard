package com.example.mygidc.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mygidc.R
import com.example.mygidc.model.Department

class DepartmentAdapter(
    private val list: List<Department>,
    private val onClick: (Department) -> Unit
) : RecyclerView.Adapter<DepartmentAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivDeptIcon)
        val name: TextView = itemView.findViewById(R.id.tvDeptName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_department_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.name.text = item.departmentName
        holder.icon.setImageResource(getDepartmentIcon(item.departmentName))

        // 👉 CLICK EVENT
        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount(): Int = list.size

    // 🔥 ICON MAPPING FUNCTION
    private fun getDepartmentIcon(name: String): Int {
        val dept = name.trim().lowercase()

        return when {
            dept.contains("street") -> R.drawable.ic_street_light
            dept.contains("water") -> R.drawable.ic_water
            dept.contains("drain") -> R.drawable.ic_drainage
            dept.contains("road") -> R.drawable.ic_road
            dept.contains("clean") -> R.drawable.ic_cleaning
            else -> R.drawable.ic_chevron_right
        }
    }
}