package com.example.mygidc.settings

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.mygidc.LoginActivity
import com.example.mygidc.R
import com.google.android.material.button.MaterialButton

class LogoutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_logout)

        val rawRole = intent.getStringExtra("role")
        val role = formatRole(rawRole)

        setupToolbar()
        bindProfileCard(role)

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            showLogoutConfirmation()
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

    private fun bindProfileCard(role: String) {
        val initials = role
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }

        findViewById<TextView>(R.id.tvAvatarInitials).text =
            if (initials.isEmpty()) "DE" else initials

        findViewById<TextView>(R.id.tvUserName).text = role
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