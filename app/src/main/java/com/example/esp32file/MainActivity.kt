package com.example.esp32file

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import java.io.BufferedOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)

        if (android.os.Build.VERSION.SDK_INT >= 31)
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 10)

        findViewById<Button>(R.id.connect).setOnClickListener { connect() }
        findViewById<Button>(R.id.select).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 20)
        }
    }

    private fun connect() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { status.text = "Bluetooth tidak tersedia"; return }
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Izinkan Bluetooth lalu tekan Connect lagi"; return
        }
        val device = adapter.bondedDevices.firstOrNull { it.name == "ESP32-FILE" }
        if (device == null) { status.text = "Pair ESP32-FILE dulu di Settings"; return }
        status.text = "Connecting..."
        thread {
            try {
                socket?.close()
                socket = device.createRfcommSocketToServiceRecord(spp)
                socket!!.connect()
                runOnUiThread { status.text = "Connected: ESP32-FILE" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Gagal: ${e.message}" }
            }
        }
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        if (r == 20 && c == RESULT_OK && d?.data != null) sendFile(d.data!!)
    }

    private fun sendFile(uri: android.net.Uri) {
        val s = socket ?: run { status.text = "Connect ESP32 dulu"; return }
        thread {
            try {
                val name = getName(uri)
                val size = contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1
                if (size < 0) throw Exception("Ukuran file tidak diketahui")
                runOnUiThread { progress.visibility=ProgressBar.VISIBLE; progress.progress=0; status.text="Menunggu ESP32..." }

                val out = BufferedOutputStream(s.outputStream)
                out.write("SEND|$name|$size\n".toByteArray()); out.flush()
                val reply = readLine(s.inputStream)
                if (reply != "READY") throw Exception("ESP32: $reply")

                val input = contentResolver.openInputStream(uri)!!
                val buf=ByteArray(4096); var sent=0L
                while (true) {
                    val n=input.read(buf); if(n<=0) break
                    out.write(buf,0,n); out.flush(); sent+=n
                    runOnUiThread { progress.progress=((sent*100)/size).toInt() }
                }
                input.close()
                val done=readLine(s.inputStream)
                runOnUiThread {
                    status.text=if(done=="DONE") "Selesai: $name" else "ESP32: $done"
                    progress.visibility=ProgressBar.GONE
                }
            } catch(e:Exception) {
                runOnUiThread { status.text="Transfer gagal: ${e.message}"; progress.visibility=ProgressBar.GONE }
            }
        }
    }

    private fun readLine(input: InputStream): String {
        val sb=StringBuilder()
        while(true){ val x=input.read(); if(x<0||x==10) break; if(x!=13) sb.append(x.toChar()) }
        return sb.toString()
    }

    private fun getName(uri: android.net.Uri): String {
        var n="file.bin"
        contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
            if(it.moveToFirst()) n=it.getString(0)
        }
        return n.replace("|","_").replace("\n","_")
    }
}
