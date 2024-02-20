package cat.dam.andy.picturestogallery_compose

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.ContextCompat

sealed class Permissions(vararg val permissions: String) {
    // Individual permissions
    object Camera : Permissions(Manifest.permission.CAMERA)

    // Bundled permissions
    object ImagePick : Permissions(*getImagePickPermissions())
    object ImgCamPerm : Permissions(*getImgCamPermission())
    object ImgVidCamPerm : Permissions(*getImgVidCamPermission())
    object ImgVidPerm : Permissions(*getImgVidPermission())
    object AudioPickPerm : Permissions(*getAudioPermission())

    // Grouped permissions
    object Location : Permissions(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    companion object {
        private fun getImagePickPermissions(): Array<String> {
            return if (PermissionManager.sdkEqOrAbove33()) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else if (PermissionManager.sdkEqOrAbove29()) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }

        private fun getImgCamPermission(): Array<String> {
            return if (PermissionManager.sdkEqOrAbove33()) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.CAMERA
                )
            } else if (PermissionManager.sdkEqOrAbove29()) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                )
            }
        }

        private fun getImgVidCamPermission(): Array<String> {
            return if (PermissionManager.sdkEqOrAbove33()) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.CAMERA
                )
            } else if (PermissionManager.sdkEqOrAbove29()) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                )
            }
        }

        private fun getAudioPermission(): Array<String> {
            return if (PermissionManager.sdkEqOrAbove33()) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else if (PermissionManager.sdkEqOrAbove29()) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }

        private fun getImgVidPermission(): Array<String> {
            return if (PermissionManager.sdkEqOrAbove33()) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            } else if (PermissionManager.sdkEqOrAbove29()) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }
}

class PermissionManager(var activity: ComponentActivity) : ComponentActivity() {
    private val requiredPermissions = mutableListOf<Permissions>()
    private var rationaleDescription: String? = null
    private var rationaleTitle: String? = null
    private var permanentlyDeniedDescription: String? = null
    private var callback: (Boolean) -> Unit = {}
    private var intent: Intent? = null
    private var detailedCallback: (Map<String, Boolean>) -> Unit = {}
    private val deniedList = arrayListOf<String>()
    private lateinit var permissionCheck: ActivityResultLauncher<Array<String>>
    private lateinit var sharedPreferences: SharedPreferences
    private val prefName = "permissions_pref"

    init {
        initializePermissionCheck()
        initializeSharedPreferences()
    }

    private fun initializePermissionCheck() {
        permissionCheck =
            activity.registerForActivityResult(RequestMultiplePermissions()) { grantResults ->
                sendResultAndCleanUp(grantResults)
            }
    }

    private fun initializeSharedPreferences() {
        sharedPreferences = activity.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }


    companion object {
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
        fun sdkEqOrAbove33() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        fun sdkEqOrAbove29() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
        fun sdkEqOrAbove30() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        fun sdkEqOrAbove31() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
        fun sdkEqOrAbove28() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        @SuppressLint("ObsoleteSdkInt")
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
        fun sdkEqOrAbove23() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        fun getActivityFromComponent(componentActivity: ComponentActivity): ComponentActivity {
            return componentActivity as ComponentActivity
        }
    }

    fun rationale(
        description: String, title: String = activity.getString(R.string.permission_title)
            ?: ""
    ): PermissionManager {
        rationaleDescription = description
        rationaleTitle = title
        return this
    }

    fun request(vararg permission: Permissions): PermissionManager {
        requiredPermissions.addAll(permission)
        return this
    }

    fun permissionPermanentlyDeniedIntent(intent: Intent): PermissionManager {
        this.intent = intent
        return this
    }

    fun permissionPermanentlyDeniedContent(description: String = ""): PermissionManager {
        this.permanentlyDeniedDescription =
            description.ifEmpty { activity?.getString(R.string.permission_description) }
        return this
    }

    fun checkAndRequestPermission(callback: (Boolean) -> Unit) {
        this.callback = callback
        handlePermissionRequest()
    }

    fun checkAndRequestDetailedPermission(callback: (Map<String, Boolean>) -> Unit) {
        this.detailedCallback = callback
        handlePermissionRequest()
    }

