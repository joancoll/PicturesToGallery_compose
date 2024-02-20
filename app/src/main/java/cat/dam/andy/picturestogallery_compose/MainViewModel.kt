package cat.dam.andy.picturestogallery_compose

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf

class MainViewModel : ViewModel() {
    val imageUri = mutableStateOf<Uri?>(null)
}