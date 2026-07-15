package com.ScriptIndia.GIDC_CMS_APP.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ScriptIndia.GIDC_CMS_APP.LoginActivity
import com.ScriptIndia.GIDC_CMS_APP.R
import com.google.android.material.button.MaterialButton

class LogoutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_logout)

        setupToolbar()
        bindProfileCard()

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<ImageView>(R.id.imgFooterLogo).setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://scriptindia.in/"))
            startActivity(browserIntent)
        }
    }

    private fun formatRole(role: String?): String {
        val r = role?.lowercase()?.trim()

        return when {
            r.isNullOrEmpty() -> "Department Engineer"
            r.contains("admin") -> "Admin"
            r.contains("agency") -> "Agency"
            r.contains("head") -> "Department Head"
            r.contains("engineer") -> "Department Engineer"
            else -> role!!
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbarLogout)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun bindProfileCard() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val userName = prefs.getString("userName", null)?.trim().orEmpty()
        val department = prefs.getString("departmentName", null)?.trim().orEmpty()
        val role = prefs.getString("role", null)?.trim() ?: intent.getStringExtra("role")

        val displayName = userName.ifEmpty { formatRole(role) }
        findViewById<TextView>(R.id.tvUserName).text = displayName
        findViewById<TextView>(R.id.tvUserType).text = formatRole(role)
       

        val initialsSource = userName.ifEmpty { displayName }
        val initials = initialsSource
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }

        findViewById<TextView>(R.id.tvAvatarInitials).text =
            if (initials.isEmpty()) "U" else initials
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}