    private fun handlePermissionRequest() {
        activity.let { activity ->
            if (areAllPermissionsGranted(activity)) {
                sendPositiveResult()
            } else if (shouldShowPermissionRationale(activity)) {
                getPermissionList().forEach {
                    updatePermissionStatus(it, true)
                }
                val requiresRationaleList =
                    getPermissionList().map { Pair(it, requiresRationale(activity, it)) }
                displayRationale(
                    activity,
                    getCommaSeparatedFormattedString(requiresRationaleList.filter { it.second }
                        .map { it.first })
                )
            } else {
                val permanentlyDeniedList =
                    getPermissionList().filter { isPermanentlyDenied(activity, it) }
                if (permanentlyDeniedList.isNotEmpty()) {
                    displayPermanentlyDenied(
                        activity,
                        getCommaSeparatedFormattedString(permanentlyDeniedList)
                    )
                    cleanUp()
                } else if (getPermissionList().any { !getPermissionStatus(it) }) {
                    requestPermissions()
                }
            }
        }
    }


    @SuppressLint("StringFormatInvalid")
    private fun displayRationale(activity: Context, permission: String?) {
        AlertDialog.Builder(activity)
            .setTitle(rationaleTitle ?: activity.getString(R.string.permission_title))
            .setMessage(
                rationaleDescription ?: activity.getString(
                    R.string.permission_description,
                    permission ?: ""
                )
            )
            .setCancelable(true)
            .setNegativeButton(activity.getString(R.string.no_thanks)) { dialog, _ ->
                dialog.dismiss()
                cleanUp()
            }
            .setPositiveButton(activity.getString(R.string.button_ok)) { _, _ -> requestPermissions() }
            .show()
    }

