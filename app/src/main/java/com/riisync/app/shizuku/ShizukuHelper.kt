/**
 * Shizuku Connection and Status Helper.
 * This file manages the interaction with the Shizuku API, including binder monitoring,
 * permission requests, and binding to the privileged User Service.
 */
package com.riisync.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

/**
 * Singleton that centralizes: detecting if Shizuku is active, asking for permission
 * at runtime, and connecting to the privileged IFileService.
 * Enhanced with live state listeners.
 */
object ShizukuHelper {

    private const val REQUEST_CODE = 5001

    /** State indicating if the Shizuku binder is currently available. */
    val isAvailable = mutableStateOf(false)
    /** State indicating if the application has been granted Shizuku permissions. */
    val hasPermission = mutableStateOf(false)
    /** Instance of the privileged file service if connected. */
    var fileService: IFileService? = null
        private set

    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private var isInitialized = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            fileService = IFileService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            fileService = null
        }
    }

    private val binderReceivedListener = OnBinderReceivedListener {
        isAvailable.value = true
        // Re-check permission when binder is received (e.g. Shizuku restarted)
        checkPermissionStatic()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isAvailable.value = false
        hasPermission.value = false
        fileService = null
    }

    /**
     * Initializes Shizuku listeners and performs an initial status check.
     */
    fun init(context: Context) {
        if (isInitialized) {
            checkStatus(context)
            return
        }

        isAvailable.value = Shizuku.pingBinder()

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        Shizuku.addRequestPermissionResultListener(OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                hasPermission.value = grantResult == PackageManager.PERMISSION_GRANTED
                if (hasPermission.value) bindService(context)
            }
        })

        checkStatus(context)
        isInitialized = true
    }

    /**
     * Checks the current availability and permission status of Shizuku.
     */
    fun checkStatus(context: Context) {
        isAvailable.value = Shizuku.pingBinder()
        if (isAvailable.value) {
            hasPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (hasPermission.value) bindService(context)
        }
    }

    /**
     * Performs a static check for permission without requiring a Context.
     */
    private fun checkPermissionStatic() {
        if (Shizuku.pingBinder()) {
            hasPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Requests Shizuku permissions from the user.
     */
    fun requestPermission() {
        if (Shizuku.isPreV11()) return 
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    /**
     * Binds to the privileged file service provided by Shizuku.
     */
    private fun bindService(context: Context) {
        if (fileService != null) return
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, FileServiceImpl::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("fileservice")
            .debuggable(false)
            .version(1)
        userServiceArgs = args
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Unbinds from the Shizuku user service.
     */
    fun unbind() {
        userServiceArgs?.let { 
            try {
                Shizuku.unbindUserService(it, connection, true) 
            } catch (e: Exception) {}
        }
    }

    /**
     * Checks if the Shizuku manager application is installed on the device.
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
