package com.example.simple_webrtc.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.webrtc.*
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*
import java.util.regex.Pattern


internal object Utils {
    fun getThreadInfo(): String {
        val thread = Thread.currentThread()
        return "@[name=${thread.name}, id=${thread.id}]"
    }

    fun assertIsTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Expected condition to be true")
        }
    }

    fun checkIsOnMainThread() {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            throw IllegalStateException("Code must run on the main thread!")
        }
    }

    fun checkIsNotOnMainThread() {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            throw IllegalStateException("Code must not run on the main thread!")
        }
    }

    fun printStackTrace() {
        try {
            throw Exception("printStackTrace() called")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return (ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private val NAME_PATTERN = Pattern.compile("[\\w][\\w _-]{1,22}[\\w]")

    // Check for a name that has no funny unicode characters
    // and to not let them look to much like other names.
    fun isValidName(name: String?): Boolean {
        if (name == null || name.isEmpty()) {
            return false
        }
        return NAME_PATTERN.matcher(name).matches()
    }

    fun byteArrayToHexString(bytes: ByteArray?): String {
        if (bytes == null) {
            return ""
        }

        return bytes.joinToString(separator = "") { eachByte ->
            "%02X".format(eachByte)
        }
    }

    fun hexStringToByteArray(str: String?): ByteArray? {
        if (str == null || (str.length % 2) != 0 || !str.all { it in '0'..'9' || it in 'A'..'F' }) {
            return null
        }

        return str.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // write file to external storage
    fun writeExternalFile(filepath: String, data: ByteArray?) {
        val file = File(filepath)
        if (file.exists() && file.isFile) {
            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filepath")
            }
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(data)
        fos.close()
    }

    // read file from external storage
    fun readExternalFile(filepath: String): ByteArray {
        val file = File(filepath)
        if (!file.exists() || !file.isFile) {
            throw IOException("File does not exist: $filepath")
        }
        val fis = FileInputStream(file)
        var nRead: Int
        val data = ByteArray(16384)
        val buffer = ByteArrayOutputStream()
        while (fis.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        fis.close()
        return buffer.toByteArray()
    }

    fun getExternalFileSize(ctx: Context, uri: Uri?): Long {
        val cursor = ctx.contentResolver.query(uri!!, null, null, null, null)
        cursor!!.moveToFirst()
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0) {
            val size = cursor.getLong(index)
            cursor.close()
            return size
        } else {
            cursor.close()
            return -1
        }
    }

    fun readExternalFile(ctx: Context, uri: Uri): ByteArray {
        val size = getExternalFileSize(ctx, uri).toInt()
        val isstream = ctx.contentResolver.openInputStream(uri)
        val buffer = ByteArrayOutputStream()
        var nRead = 0
        val dataArray = ByteArray(size)
        while (isstream != null && isstream.read(dataArray, 0, dataArray.size)
                .also { nRead = it } != -1
        ) {
            buffer.write(dataArray, 0, nRead)
        }
        isstream?.close()
        return dataArray
    }

    fun writeExternalFile(ctx: Context, uri: Uri, dataArray: ByteArray) {
        val fos = ctx.contentResolver.openOutputStream(uri)
        fos!!.write(dataArray)
        fos.close()
    }

    // write file to external storage
    fun writeInternalFile(filePath: String, dataArray: ByteArray) {
        val file = File(filePath)
        if (file.exists() && file.isFile) {
            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filePath")
            }
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(dataArray)
        fos.close()
    }

    // read file from external storage
    fun readInternalFile(filePath: String): ByteArray {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw IOException("File does not exist: $filePath")
        }
        val fis = FileInputStream(file)
        var nRead: Int
        val dataArray = ByteArray(16384)
        val buffer = ByteArrayOutputStream()
        while (fis.read(dataArray, 0, dataArray.size).also { nRead = it } != -1) {
            buffer.write(dataArray, 0, nRead)
        }
        fis.close()
        return buffer.toByteArray()
    }

    fun getIpAddressString(): String? {
        try {
            val enNetI = NetworkInterface
                .getNetworkInterfaces()
            while (enNetI.hasMoreElements()) {
                val netI = enNetI.nextElement()
                val enumIpAddr = netI
                    .inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return "0.0.0.0"
    }

    fun getQRCode(content: String): Bitmap {
        val multiFormatWriter = MultiFormatWriter()
        val hints = Hashtable<EncodeHintType, Any>()
        //设置空白边距的宽度
        hints[EncodeHintType.MARGIN] = 0
        val bitMatrix =
            multiFormatWriter.encode(content, BarcodeFormat.QR_CODE, 250, 250, hints)
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.createBitmap(bitMatrix)
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

}
