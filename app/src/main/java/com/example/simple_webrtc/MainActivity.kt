package com.example.simple_webrtc

import android.Manifest
import android.app.Dialog
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.example.gesturelib.Config
import com.example.gesturelib.GestureManager
import com.example.gesturelib.LocationEnum
import com.example.simple_webrtc.databinding.ActivityMainBinding
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.rtc.WebRTCClient
import com.example.simple_webrtc.utils.Constant
import com.example.simple_webrtc.utils.Constant.SERVICE_IP_LIST
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.Utils.getIpAddressString
import com.example.simple_webrtc.utils.Utils.getQRCode
import com.example.simple_webrtc.utils.cache.SPUtils
import org.webrtc.EglBase
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connection: ServiceConnection

    private var binder: MainService.MainBinder? = null
    private var intentActivityResult: ActivityResultLauncher<Intent>? = null
    private var contactAdapter: ContactAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setSupportActionBar(binding.toolbar)
        MainService.start(this)
        SocketService.setContext(this)

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service as MainService.MainBinder
            }

            override fun onServiceDisconnected(name: ComponentName?) {

            }
        }

        initViews()
        bindService(Intent(this, MainService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun initViews() {
        binding.rvContacts.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        intentActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            run {
                if (it.resultCode == RESULT_OK) {
                    it.data?.getStringExtra("QRResult")?.let {
                        updateList()
                    }
                }
            }
        }

        val contactSet = SPUtils.getInstance().getStringSet(SERVICE_IP_LIST)
        if (contactSet.size > 0) {
            val dataList = ArrayList(contactSet)
            contactAdapter = ContactAdapter(dataList)
            contactAdapter?.setOnClickListener = object : (View, String) -> Unit {
                override fun invoke(p1: View, p2: String) {
                    val intent = Intent(this@MainActivity, CallActivity::class.java)
                    intent.action = CallActivity.ACTION_OUTGOING_CALL
                    intent.putExtra(CallActivity.EXTRA_CONTACT, Contact("", p2))
                    startActivity(intent)
                }
            }
            binding.rvContacts.adapter = contactAdapter
        }

    }

    private fun updateList() {
        val contactSet = SPUtils.getInstance().getStringSet(SERVICE_IP_LIST)
        if (contactSet.size > 0) {
            val dataList = ArrayList(contactSet)
            contactAdapter?.updateList(dataList)
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_camera -> {
                val intent = Intent(this, QRScanActivity::class.java)
                intentActivityResult?.launch(intent)
                true
            }
            R.id.action_qr -> {
                val ipv4Address = getIpAddressString()
                ipv4Address?.let { ip ->
                    if (ip.isNotEmpty()) {
                        showQrDialog(getQRCode(ip))
                    }
                }
                true
            }
            R.id.action_settings -> {
                Toast.makeText(this, "setting clicked", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (!EasyPermissions.hasPermissions(this, *permissions)) {
            // Permission is not granted
            Log.d("checkCameraPermissions", "No Camera Permissions")
            EasyPermissions.requestPermissions(this, "Please provide permissions", 1, *permissions)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.d(ContentValues.TAG, "Permission request successful")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.d(ContentValues.TAG, "Permission request failed")
    }

    private fun showQrDialog(bitmap: Bitmap) {
        val qrDialog = Dialog(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        val view = layoutInflater.inflate(R.layout.dialog_qr, null)
        qrDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        qrDialog.setContentView(view)

        val window: Window? = qrDialog.window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val layoutParams = window.attributes
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = layoutParams
        }

        qrDialog.show()
        val ivQr = view.findViewById<ImageView>(R.id.iv_qr)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        ivQr.setImageBitmap(bitmap)
        btnCancel.setOnClickListener {
            qrDialog.dismiss()
        }
    }

}