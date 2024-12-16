package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class LogcatActivity : BaseActivity() {
    private val binding by lazy {
        ActivityLogcatBinding.inflate(layoutInflater)
    }

    companion object {
        private const val MAX_BUFFERED_LINES = (1 shl 14) - 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.title_logcat)

        lifecycleScope.launch(Dispatchers.IO) { streamingLog() }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            Utils.setClipboard(this, binding.tvLogcat.text.toString())
            toast(R.string.toast_success)
            true
        }

        R.id.clear_all -> {
            flush()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun flush() {
        lifecycleScope.launch(Dispatchers.IO) {
            val command = listOf("logcat", "-c")
            val process = ProcessBuilder(command).start()
            process.waitFor()
            withContext(Dispatchers.Main) {
                binding.tvLogcat.text = ""
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun streamingLog() = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder("logcat", "-v", "time", "-s", "GoLog,${ANG_PACKAGE},AndroidRuntime,System.err")
        builder.environment()["LC_ALL"] = "C"
        var process: Process? = null
        try {
            process = try {
                builder.start()
            } catch (e: IOException) {
                Log.e(packageName, Log.getStackTraceString(e))
                return@withContext
            }
            val stdout = BufferedReader(InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8))

            var timeLastNotify = System.nanoTime()
            val bufferedLogLines = arrayListOf<String>()
            var timeout = 1000000000L / 2 // The timeout is initially small so that the view gets populated immediately.

            while (true) {
                val line = stdout.readLine() ?: break
                bufferedLogLines.add(line)
                val timeNow = System.nanoTime()
                if (bufferedLogLines.size < MAX_BUFFERED_LINES && (timeNow - timeLastNotify) < timeout && stdout.ready())
                    continue
                timeout = 1000000000L * 5 / 2 // Increase the timeout after the initial view has something in it.
                timeLastNotify = timeNow

                withContext(Dispatchers.Main) {
                    val contentHeight = binding.tvLogcat.height
                    val scrollViewHeight = binding.svLogcat.height
                    val isScrolledToBottomAlready = (binding.svLogcat.scrollY + scrollViewHeight) >= contentHeight * 0.9
                    binding.pbWaiting.visibility = View.GONE
                    binding.tvLogcat.text = binding.tvLogcat.text.toString() + bufferedLogLines.joinToString(separator = "\n", postfix = "\n")
                    bufferedLogLines.clear()
                    if (isScrolledToBottomAlready) {
                        binding.svLogcat.post {
                            binding.svLogcat.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
            }
        } finally {
            process?.destroy()
        }
    }
}
