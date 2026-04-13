package com.example.mygidc

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mygidc.api.RetrofitClient
import com.example.mygidc.dashboard.*
import com.example.mygidc.model.LoginRequest
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import com.google.gson.Gson

class LoginActivity : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val ivToggle = findViewById<ImageView>(R.id.ivPasswordToggle)

        ivToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible)
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

            etPassword.setSelection(etPassword.text.length)
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter username & password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(username, password)
        }
    }

    private fun loginUser(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.api.login(LoginRequest(username, password))

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!

                    if (data.status == "Active") {

                        // ✅ FIXED SESSION
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("isLoggedIn", true)
                            .putString("role", data.type)
                            .putLong("userId", data.userID)
                            .apply()

                        val deptJson = Gson().toJson(data.listDepartmentMaster)
                        val role = data.type.lowercase()

                        withContext(Dispatchers.Main) {

                            val intent = when {
                                role.contains("admin") -> Intent(this@LoginActivity, AdminDashboardActivity::class.java)
                                role.contains("agency") -> Intent(this@LoginActivity, AgencyDashboardActivity::class.java)
                                role.contains("head") -> Intent(this@LoginActivity, HeadDashboardActivity::class.java)
                                else -> Intent(this@LoginActivity, EngineerDashboardActivity::class.java)
                            }

                            intent.putExtra("departments", deptJson)
                            intent.putExtra("agencyId", data.userID.toInt())
                            intent.putExtra("role", data.type)

                            startActivity(intent)
                            finish()
                        }

                    } else {
                        showError("User inactive")
                    }
                } else {
                    showError("Login failed")
                }

            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }
}