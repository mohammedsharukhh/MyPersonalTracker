package com.mohammedsharukms.mypersonaltracker.ui.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.mohammedsharukms.mypersonaltracker.container
import kotlinx.coroutines.launch

/** Camera / photo-library / document-scanner launchers that hand back a saved photo file name. */
class PhotoCapture internal constructor(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
    val scanDocument: () -> Unit,
)

/**
 * @param maxDimension longest side of the stored image; bills use more so small print stays readable.
 */
@Composable
fun rememberPhotoCapture(maxDimension: Int = 1600, onSaved: (String) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val photos = context.container.photos
    val scope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    fun save(uri: Uri) = scope.launch { photos.save(uri, maxDimension)?.let(onSaved) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) save(uri)
        pendingUri = null
    }
    fun launchCamera() {
        val (_, uri) = photos.newCaptureTarget()
        pendingUri = uri
        camera.launch(uri)
    }
    // Declaring CAMERA in the manifest means the system camera intent also needs it granted.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) save(uri)
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
        scope.launch { photos.saveStacked(pages)?.let(onSaved) }
    }

    return remember(maxDimension) {
        PhotoCapture(
            takePhoto = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            },
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            scanDocument = {
                val activity = context as? Activity
                if (activity != null) {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                        // Scanner unavailable (e.g. no Play services): fall back to the photo library.
                        .addOnFailureListener { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
            },
        )
    }
}
