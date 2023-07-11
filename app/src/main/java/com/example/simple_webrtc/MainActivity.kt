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
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.simple_webrtc.databinding.ActivityMainBinding
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.utils.Constant.SERVICE_IP_LIST
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.Utils.getQRCode
import com.example.simple_webrtc.utils.cache.SPUtils
import org.json.JSONObject
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
                binder?.refreshContactList().let { contacts ->
                    if (contacts != null) {
                        contactAdapter?.updateList(contacts)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {

            }
        }

        initViews()
        bindService(Intent(this, MainService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun initViews() {
        binding.rvContacts.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        intentActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                run {
                    if (it.resultCode == RESULT_OK) {
                        it.data?.getStringExtra("QRResult")?.let {
                            updateList()
                        }
                    }
                }
            }
        contactAdapter = ContactAdapter(emptyList())
        binding.rvContacts.adapter = contactAdapter

        contactAdapter?.setOnClickListener = object : (View, Contact) -> Unit {
            override fun invoke(p1: View, p2: Contact) {
                val intent = Intent(this@MainActivity, CallActivity::class.java)
                intent.action = CallActivity.ACTION_OUTGOING_CALL
                intent.putExtra(CallActivity.EXTRA_CONTACT, p2)
                startActivity(intent)
            }
        }
        contactAdapter?.setOnLongClickListener = object : (View, Contact) -> Unit {
            override fun invoke(p1: View, p2: Contact) {
                val popupMenu = PopupMenu(this@MainActivity, p1)
                popupMenu.menuInflater.inflate(R.menu.menu_item, popupMenu.menu)
                popupMenu.show()
                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            showEditDialog(p2)
                        }
                        R.id.action_delete -> {
                            val targetContact = p2.name + "," + p2.ipAddress
                            delContact(targetContact)
                            binder?.refreshContactList()?.let { contactAdapter?.updateList(it) }
                        }
                    }
                    true
                }
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(refreshContactListReceiver, IntentFilter("refresh_contact_list"))

        refreshContactListBroadcast()
    }

    private val refreshContactListReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this, "trigger refreshContactList() from broadcast at ${lifecycle.currentState}")
            refreshContactList()
        }
    }

    private fun refreshContactList() {
        Log.d(this, "refreshContactList")

        val binder = binder ?: return
        val contacts = binder.getContactList()

        runOnUiThread {
            contactAdapter?.updateList(contacts)
        }
    }

    fun delContact(targetContact: String) {
        val contactSet = HashSet<String>()
        contactSet.addAll(SPUtils.getInstance().getStringSet(SERVICE_IP_LIST))
        if (contactSet.size > 0) {
            contactSet.remove(targetContact)
            SPUtils.getInstance().put(SERVICE_IP_LIST, contactSet)
        }
    }

    fun showEditDialog(contact: Contact) {
        val view = View.inflate(this@MainActivity, R.layout.dialog_contact_edit, null)
        val editName = view.findViewById<EditText>(R.id.ed_contact_name)
        val editIp = view.findViewById<EditText>(R.id.ed_contact_ip)
        editName.setText(contact.name)
        editIp.setText(contact.ipAddress)
        AlertDialog.Builder(this).apply {
            setTitle("Edit Contact")
            setView(view)
            setNegativeButton("Cancel", null)
            setPositiveButton("OK") { dialog, _ ->
                run {
                    val targetContact = editName.text.toString() + "," + editIp.text
                    val oldContact = contact.name + "," + contact.ipAddress
                    delContact(oldContact)
                    binder?.addContact(targetContact)
                    binder?.let { contactAdapter?.updateList(it.refreshContactList()) }
                    dialog.cancel()
                }
            }
            show()
        }
    }

    private fun refreshContactListBroadcast() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("refresh_contact_list"))
    }

    private fun showAddDialog() {
        val view = View.inflate(this@MainActivity, R.layout.dialog_contact_edit, null)
        val editName = view.findViewById<EditText>(R.id.ed_contact_name)
        val editIp = view.findViewById<EditText>(R.id.ed_contact_ip)
        AlertDialog.Builder(this).apply {
            setTitle("Add Contact")
            setView(view)
            setNegativeButton("Cancel", null)
            setPositiveButton("OK") { dialog, _ ->
                run {
                    val targetContact = editName.text.toString() + "," + editIp.text
                    binder?.addContact(targetContact)
                    binder?.refreshContactList()?.let { contactAdapter?.updateList(it) }
                    dialog.cancel()
                }
            }
            show()
        }
    }

    private fun updateList() {
        val contactSet = SPUtils.getInstance().getStringSet(SERVICE_IP_LIST)
        if (contactSet.size > 0) {
            val dataList = ArrayList<Contact>()
            contactSet.forEach {
                val contact = Contact(it.substringBefore(","), it.substringAfter(","))
                dataList.add(contact)
            }
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
            R.id.action_refresh -> {
                binder?.refreshContactList()?.let { binder?.pingContacts(it) }
                true
            }
            R.id.action_add -> {
                showAddDialog()
                true
            }
            R.id.action_camera -> {
                val intent = Intent(this, QRScanActivity::class.java)
                intentActivityResult?.launch(intent)
                true
            }
            R.id.action_qr -> {
                val contact = binder?.getSelfContact()
                contact?.let {
                    val json = JSONObject().apply {
                        put("name", contact.name)
                        put("ipAddress", contact.ipAddress)
                    }

                    showQrDialog(getQRCode(json.toString()))
                }
                true
            }
            R.id.action_about -> {
                Toast.makeText(this, binder?.getCurrentIPAddress(), Toast.LENGTH_SHORT).show()
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
        ivQr.setImageBitmap(bitmap)
    }

    override fun onResume() {
        super.onResume()
        binder?.refreshContactList()?.let { binder?.pingContacts(it) }
    }

}