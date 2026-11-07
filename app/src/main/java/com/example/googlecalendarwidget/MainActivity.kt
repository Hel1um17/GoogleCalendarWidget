package com.example.googlecalendarwidget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 小部件本身不能申请危险权限，所以首次打开 App 时由 Activity 动态申请 READ_CALENDAR。
 * 授权成功后马上通知所有小部件刷新。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_READ_CALENDAR = 100
        private const val PREFS_NAME = "calendar_widget_prefs"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
        private const val KEY_PERMISSION_DENIED = "permission_denied"
    }

    private lateinit var statusView: TextView
    private lateinit var primaryButton: Button
    private lateinit var settingsButton: Button

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        statusView = findViewById(R.id.status_text)
        primaryButton = findViewById(R.id.primary_button)
        settingsButton = findViewById(R.id.settings_button)

        primaryButton.setOnClickListener { onPrimaryClicked() }
        settingsButton.setOnClickListener { openAppSettings() }

        updateUi()

        // 只在“第一次打开 App”时自动弹一次系统权限框；用户拒绝后不再反复骚扰，
        // 而是由页面上的按钮 + 应用设置入口继续引导。
        if (savedInstanceState == null &&
            !hasCalendarPermission() &&
            !prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
        ) {
            requestPermissionNow()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()

        if (hasCalendarPermission()) {
            // 从系统设置返回、或权限状态发生变化时，立即刷新桌面小部件。
            CalendarWidgetProvider.refreshAll(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_READ_CALENDAR) return

        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        prefs.edit()
            .putBoolean(KEY_PERMISSION_DENIED, !granted)
            .apply()

        updateUi()

        if (granted) {
            CalendarWidgetProvider.refreshAll(this)
        }
    }

    private fun updateUi() {
        val granted = hasCalendarPermission()

        statusView.text = getString(
            if (granted) R.string.main_status_granted else R.string.main_status_denied
        )
        primaryButton.text = getString(
            if (granted) R.string.main_refresh else R.string.main_grant
        )
        settingsButton.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun onPrimaryClicked() {
        if (hasCalendarPermission()) {
            CalendarWidgetProvider.refreshAll(this)
            statusView.text = getString(R.string.main_refreshed)
            return
        }

        val permission = Manifest.permission.READ_CALENDAR
        val deniedBefore = prefs.getBoolean(KEY_PERMISSION_DENIED, false)

        // Android 11+ 连续拒绝后，系统不会再弹权限框；此时只能引导用户去应用设置。
        if (deniedBefore && !shouldShowRequestPermissionRationale(permission)) {
            openAppSettings()
            return
        }

        if (shouldShowRequestPermissionRationale(permission)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setPositiveButton(R.string.continue_label) { _, _ ->
                    requestPermissionNow()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            requestPermissionNow()
        }
    }

    private fun requestPermissionNow() {
        prefs.edit()
            .putBoolean(KEY_PERMISSION_REQUESTED, true)
            .apply()
        requestPermissions(
            arrayOf(Manifest.permission.READ_CALENDAR),
            REQUEST_READ_CALENDAR
        )
    }

    private fun hasCalendarPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }
}
