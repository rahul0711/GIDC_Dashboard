package com.example.mygidc.dashboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.mygidc.R
import com.example.mygidc.api.RetrofitClient
import com.example.mygidc.model.ApprovedResolvedComplainRequest
import com.example.mygidc.model.ComplaintModel
import com.example.mygidc.model.UpdateComplainRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ComplaintDetailActivity : AppCompatActivity() {

    private val TAG = "ComplaintDetail"

    // ── Single shared audio player manager ────────────────────────────────────
    private var audioPlayer: AudioPlayerManager? = null

    companion object {
        const val SOURCE_ALERT_RESOLVE = "alert_resolve"
        const val SOURCE_STATUS        = "status_counter"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AudioPlayerManager — owns one MediaPlayer, wired to UI controls
    // ─────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────
// AudioPlayerManager — FULL DEBUG VERSION
// Filter Logcat by tag: AUDIO_DEBUG
// ─────────────────────────────────────────────────────────────────────────
    inner class AudioPlayerManager(
        private val url: String,
        private val btnPlayPause: MaterialButton,
        private val btnStop: MaterialButton,
        private val seekBar: SeekBar,
        private val tvCurrent: TextView,
        private val tvTotal: TextView
    ) {
        private val ATAG = "AUDIO_DEBUG"  // filter by this in Logcat

        private var mp: MediaPlayer? = null
        private var isPlaying = false
        private val handler = Handler(Looper.getMainLooper())
        private var seekRunnable: Runnable? = null

        init {
            Log.d(ATAG, "┌─────────────────────────────────────")
            Log.d(ATAG, "│ AudioPlayerManager INIT")
            Log.d(ATAG, "│ URL = '$url'")
            Log.d(ATAG, "│ URL length = ${url.length}")
            Log.d(ATAG, "│ URL starts with https = ${url.startsWith("https://")}")
            Log.d(ATAG, "│ URL starts with http  = ${url.startsWith("http://")}")
            Log.d(ATAG, "└─────────────────────────────────────")

            tvCurrent.text   = "0:00"
            tvTotal.text     = "0:00"
            seekBar.progress = 0

            btnPlayPause.setOnClickListener { onPlayPauseClicked() }
            btnStop.setOnClickListener     { onStopClicked() }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        mp?.seekTo(progress)
                        tvCurrent.text = formatTime(progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = stopSeekUpdate()
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    if (isPlaying) startSeekUpdate()
                }
            })
        }

        private fun onPlayPauseClicked() {
            Log.d(ATAG, "▶ onPlayPauseClicked — mp=${if (mp == null) "NULL" else "EXISTS"} isPlaying=$isPlaying")

            when {
                // ── FIRST PRESS: init MediaPlayer ─────────────────────────────
                mp == null -> {
                    Log.d(ATAG, "── STEP 1: mp is null, starting fresh init")
                    Log.d(ATAG, "── STEP 1a: URL to play = '$url'")

                    btnPlayPause.isEnabled = false
                    btnPlayPause.text      = "⏳  Loading…"

                    try {
                        Log.d(ATAG, "── STEP 2: Creating MediaPlayer instance")
                        val player = MediaPlayer()

                        Log.d(ATAG, "── STEP 3: Setting AudioAttributes")
                        player.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        Log.d(ATAG, "── STEP 3: AudioAttributes SET OK")

                        // Build headers
                        val headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Android)",
                            "Accept"     to "*/*",
                            "Connection" to "keep-alive"
                        )
                        Log.d(ATAG, "── STEP 4: Headers built = $headers")

                        val uri = Uri.parse(url.trim())
                        Log.d(ATAG, "── STEP 4a: Parsed URI = $uri")
                        Log.d(ATAG, "── STEP 4b: URI scheme  = ${uri.scheme}")
                        Log.d(ATAG, "── STEP 4c: URI host    = ${uri.host}")
                        Log.d(ATAG, "── STEP 4d: URI path    = ${uri.path}")

                        Log.d(ATAG, "── STEP 5: Calling setDataSource(context, uri, headers)")
                        player.setDataSource(this@ComplaintDetailActivity, uri, headers)
                        Log.d(ATAG, "── STEP 5: setDataSource COMPLETED without exception")

                        // Wire listeners BEFORE prepareAsync
                        player.setOnPreparedListener { readyPlayer ->
                            Log.d(ATAG, "✅ STEP 6: onPrepared fired!")
                            Log.d(ATAG, "✅ STEP 6a: duration = ${readyPlayer.duration}ms")
                            Log.d(ATAG, "✅ STEP 6b: calling player.start()")
                            btnPlayPause.isEnabled           = true
                            seekBar.max                      = readyPlayer.duration
                            tvTotal.text                     = formatTime(readyPlayer.duration)
                            readyPlayer.start()
                            this@AudioPlayerManager.isPlaying = true
                            btnPlayPause.text                 = "⏸  Pause"
                            startSeekUpdate()
                            Log.d(ATAG, "✅ STEP 6c: player.start() called, isPlaying=$isPlaying")
                        }

                        player.setOnCompletionListener {
                            Log.d(ATAG, "✅ STEP 7: onCompletion fired — playback finished")
                            this@AudioPlayerManager.isPlaying = false
                            btnPlayPause.text                 = "▶  Play"
                            btnPlayPause.isEnabled            = true
                            seekBar.progress                  = 0
                            tvCurrent.text                    = "0:00"
                            stopSeekUpdate()
                        }

                        player.setOnErrorListener { _, what, extra ->
                            val whatStr = when (what) {
                                MediaPlayer.MEDIA_ERROR_UNKNOWN     -> "MEDIA_ERROR_UNKNOWN(1)"
                                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "MEDIA_ERROR_SERVER_DIED(100)"
                                else                               -> "UNKNOWN_WHAT($what)"
                            }
                            val extraStr = when (extra) {
                                MediaPlayer.MEDIA_ERROR_IO          -> "MEDIA_ERROR_IO(-1004)"
                                MediaPlayer.MEDIA_ERROR_MALFORMED   -> "MEDIA_ERROR_MALFORMED(-1007)"
                                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "MEDIA_ERROR_UNSUPPORTED(-1010)"
                                MediaPlayer.MEDIA_ERROR_TIMED_OUT   -> "MEDIA_ERROR_TIMED_OUT(-110)"
                                -2147483648                         -> "LOW_LEVEL_SYSTEM_ERROR"
                                else                               -> "UNKNOWN_EXTRA($extra)"
                            }
                            Log.e(ATAG, "❌ onError: what=$whatStr extra=$extraStr url=$url")

                            // ── User-friendly message based on error type ──────────────────
                            val userMessage = when (extra) {
                                MediaPlayer.MEDIA_ERROR_IO       -> "Recording not found on server"
                                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Connection timed out"
                                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Audio format not supported"
                                -2147483648 -> "Recording unavailable (file may not exist on server)"
                                else        -> "Playback failed ($whatStr)"
                            }

                            Toast.makeText(
                                this@ComplaintDetailActivity,
                                userMessage,
                                Toast.LENGTH_LONG
                            ).show()

                            releasePlayer()
                            btnPlayPause.isEnabled = true
                            btnPlayPause.text      = "▶  Play"
                            true
                        }

                        // Attach info listener for extra diagnostic info
                        player.setOnInfoListener { _, what, extra ->
                            Log.d(ATAG, "ℹ️ onInfo: what=$what extra=$extra")
                            false
                        }

                        mp = player

                        Log.d(ATAG, "── STEP 8: Calling prepareAsync()")
                        player.prepareAsync()
                        Log.d(ATAG, "── STEP 8: prepareAsync() called — waiting for onPrepared...")

                    } catch (e: Exception) {
                        Log.e(ATAG, "❌ EXCEPTION during MediaPlayer init!")
                        Log.e(ATAG, "❌ Exception type    = ${e::class.java.simpleName}")
                        Log.e(ATAG, "❌ Exception message = ${e.message}")
                        Log.e(ATAG, "❌ Stack trace:", e)
                        Toast.makeText(
                            this@ComplaintDetailActivity,
                            "Init error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        releasePlayer()
                        btnPlayPause.isEnabled = true
                        btnPlayPause.text      = "▶  Play"
                    }
                }

                // ── PAUSE ──────────────────────────────────────────────────────
                isPlaying -> {
                    Log.d(ATAG, "⏸ Pausing playback")
                    mp?.pause()
                    this@AudioPlayerManager.isPlaying = false
                    btnPlayPause.text                 = "▶  Play"
                    stopSeekUpdate()
                }

                // ── RESUME ─────────────────────────────────────────────────────
                else -> {
                    Log.d(ATAG, "▶ Resuming playback")
                    mp?.start()
                    this@AudioPlayerManager.isPlaying = true
                    btnPlayPause.text                 = "⏸  Pause"
                    startSeekUpdate()
                }
            }
        }

        private fun onStopClicked() {
            Log.d(ATAG, "⏹ Stop clicked — releasing player")
            releasePlayer()
            btnPlayPause.isEnabled = true
            btnPlayPause.text      = "▶  Play"
            seekBar.progress       = 0
            tvCurrent.text         = "0:00"
        }

        private fun startSeekUpdate() {
            seekRunnable = object : Runnable {
                override fun run() {
                    try {
                        val player = mp ?: return
                        if (player.isPlaying) {
                            val pos = player.currentPosition
                            seekBar.progress = pos
                            tvCurrent.text   = formatTime(pos)
                            Log.d(ATAG, "── STEP 8: Calling prepareAsync()")
                            player.prepareAsync()
                            Log.d(ATAG, "── STEP 8: prepareAsync() called — waiting for onPrepared...")

// ✅ Auto-cancel if server doesn't respond in 15 seconds
                            handler.postDelayed({
                                if (mp != null && !isPlaying) {
                                    Log.e(ATAG, "❌ TIMEOUT: onPrepared never fired after 15s — aborting")
                                    Toast.makeText(
                                        this@ComplaintDetailActivity,
                                        "Recording unavailable (server timeout)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    releasePlayer()
                                    btnPlayPause.isEnabled = true
                                    btnPlayPause.text      = "▶  Play"
                                }
                            }, 15_000)
                        }
                    } catch (e: Exception) {
                        Log.e(ATAG, "SeekUpdate error: ${e.message}")
                    }
                }
            }
            handler.post(seekRunnable!!)
        }

        private fun stopSeekUpdate() {
            seekRunnable?.let { handler.removeCallbacks(it) }
            seekRunnable = null
        }

        private fun releasePlayer() {
            Log.d(ATAG, "🔴 releasePlayer() called")
            stopSeekUpdate()
            try { mp?.stop()    } catch (e: Exception) { Log.e(ATAG, "stop() error: ${e.message}") }
            try { mp?.reset()   } catch (e: Exception) { Log.e(ATAG, "reset() error: ${e.message}") }
            try { mp?.release() } catch (e: Exception) { Log.e(ATAG, "release() error: ${e.message}") }
            mp        = null
            isPlaying = false
            Log.d(ATAG, "🔴 releasePlayer() done")
        }

        fun destroy() {
            Log.d(ATAG, "💀 destroy() called")
            releasePlayer()
        }

        private fun formatTime(ms: Int): String {
            val s = ms / 1000
            return "%d:%02d".format(s / 60, s % 60)
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complaint_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        val json = intent.getStringExtra("complaint")
        val item = if (json != null) Gson().fromJson(json, ComplaintModel::class.java) else null

        if (item == null) {
            Toast.makeText(this, "No data found", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "ComplaintModel is null")
            finish()
            return
        }

        val rawRole          = intent.getStringExtra("role")   ?: ""
        val status           = intent.getStringExtra("status") ?: item.status ?: ""
        val source           = intent.getStringExtra("source") ?: SOURCE_STATUS
        val roleLower        = rawRole.trim().lowercase()
        val isAlertOrResolve = source.equals(SOURCE_ALERT_RESOLVE, ignoreCase = true)

        Log.d(TAG, "role='$roleLower' status='$status' source='$source' alertOrResolve=$isAlertOrResolve")

        when {
            roleLower == "agency" || roleLower.contains("agency") ->
                bindAgencyView(item, isAlertOrResolve)
            roleLower.contains("engineer") || roleLower.contains("head") ->
                bindEngineerView(item, status, isAlertOrResolve)
            else ->
                bindAdminView(item, isAlertOrResolve)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN VIEW
    // ─────────────────────────────────────────────────────────────────────────
    private fun bindAdminView(item: ComplaintModel, isAlertOrResolve: Boolean) {
        showSection(R.id.sectionAdmin)
        hideSection(R.id.sectionEngineer)
        hideSection(R.id.sectionAgency)

        setText(R.id.tvAdminComplaintNo, item.complainFormID.toString())
        setText(R.id.tvAdminMobile,      item.callMobileNumber     ?: "-")
        setText(R.id.tvAdminTime,        item.callStartTime        ?: "-")
        setText(R.id.tvAdminType,        item.complainType         ?: "-")
        setText(R.id.tvAdminSubType,     item.complainSubType      ?: "-")
        setText(R.id.tvAdminArea,        item.complainArea         ?: "-")
        setText(R.id.tvAdminLandmark,    item.complainLandmark     ?: "-")
        setText(R.id.tvAdminNotes,       item.complainSpecialNotes ?: "-")
        setText(R.id.tvAdminPriority,    item.complainPriority     ?: "-")
        setText(R.id.tvAdminStatus,      item.status               ?: "-")

        setText(R.id.tvAdminAgencyId, item.agencyId ?: "-")
        setText(R.id.tvAdminAgency,   item.agency   ?: "Not Assigned")
        findViewById<View>(R.id.rowAdminAgencyId)?.visibility = View.VISIBLE
        findViewById<View>(R.id.rowAdminAgency)?.visibility   = View.VISIBLE

        findViewById<TextView>(R.id.tvAdminMobile)?.setOnClickListener {
            dialNumber(item.callMobileNumber)
        }

        findViewById<MaterialButton>(R.id.btnAdminCallAgency)?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                showCallChooser("Complainant", item.callMobileNumber, "Agency", item.agencyPhoneno)
            }
        }

        val extra = if (isAlertOrResolve) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowAdminBusinessHours)?.visibility = extra
        findViewById<View>(R.id.rowAdminDuration)?.visibility      = extra
        findViewById<View>(R.id.rowAdminResolveTime)?.visibility   = extra
        findViewById<View>(R.id.rowAdminAlertTime)?.visibility     = extra

        if (isAlertOrResolve) {
            setText(R.id.tvAdminBusinessHours, item.businessHours   ?: "-")
            setText(R.id.tvAdminDuration,      "${item.callDuration ?: "0"} sec")
            setText(R.id.tvAdminResolveTime,   "${item.resolveTime  ?: "-"} hrs")
            setText(R.id.tvAdminAlertTime,     "${item.alertTime    ?: "-"} hrs")
        }

        setupRecordingPlayer(
            recordingUrl    = item.callRecordingLink,
            sectionId       = R.id.sectionAdminRecording,
            btnPlayPauseId  = R.id.btnAdminPlayPause,
            btnStopId       = R.id.btnAdminStop,
            seekBarId       = R.id.seekBarAdmin,
            tvCurrentTimeId = R.id.tvAdminCurrentTime,
            tvTotalTimeId   = R.id.tvAdminTotalTime
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENGINEER / HEAD VIEW
    // ─────────────────────────────────────────────────────────────────────────
    private fun bindEngineerView(item: ComplaintModel, status: String, isAlertOrResolve: Boolean) {
        hideSection(R.id.sectionAdmin)
        showSection(R.id.sectionEngineer)
        hideSection(R.id.sectionAgency)

        setText(R.id.tvEngComplaintNo, item.complainFormID.toString())
        setText(R.id.tvEngMobile,      item.callMobileNumber     ?: "-")
        setText(R.id.tvEngTime,        item.callStartTime        ?: "-")
        setText(R.id.tvEngType,        item.complainType         ?: "-")
        setText(R.id.tvEngSubType,     item.complainSubType      ?: "-")
        setText(R.id.tvEngArea,        item.complainArea         ?: "-")
        setText(R.id.tvEngLandmark,    item.complainLandmark     ?: "-")
        setText(R.id.tvEngNotes,       item.complainSpecialNotes ?: "-")
        setText(R.id.tvEngPriority,    item.complainPriority     ?: "-")
        setText(R.id.tvEngStatus,      item.status               ?: "-")
        setText(R.id.tvEngAgencyId,    item.agencyId             ?: "-")
        setText(R.id.tvEngAgency,      item.agency               ?: "Not Assigned")

        findViewById<View>(R.id.rowEngAgencyId)?.visibility = View.VISIBLE
        findViewById<View>(R.id.rowEngAgency)?.visibility   = View.VISIBLE

        findViewById<TextView>(R.id.tvEngMobile)?.setOnClickListener {
            dialNumber(item.callMobileNumber)
        }

        val effectiveStatus = status.trim().lowercase()
            .ifEmpty { item.status?.trim()?.lowercase() ?: "" }

        val btnApprove = findViewById<MaterialButton>(R.id.btnEngApprove)
        if (effectiveStatus == "resolved") {
            btnApprove?.visibility = View.VISIBLE
            btnApprove?.setOnClickListener { approveComplaint(btnApprove, item) }
        } else {
            btnApprove?.visibility = View.GONE
        }

        findViewById<MaterialButton>(R.id.btnEngCall)?.setOnClickListener {
            showCallChooser("Complainant", item.callMobileNumber, "Agency", item.agencyPhoneno)
        }

        val extra = if (isAlertOrResolve) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowEngBusinessHours)?.visibility = extra
        findViewById<View>(R.id.rowEngDuration)?.visibility      = extra
        findViewById<View>(R.id.rowEngResolveTime)?.visibility   = extra
        findViewById<View>(R.id.rowEngAlertTime)?.visibility     = extra

        if (isAlertOrResolve) {
            setText(R.id.tvEngBusinessHours, item.businessHours   ?: "-")
            setText(R.id.tvEngDuration,      "${item.callDuration ?: "0"} sec")
            setText(R.id.tvEngResolveTime,   "${item.resolveTime  ?: "-"} hrs")
            setText(R.id.tvEngAlertTime,     "${item.alertTime    ?: "-"} hrs")
        }

        setupRecordingPlayer(
            recordingUrl    = item.callRecordingLink,
            sectionId       = R.id.sectionEngRecording,
            btnPlayPauseId  = R.id.btnEngPlayPause,
            btnStopId       = R.id.btnEngStop,
            seekBarId       = R.id.seekBarEng,
            tvCurrentTimeId = R.id.tvEngCurrentTime,
            tvTotalTimeId   = R.id.tvEngTotalTime
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGENCY VIEW
    // ─────────────────────────────────────────────────────────────────────────
    private fun bindAgencyView(item: ComplaintModel, isAlertOrResolve: Boolean) {
        hideSection(R.id.sectionAdmin)
        hideSection(R.id.sectionEngineer)
        showSection(R.id.sectionAgency)

        setText(R.id.tvAgcComplaintNo, item.complainFormID.toString())
        setText(R.id.tvAgcTime,        item.callStartTime        ?: "-")
        setText(R.id.tvAgcType,        item.complainType         ?: "-")
        setText(R.id.tvAgcSubType,     item.complainSubType      ?: "-")
        setText(R.id.tvAgcMobile,      item.callMobileNumber     ?: "-")
        setText(R.id.tvAgcArea,        item.complainArea         ?: "-")
        setText(R.id.tvAgcLandmark,    item.complainLandmark     ?: "-")
        setText(R.id.tvAgcPriority,    item.complainPriority     ?: "-")
        setText(R.id.tvAgcStatus,      item.status               ?: "-")

        findViewById<TextView>(R.id.tvAgcMobile)?.setOnClickListener {
            dialNumber(item.callMobileNumber)
        }

        val statusOptions = listOf("Hold", "In Process", "Resolved", "Cancel", "New")
        val spinner = findViewById<Spinner>(R.id.spinnerAgcStatus)

        val spinnerAdapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, statusOptions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(Color.parseColor("#1A1A1A")); textSize = 14f; setPadding(0, 0, 0, 0)
                }
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextColor(Color.parseColor("#1A1A1A")); textSize = 14f
                    setPadding(40, 28, 40, 28); setBackgroundColor(Color.WHITE)
                }
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner?.adapter = spinnerAdapter

        val matchIndex = statusOptions.indexOfFirst { it.equals(item.status ?: "", ignoreCase = true) }
        if (matchIndex >= 0) spinner?.setSelection(matchIndex)

        val etNotes = findViewById<TextInputEditText>(R.id.etAgcNotes)
        etNotes?.setText(item.complainSpecialNotes ?: "")

        val btnUpdate = findViewById<MaterialButton>(R.id.btnAgcUpdate)
        btnUpdate?.setOnClickListener {
            val selectedStatus = spinner?.selectedItem?.toString() ?: "Hold"
            val notes          = etNotes?.text?.toString()?.trim() ?: ""
            val agencyId       = item.agencyId?.takeIf { it.isNotEmpty() } ?: "23"
            if (notes.isEmpty()) {
                Toast.makeText(this, "Please enter notes before updating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateComplaint(item.complainFormID, agencyId, selectedStatus, notes)
        }

        findViewById<MaterialButton>(R.id.btnAgcCall)?.setOnClickListener {
            showCallChooser("Complainant", item.callMobileNumber, "Agency", item.agencyPhoneno)
        }

        val extra = if (isAlertOrResolve) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowAgcBusinessHours)?.visibility = extra
        findViewById<View>(R.id.rowAgcDuration)?.visibility      = extra
        findViewById<View>(R.id.rowAgcResolveTime)?.visibility   = extra
        findViewById<View>(R.id.rowAgcAlertTime)?.visibility     = extra

        if (isAlertOrResolve) {
            setText(R.id.tvAgcBusinessHours, item.businessHours   ?: "-")
            setText(R.id.tvAgcDuration,      "${item.callDuration ?: "0"} sec")
            setText(R.id.tvAgcResolveTime,   "${item.resolveTime  ?: "-"} hrs")
            setText(R.id.tvAgcAlertTime,     "${item.alertTime    ?: "-"} hrs")
        }

        setupRecordingPlayer(
            recordingUrl    = item.callRecordingLink,
            sectionId       = R.id.sectionAgcRecording,
            btnPlayPauseId  = R.id.btnAgcPlayPause,
            btnStopId       = R.id.btnAgcStop,
            seekBarId       = R.id.seekBarAgc,
            tvCurrentTimeId = R.id.tvAgcCurrentTime,
            tvTotalTimeId   = R.id.tvAgcTotalTime
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RECORDING PLAYER SETUP
    // ─────────────────────────────────────────────────────────────────────────
    private fun setupRecordingPlayer(
        recordingUrl: String?,
        sectionId: Int,
        btnPlayPauseId: Int,
        btnStopId: Int,
        seekBarId: Int,
        tvCurrentTimeId: Int,
        tvTotalTimeId: Int
    ) {
        val section = findViewById<View>(sectionId)

        val cleanUrl = recordingUrl?.trim() ?: ""

        // ✅ FIXED: Also check for "not available" and that URL actually starts with http
        val isInvalidUrl = cleanUrl.isEmpty()
                || !cleanUrl.lowercase().startsWith("http")  // ← THIS catches everything in one go

        if (isInvalidUrl) {
            section?.visibility = View.GONE
            Log.d("AUDIO_DEBUG", "Invalid URL — hiding player. Raw = '$recordingUrl'")
            return
        }

        section?.visibility = View.VISIBLE
        Log.d("AUDIO_DEBUG", "Valid URL — showing player: $cleanUrl")

        val btnPlayPause = findViewById<MaterialButton>(btnPlayPauseId) ?: return
        val btnStop      = findViewById<MaterialButton>(btnStopId)      ?: return
        val seekBar      = findViewById<SeekBar>(seekBarId)             ?: return
        val tvCurrent    = findViewById<TextView>(tvCurrentTimeId)      ?: return
        val tvTotal      = findViewById<TextView>(tvTotalTimeId)        ?: return

        audioPlayer?.destroy()
        audioPlayer = AudioPlayerManager(
            url          = cleanUrl,
            btnPlayPause = btnPlayPause,
            btnStop      = btnStop,
            seekBar      = seekBar,
            tvCurrent    = tvCurrent,
            tvTotal      = tvTotal
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CALL CHOOSER
    // ─────────────────────────────────────────────────────────────────────────
    private fun showCallChooser(label1: String, number1: String?, label2: String, number2: String?) {
        val allNumbers = mutableListOf<Pair<String, String>>()

        if (isValidNumber(number1)) allNumbers.add(Pair(label1, number1!!.trim()))

        number2?.split(",")?.forEachIndexed { index, raw ->
            val cleaned = raw.trim()
            if (isValidNumber(cleaned))
                allNumbers.add(Pair(if (index == 0) label2 else "$label2 ${index + 1}", cleaned))
        }

        when {
            allNumbers.isEmpty() ->
                Toast.makeText(this, "No contact number available", Toast.LENGTH_SHORT).show()
            allNumbers.size == 1 ->
                dialNumber(allNumbers[0].second)
            else -> {
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8)
                }
                container.addView(TextView(this).apply {
                    text = "Select number to call"; textSize = 17f
                    setTextColor(android.graphics.Color.BLACK)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(60, 40, 60, 24)
                })
                val dialog = AlertDialog.Builder(this)
                    .setView(container)
                    .setNegativeButton("Cancel", null)
                    .create()

                allNumbers.forEach { (lbl, num) ->
                    container.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#22000000"))
                    })
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(60, 36, 60, 36)
                        isClickable = true; isFocusable = true
                        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                        background = ta.getDrawable(0); ta.recycle()
                        setOnClickListener { dialog.dismiss(); dialNumber(num) }
                    }
                    row.addView(TextView(this).apply {
                        text = lbl.uppercase(); textSize = 11f; letterSpacing = 0.08f
                        setTextColor(android.graphics.Color.parseColor("#888888"))
                    })
                    row.addView(TextView(this).apply {
                        text = num; textSize = 16f
                        setTextColor(android.graphics.Color.parseColor("#1976D2"))
                        typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 4, 0, 0)
                    })
                    container.addView(row)
                }
                dialog.show()
            }
        }
    }

    private fun dialNumber(number: String?) {
        if (!isValidNumber(number)) {
            Toast.makeText(this, "No contact number available", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number!!.trim()}")))
    }

    private fun isValidNumber(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val clean = number.trim()
        return clean.length >= 7
                && clean != "-"
                && clean.replace(Regex("[^0-9]"), "").length >= 7
                && clean.lowercase() != "null"
                && clean.lowercase() != "n/a"
                && clean.lowercase() != "none"
                && clean.lowercase() != "na"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APPROVE COMPLAINT
    // ─────────────────────────────────────────────────────────────────────────
    private fun approveComplaint(btn: MaterialButton, item: ComplaintModel) {
        btn.isEnabled = false; btn.text = "Approving…"

        val request = ApprovedResolvedComplainRequest(
            complainFormID       = item.complainFormID,
            agencyId             = item.agencyId?.takeIf { it.isNotEmpty() } ?: "23",
            status               = "Approved",
            callDuration         = item.callDuration         ?: "0",
            complainType         = item.complainType         ?: "",
            callStartTime        = item.callStartTime        ?: "",
            callMobileNumber     = item.callMobileNumber     ?: "",
            callRecordingLink    = item.callRecordingLink    ?: "",
            complainSpecialNotes = item.complainSpecialNotes ?: ""
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.approvedResolvedComplain(request)
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@ComplaintDetailActivity,
                        "Complaint #${item.complainFormID} approved!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Log.e(TAG, "Approve failed: ${response.code()} — ${response.errorBody()?.string()}")
                    Toast.makeText(
                        this@ComplaintDetailActivity,
                        "Approval failed: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    btn.isEnabled = true; btn.text = "✔  Approve"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Approve exception: ${e.message}", e)
                Toast.makeText(
                    this@ComplaintDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                btn.isEnabled = true; btn.text = "✔  Approve"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE COMPLAINT
    // ─────────────────────────────────────────────────────────────────────────
    private fun updateComplaint(complainFormID: Int, agencyId: String, status: String, notes: String) {
        val btnUpdate = findViewById<MaterialButton>(R.id.btnAgcUpdate)
        btnUpdate?.isEnabled = false; btnUpdate?.text = "Updating…"

        val request = UpdateComplainRequest(
            complainFormID       = complainFormID,
            agencyId             = agencyId,
            status               = status,
            complainSpecialNotes = notes
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.updateComplain(request)
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@ComplaintDetailActivity,
                        "Complaint updated successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Log.e(TAG, "Update failed: ${response.code()} — ${response.errorBody()?.string()}")
                    Toast.makeText(
                        this@ComplaintDetailActivity,
                        "Update failed: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update exception: ${e.message}", e)
                Toast.makeText(
                    this@ComplaintDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnUpdate?.isEnabled = true; btnUpdate?.text = "Update Complaint"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private fun setText(viewId: Int, value: String) {
        val view = findViewById<TextView>(viewId)
        if (view == null) {
            try { Log.e(TAG, "TextView NOT FOUND: '${resources.getResourceEntryName(viewId)}'") }
            catch (e: Exception) { Log.e(TAG, "TextView NOT FOUND id=$viewId") }
            return
        }
        view.text = value
    }

    private fun showSection(id: Int) {
        val v = findViewById<View>(id) ?: run {
            Log.e(TAG, "Section NOT FOUND: ${resources.getResourceEntryName(id)}"); return
        }
        v.visibility = View.VISIBLE
    }

    private fun hideSection(id: Int) { findViewById<View>(id)?.visibility = View.GONE }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        // Pause audio when the user leaves the screen (optional UX improvement)
        // audioPlayer?.destroy()  // uncomment if you want stop-on-leave
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer?.destroy()
        audioPlayer = null
    }
}