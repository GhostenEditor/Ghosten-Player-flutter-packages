package com.ghosten.file_picker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.File

class FilePickerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var activity: Activity
    private var methodCallResult: Result? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, PLUGIN_NAMESPACE)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivity() {
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "externalStoragePath" -> result.success(Environment.getExternalStorageDirectory().path)
            "moviePath" -> result.success(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path)
            "musicPath" -> result.success(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).path)
            "downloadPath" -> result.success(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ).path
            )

            "cachePath" -> result.success(context.cacheDir.path)
            "filePath" -> result.success(context.filesDir.path)
            "externalUsbStorages" -> result.success(getUniversalUSBPath())
            "requestStoragePermission" -> requestStoragePermission(result)
            "requestStorageManagePermission" -> requestStorageManagePermission(result)
            "requestInstallPermission" -> requestInstallPermission(result)
            "installApk" -> installApk(call.arguments as String, result)
            else -> result.notImplemented()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        resultCode: Array<out String>,
        data: IntArray
    ): Boolean {
        return when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                methodCallResult?.success(data.all { it == PackageManager.PERMISSION_GRANTED })
                methodCallResult = null
                true
            }

            REQUEST_INSTALL_PERMISSION -> {
                methodCallResult?.success(data.all { it == PackageManager.PERMISSION_GRANTED })
                methodCallResult = null
                true
            }

            REQUEST_INSTALL -> {
                true
            }

            else -> false
        }
    }

    private fun getUniversalUSBPath(): List<HashMap<String, String>> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes: List<StorageVolume> = storageManager.storageVolumes
        val list = mutableListOf<HashMap<String, String>>()

        for (volume in storageVolumes) {
            if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    list.add(HashMap<String, String>().apply {
                        this["path"] = volume.directory!!.absolutePath
                        this["desc"] = volume.getDescription(context)
                    })
                } else {
                    try {
                        val getPath = volume.javaClass.getMethod("getPath")
                        list.add(HashMap<String, String>().apply {
                            this["path"] = getPath.invoke(volume) as String
                            this["desc"] = ""
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    private fun requestStorageManagePermission(result: Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.setData(("package:" + activity.packageName).toUri())
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(intent)
                    result.success(false)
                } else {
                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    fallbackIntent.setData(("package:" + activity.packageName).toUri())
                    activity.startActivity(fallbackIntent)
                    result.success(false)
                }
            } else {
                result.success(true)
            }
        } else {
            result.success(true)
        }
    }

    private fun requestStoragePermission(result: Result) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
                methodCallResult = result
                return activity.requestPermissions(permissions, REQUEST_STORAGE_PERMISSION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                val permissions = arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                )
                methodCallResult = result
                return activity.requestPermissions(permissions, REQUEST_STORAGE_PERMISSION)
            }
        }
        result.success(true)
    }

    private fun installApk(filePath: String?, result: Result) {
        if (filePath.isNullOrEmpty()) {
            return result.error("0", "FilePath Must Not Null", null)
        }
        val pName = context.packageName
        if (pName.isNullOrEmpty()) {
            return result.error("0", "Failed To Obtain PackageName Must Not Null", null)
        }
        if (hasInstallPermission()) {
            val intent = getInstallAppIntent(context, pName, filePath) ?: return result.error(
                "0",
                "Not Get Install Intent",
                null
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            activity.startActivityForResult(intent, REQUEST_INSTALL)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun getInstallAppIntent(
        context: Context?,
        packageName: String,
        filePath: String?,
    ): Intent? {
        if (context == null) return null
        if (filePath.isNullOrEmpty()) return null
        var file = File(filePath)
        if (!file.exists()) return null
        Log.i("test", "getInstallAppIntent:${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            val storePath =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            // DIRECTORY_DOWNLOADS
            val downloadsDir = File(storePath).apply {
                if (!exists()) {
                    mkdir()
                }
            }
            // DIRECTORY_DOWNLOADS / packageName
            val downloadsAppDir = File(downloadsDir, packageName).apply {
                if (!exists()) {
                    mkdir()
                }
            }
            Log.i("test", "getInstallAppIntent:$storePath")
            val destFile = File(downloadsAppDir, file.name)
            file.copyTo(destFile, overwrite = true)
            file = destFile
        }

        val uri: Uri = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Uri.fromFile(file)
        } else {
            InstallFileProvider.getUriForFile(context, file)
        }
        Log.i("test", "getInstallAppIntent:$uri")
        val intent = Intent(Intent.ACTION_VIEW)
        val type = "application/vnd.android.package-archive"
        intent.setDataAndType(uri, type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        return intent
    }

    private fun hasInstallPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager?.canRequestPackageInstalls() ?: false
    }

    private fun requestInstallPermission(result: Result) {
        if (hasInstallPermission()) return result.success(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = "package:${context.packageName}".toUri()
            methodCallResult = result
            activity.startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
        } else {
            result.success(true)
        }
    }

    companion object {
        const val PLUGIN_NAMESPACE = "com.ghosten.player/file_picker"
        const val REQUEST_STORAGE_PERMISSION = 7454
        const val REQUEST_INSTALL = 1024
        const val REQUEST_INSTALL_PERMISSION = 1023
    }
}

class InstallFileProvider : FileProvider() {
    companion object {
        fun getUriForFile(context: Context, file: File): Uri {
            val authority = "${context.packageName}.installFileProvider.install"
            return getUriForFile(context, authority, file)
        }
    }
}
