package cat.dam.andy.picturestogallery_compose

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cat.dam.andy.picturestogallery_compose.ui.theme.PicturesToGallery_composeTheme
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val context: Context by lazy { this }
    private var uriPhotoImage: Uri? = null
    private val viewModel by viewModels<MainViewModel>()
    private var launcherTakePicture by mutableStateOf<ActivityResultLauncher<Intent>?>(null)
    private val launcherGallery = rememberLauncher { handleGalleryResult(it) }
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        launcherTakePicture = rememberLauncherTakePicture(launcherTakePictureCallback)
        setContent {
            PicturesToGallery_composeTheme {
                MainScreen()
            }
        }
    }

    // Funcions del Compose que utilitzen ActivityResultLauncher per gestionar els resultats
    private fun rememberLauncher(onResult: (ActivityResult) -> Unit): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult(), onResult)
    }

    private fun rememberLauncherTakePicture(onResult: (ActivityResult) -> Unit): ActivityResultLauncher<Intent> {
        return rememberLauncher(onResult)
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            DisplayImage()
            Button(
                onClick = {
                    handleTakePictureButtonClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Take Picture")
            }

            Button(
                onClick = {
                    handleGalleryButtonClick(launcherGallery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Open Gallery")
            }
        }
    }

    private @Composable
    fun DisplayImage() {
        val imageUri = viewModel.imageUri.value
        //Utilitza llibreria Coil
        if (imageUri != null) {
            val painter = rememberAsyncImagePainter(model = imageUri)
            Image(
                painter = painter,
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(8.dp)
            )
        } else {
            // Mostra una imatge predeterminada o gestiona l'absència d'imatge
            // en funció de les teves necessitats.
        }
    }

    private fun handleGalleryButtonClick(launcherGallery: ActivityResultLauncher<Intent>) {
            handleMediaAccessGallery(launcherGallery)
    }

    private fun handleTakePictureButtonClick() {
        permissionManager
            .request(Permissions.ImgCamPerm)
            .rationale(description = "Permission needed to take pictures and to save them", title = "Camera and Storage Permission")
            .checkAndRequestPermission { isGranted ->
                if (isGranted) {
                    captureImageAndSaveToGalleryInternal()
                } else {
                    Toast.makeText(this@MainActivity, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleMediaAccessGallery(launcherGallery: ActivityResultLauncher<Intent>) {
        permissionManager
            .request(Permissions.ImagePick)
            .rationale(description = "Permission needed to access media", title = "Media Access Permission")
            .checkAndRequestPermission { isGranted ->
                if (isGranted) {
                    launcherGallery.launch(getGalleryIntent())
                } else {
                    Toast.makeText(this@MainActivity, "Gallery permission denied", Toast.LENGTH_SHORT).show()
                }
            }
}

    private fun captureImageAndSaveToGalleryInternal() {
        permissionManager
            .request(Permissions.ImagePick)
            .rationale(
                description = "Permission needed to save image",
                title = "Storage Permission"
            )
            .checkAndRequestPermission { isGranted ->
                if (isGranted) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.TITLE, "Image")
                        put(MediaStore.Images.Media.DESCRIPTION, "From your Camera")
                    }
                    uriPhotoImage =
                        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, uriPhotoImage)
                    }
                    // Utilitza el launcherTakePicture ja inicialitzat
                    launcherTakePicture?.launch(cameraIntent)
                } else {
                    Toast.makeText(this@MainActivity, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleGalleryResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val pickedImageUri = data.data
                viewModel.imageUri.value = pickedImageUri
            }
        } else {
            Toast.makeText(this@MainActivity, "Cancelled...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleTakePictureResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this@MainActivity, "Image captured", Toast.LENGTH_SHORT).show()
            viewModel.imageUri.value = uriPhotoImage
            if (!apiRequiresScopePermissions()) {
                refreshGallery() //refresca gallery per veure nou fitxer (OLD API)
            }
            //Intent data = result.getData(); //si volguessim només la miniatura
        } else {
            Toast.makeText(
                this@MainActivity, getString(R.string.photo_capture_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getGalleryIntent(): Intent {
        val intent: Intent = if (apiRequiresScopePermissions()) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_PICK)
        }

        try {
            if (!apiRequiresScopePermissions()) {
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                intent.action = Intent.ACTION_GET_CONTENT
            }

            if (intent.resolveActivity(packageManager) == null) {
                Toast.makeText(
                    this@MainActivity, "El seu dispositiu no permet accedir a la galeria",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return intent
    }

//    private fun captureImageAndSaveToGallery() {
//        val values = ContentValues().apply {
//            put(MediaStore.Images.Media.TITLE, "Image")
//            put(MediaStore.Images.Media.DESCRIPTION, "From your Camera")
//        }
//        uriPhotoImage =
//            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
//
//        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
//            putExtra(MediaStore.EXTRA_OUTPUT, uriPhotoImage)
//        }
//
//        // Utilitza el launcherTakePicture ja inicialitzat
//        launcherTakePicture?.launch(cameraIntent)
//    }

    fun captureImageAndSaveToGalleryOldAPI() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Create the File where the photo should go
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(
                    this@MainActivity, "Error en la creació del fitxer",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                val values = ContentValues()
                values.put(MediaStore.Images.Media.TITLE, getString(R.string.picture_title))
                values.put(
                    MediaStore.Images.Media.DESCRIPTION,
                    getString(R.string.picture_time) + " " + System.currentTimeMillis()
                )
                val uriImage = FileProvider.getUriForFile(
                    this,
                    this.packageName + ".provider",  //(use your app signature + ".provider" )
                    photoFile
                )
                values.put(MediaStore.Images.Media.DATA, uriImage.path)
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage)
                val launcherTakePicture = rememberLauncherTakePicture(launcherTakePictureCallback)
                launcherTakePicture.launch(intent)
            } else {
                Toast.makeText(
                    this@MainActivity, getString(R.string.picture_creation_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                this@MainActivity, getString(R.string.camera_access_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val launcherTakePictureCallback = { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            // La foto s'ha capturat amb èxit i es pot afegir a la galeria aquí si és necessari.
            Toast.makeText(this, "Image captured", Toast.LENGTH_SHORT).show()
            viewModel.imageUri.value = uriPhotoImage
            if (!apiRequiresScopePermissions()) {
                refreshGallery() //refresca gallery per veure nou fitxer (OLD API)
            }
            //Intent data = result.getData(); //si volguessim només la miniatura
        } else {
            // L'usuari pot haver cancel·lat la presa de la foto o pot haver-hi altres problemes.
            Toast.makeText(
                this@MainActivity, getString(R.string.photo_capture_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createImageFile(): File? {
        val wasSuccessful: Boolean //just for testing mkdirs
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val imageFileName = "IMG_" + timeStamp + "_"
        val storageDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + File.separator + this.packageName)
        wasSuccessful = if (!storageDir.exists()) {
            storageDir.mkdir()
        } else {
            storageDir.mkdirs()
        }
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )
        val currentPhotoPath = image.absolutePath
        uriPhotoImage = Uri.fromFile(image)
        return image
    }

    private fun refreshGallery() {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = uriPhotoImage
        this.sendBroadcast(mediaScanIntent)
    }

    private fun apiRequiresScopePermissions(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(
            Build.VERSION_CODES.R
        ) >= 2
    }

    private fun apiRequiresMediaAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}