    @SuppressLint("StringFormatInvalid")
    private fun displayPermanentlyDenied(
        activity: Context,
        deniedPermissions: String?
    ) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.permission_title))
            .setMessage(
                permanentlyDeniedDescription ?: activity.getString(
                    R.string.permission_description_permanently,
                    deniedPermissions
                )
            )
            .setCancelable(true)
            .setNegativeButton(activity.getString(R.string.no_thanks)) { dialog, _ ->
                dialog.dismiss()
                cleanUp()
            }
            .setPositiveButton(activity.getString(R.string.go_to_settings)) { _, _ ->
                val finalIntent = if (intent != null) {
                    intent
                } else {
                    val intent2 = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.packageName)
                    )
                    intent2.addCategory(Intent.CATEGORY_DEFAULT)
                    intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent2
                }
                activity.startActivity(finalIntent)
            }.show()
    }

    private fun sendPositiveResult() {
        sendResultAndCleanUp(getPermissionList().associateWith { true })
    }

    private fun sendResultAndCleanUp(grantResults: Map<String, Boolean>) {
        if (deniedList.isNotEmpty()) {
            activity.let {
                displayPermanentlyDenied(
                    it,
                    getCommaSeparatedFormattedString(deniedList)
                )
            }
        } else {
            callback(grantResults.all { it.value })
            detailedCallback(grantResults)
        }
        cleanUp()
    }

    private fun cleanUp() {
        requiredPermissions.clear()
        rationaleDescription = null
        permanentlyDeniedDescription = null
        deniedList.clear()
        callback = {}
        detailedCallback = {}
    }

    // 4 -> 2 NEW 2 Permanently denied
    private fun requestPermissions() {
        val list = getPermissionList()
        val deniedList = list.filter { isPermanentlyDenied(activity, it) }
        this.deniedList.addAll(deniedList)
        val finalList = list.subtract(deniedList.toSet())
        permissionCheck.launch(finalList.toTypedArray())
    }

    private fun areAllPermissionsGranted(activity: ComponentActivity) =
        requiredPermissions.all { it.isGranted(activity) }

    private fun shouldShowPermissionRationale(activity: ComponentActivity) =
        requiredPermissions.any { it.requiresRationale(activity) }

    private fun getPermissionList(): Array<String> {
        val reqPermissions = requiredPermissions.flatMap { it.permissions.toList() }.toTypedArray()
        return reqPermissions
    }

    private fun Permissions.isGranted(activity: ComponentActivity) =
        permissions.all { hasPermission(activity, it) }

    private fun Permissions.requiresRationale(activity: ComponentActivity): Boolean {
        return permissions.any { activity.shouldShowRequestPermissionRationale(it) }
    }

    private fun requiresRationale(activity: ComponentActivity, permission: String) =
        activity.shouldShowRequestPermissionRationale(permission) ?: false

    private fun isPermanentlyDenied(activity: ComponentActivity, permission: String): Boolean {
        if (!hasPermission(activity, permission)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //en versions noves si el permís no és sensible i està denegat és de forma permanent
                //si el permís és senseible i està denegat pot ser perquè pregunta cada vegada (caldrà veure si requereix rational)
                val permissionInfo =
                    activity.packageManager.getPermissionInfo(permission, PackageManager.GET_META_DATA)
                val flags = permissionInfo.protectionFlags
                val isUserSensitive = (flags and PermissionInfo.PROTECTION_FLAG_INSTANT) != 0
                if (isUserSensitive) { // quan sigui un permís sensible i opció de preguntar cada vegada (per exemple càmera)
                    updatePermissionStatus(permission, false)
                    return requiresRationale(activity, permission)
                } else { //quan sigui un permís no sensible i s'hagi denegat completament
                    return (true)
                }
            } else {
                //en versions anteriors només és de forma permanent quan permís denegat i requiredRationale
                updatePermissionStatus(permission, false)
                return requiresRationale(activity, permission)
            }
        } else {
            return false //quan es té el permís aquest no és denegat de forma permanent
        }
    }

    private fun hasPermission(activity: ComponentActivity, permission: String) =
        ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    private fun getCommaSeparatedFormattedString(permissions: List<String>): String? {
        val newList = mapPermissionsToStrings(permissions)
        val list = newList.toMutableList()
        return if (list.size == 1) {
            list.first()
        } else {
            list.removeLast()
            val string = list.joinToString(", ")
            string + " , " + newList.last()
        }
    }

    private fun mapPermissionsToStrings(list: List<String>): List<String?> {
        return list.map {
            when (it) {
                Manifest.permission.POST_NOTIFICATIONS -> activity?.getString(R.string.post_notifications)
                Manifest.permission.WAKE_LOCK -> activity?.getString(R.string.wake_lock)
                Manifest.permission.INTERNET -> activity?.getString(R.string.internet)
                Manifest.permission.ACCESS_NETWORK_STATE -> activity?.getString(R.string.access_network_state)
                Manifest.permission.READ_CALENDAR -> activity?.getString(R.string.read_calendar)
                Manifest.permission.WRITE_CALENDAR -> activity?.getString(R.string.write_calendar)
                Manifest.permission.READ_EXTERNAL_STORAGE -> activity?.getString(R.string.read_external_storage)
                Manifest.permission.READ_MEDIA_IMAGES -> activity?.getString(R.string.read_media_images)
                Manifest.permission.READ_MEDIA_VIDEO -> activity?.getString(R.string.read_media_video)
                Manifest.permission.READ_MEDIA_AUDIO -> activity?.getString(R.string.read_media_audio)
                Manifest.permission.SCHEDULE_EXACT_ALARM -> activity?.getString(R.string.schedule_exact_alarm)
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> activity?.getString(R.string.write_external_storage)
                Manifest.permission.CAMERA -> activity?.getString(R.string.camera)
                Manifest.permission.READ_PHONE_STATE -> activity?.getString(R.string.read_phone_state)
                Manifest.permission.READ_PHONE_NUMBERS -> activity?.getString(R.string.read_phone_numbers)
                Manifest.permission.GET_ACCOUNTS -> activity?.getString(R.string.get_accounts)
                Manifest.permission.FOREGROUND_SERVICE -> activity?.getString(R.string.foreground_service)
                Manifest.permission.ACCESS_FINE_LOCATION -> activity?.getString(R.string.access_fine_location)
                Manifest.permission.RECEIVE_BOOT_COMPLETED -> activity?.getString(R.string.receive_boot_completed)
                Manifest.permission.READ_CONTACTS -> activity?.getString(R.string.read_contacts)
                Manifest.permission.RECORD_AUDIO -> activity?.getString(R.string.record_audio)
                Manifest.permission.ACCESS_WIFI_STATE -> activity?.getString(R.string.access_wifi_state)
                Manifest.permission.MODIFY_AUDIO_SETTINGS -> activity?.getString(R.string.modify_audio_settings)
                Manifest.permission.BLUETOOTH -> activity?.getString(R.string.bluetooth)
                Manifest.permission.BLUETOOTH_CONNECT -> activity?.getString(R.string.bluetooth_connect)
                Manifest.permission.ACTIVITY_RECOGNITION -> activity?.getString(R.string.activity_recognition)
                Manifest.permission.USE_FULL_SCREEN_INTENT -> activity?.getString(R.string.use_full_screen_intent)
                Manifest.permission.VIBRATE -> activity?.getString(R.string.vibrate)
                else -> "Other"
            }
        }
    }

    private fun updatePermissionStatus(key: String, value: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    private fun getPermissionStatus(key: String): Boolean {
        return sharedPreferences.getBoolean(key, false)
    }
}

fun Context.scanForActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.scanForActivity()
        else -> {
            null
        }
    }
}

