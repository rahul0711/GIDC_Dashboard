package com.ScriptIndia.GIDC_CMS_APP

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.ScriptIndia.GIDC_CMS_APP.dashboard.AdminDashboardActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set splash layout
        setContentView(R.layout.activity_splash)

        // Delay for 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({

            val sharedPref = getSharedPreferences("app", MODE_PRIVATE)
            val token = sharedPref.getString("token", null)

            if (token != null) {
                startActivity(Intent(this, AdminDashboardActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            finish()

        }, 2000)
    }
}