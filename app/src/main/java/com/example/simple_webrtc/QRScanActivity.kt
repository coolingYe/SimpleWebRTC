package com.example.simple_webrtc

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.simple_webrtc.databinding.ActivityQrscanBinding
import com.example.simple_webrtc.utils.Constant.SERVICE_IP_LIST
import com.example.simple_webrtc.utils.Utils
import com.example.simple_webrtc.utils.cache.SPUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.json.JSONObject


class QRScanActivity : AppCompatActivity(), BarcodeCallback {

    private lateinit var binding: ActivityQrscanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrscanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Utils.hasPermission(this, Manifest.permission.CAMERA)) {
            initCamera()
        }

        if (!Utils.hasPermission(this, Manifest.permission.CAMERA)) {
            enabledCameraForResult.launch(Manifest.permission.CAMERA)
        }
    }

    private val enabledCameraForResult =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                initCamera()
            } else {
                Toast.makeText(this, R.string.missing_camera_permission, Toast.LENGTH_LONG).show()
                // no finish() in case no camera access wanted but contact data pasted
            }
        }

    private fun initCamera() {
        val formats = listOf(BarcodeFormat.QR_CODE)
        binding.barcodeScannerView.barcodeView?.decoderFactory = DefaultDecoderFactory(formats)
        binding.barcodeScannerView.decodeContinuous(this)
        binding.barcodeScannerView.resume()
    }

    override fun barcodeResult(result: BarcodeResult?) {
        binding.barcodeScannerView.pause()
        result?.let {
            if (it.text.isEmpty()) return
            Toast.makeText(this, it.text, Toast.LENGTH_SHORT).show()
            val planSet = HashSet<String>()
            val json = JSONObject(it.text)
            val name = json.get("name")
            val ipAddress = json.get("ipAddress")
            planSet.addAll(SPUtils.getInstance().getStringSet(SERVICE_IP_LIST))
            planSet.add("$name,$ipAddress")
            SPUtils.getInstance().put(SERVICE_IP_LIST, planSet)
            val intent = Intent()
            intent.putExtra("QRResult", "Callback")
            setResult(RESULT_OK, intent)
            finish()

        }
    }

    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {

    }

    override fun onResume() {
        super.onResume()
        binding.barcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeScannerView.pause()
    }
}