package com.nkj97721.framedimmer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val captureRequest = 4102
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 16f
            setPadding(32, 48, 32, 32)
            text = "FrameDimmer\n全局高亮保护（实验版）\n\n检测到大面积高亮画面后，尝试将系统亮度降到最低；高亮消失后还原原亮度。\n\n注意：MediaProjection 异步捕获，无法保证在高亮首帧显示前完成处理。受保护内容可能无法检测。"
        }
        val start = Button(this).apply {
            text = "开启全局监控"
            setOnClickListener {
                if (!Settings.System.canWrite(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                    Toast.makeText(this@MainActivity, "允许修改系统设置后，返回并再次点击开启", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val manager = getSystemService(MediaProjectionManager::class.java)
                @Suppress("DEPRECATION")
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        }
        val stop = Button(this).apply {
            text = "停止监控并恢复亮度"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                Toast.makeText(this@MainActivity, "已请求停止监控并恢复亮度", Toast.LENGTH_SHORT).show()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(status)
            addView(start)
            addView(stop)
        })
    }

    @Deprecated("Deprecated by Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, CaptureService::class.java).apply {
                    putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureService.EXTRA_RESULT_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                Toast.makeText(this, "已启动全局高亮检测", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "未授予屏幕捕获权限", Toast.LENGTH_SHORT).show()
        }
    }
}
