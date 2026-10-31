package com.nkj97721.framedimmer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val captureRequest = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = "FrameDimmer\n全局画面亮度保护（实验版）\n\n注意：Android 不允许第三方应用保证在下一帧显示前完成分析。"
            textSize = 16f
            setPadding(32, 48, 32, 32)
        }
        val start = Button(this).apply {
            text = "开启全局监控"
            setOnClickListener {
                if (!Settings.System.canWrite(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
                    Toast.makeText(this@MainActivity, "请允许修改系统设置，然后重新点击", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val manager = getSystemService(MediaProjectionManager::class.java)
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(status)
            addView(start)
        })
    }

    @Deprecated("Deprecated by Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(service)
            Toast.makeText(this, "监控服务已启动（亮度分析模块待接入）", Toast.LENGTH_SHORT).show()
        }
    }
}
