package com.example.firebasemlsample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_result_bottom_sheet.*
import java.io.File
import java.util.concurrent.Executors

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {

    // CameraX variables
    private lateinit var preview: Preview
    private lateinit var camera: Camera

    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val previewView by lazy {
        findViewById<PreviewView>(R.id.previewView)
    }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setUpBottomSheet()

        progressBar.isVisible = false

        recognizeButton.setOnClickListener {
            onClickRecognize()
        }

        changeRecognizerTypeButton.setOnClickListener {
            viewModel.changeNextRecognizerType()
        }

        viewModel.recognizerType.observe(this) {
            changeRecognizerTypeButton.text = it.labelText
        }
    }

    override fun onBackPressed() {
        if (isBottomSheetShown()) {
            hideBottomSheet()
            return
        }
        super.onBackPressed()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        preview = Preview.Builder().build()

        imageCapture = ImageCapture.Builder().build()

        val cameraSelector =
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            // Attach the preview to preview view
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun onClickRecognize() {
        onRecognitionStarted()

        // 画像を保存する
        // https://developer.android.com/training/camerax/take-photo#implementation
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputFileOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "onImageSaved")
                    // Pass image to an ML Vision API
                    when (viewModel.recognizerType.value) {
                        RecogniserType.TEXT -> processImageByCloudTextRecognizer(photoFile)
                        RecogniserType.LABEL -> processImageByCloudLabelDetector(photoFile)
                        RecogniserType.LANDMARK -> processImageByCloudLandmarkDetector(photoFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.d(TAG, "onError: errorMessage=${exception.message}")
                    exception.printStackTrace()
                }
            })
    }

    /**
     * text recognition by ML Vision Api.
     *
     * https://firebase.google.com/docs/ml/android/recognize-text
     */
    private fun processImageByCloudTextRecognizer(photoFile: File) {
        // ヒントを与える
        val options = FirebaseVisionCloudTextRecognizerOptions.Builder()
            .setLanguageHints(listOf("ja"))
            .build()
        val detector = FirebaseVision.getInstance().getCloudTextRecognizer(options)

        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
            .addOnSuccessListener { firebaseVisionText ->
                Log.d(TAG, "sucess text recognition: recognizedText = ${firebaseVisionText.text}")
                showResultOnBottomSheet(firebaseVisionText.text, photoFile, RecogniserType.TEXT)
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "failed text recognition: errorMessage=${e.message}")
                e.printStackTrace()
            }
    }

    /**
     * image labeling by ML Vision Api.
     *
     * https://firebase.google.com/docs/ml/android/label-images
     */
    private fun processImageByCloudLabelDetector(photoFile: File) {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        FirebaseVision.getInstance().cloudImageLabeler
            .processImage(FirebaseVisionImage.fromBitmap(bitmap))
            .addOnSuccessListener { labels ->
                Log.d(TAG, "success image labeling: labelsSize=${labels.size}")
                showResultOnBottomSheet(
                    labels.joinToString("\n") {
                        "text: ${it.text}\nentityId: ${it.entityId}\nconfidence: ${it.confidence}\n"
                    },
                    photoFile,
                    RecogniserType.LABEL
                )
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "failed image labeling: errorMessage=${e.message}")
                e.printStackTrace()
            }
    }

    /**
     * landmark recognition by ML Vision Api.
     *
     * https://firebase.google.com/docs/ml/android/recognize-landmarks
     */
    private fun processImageByCloudLandmarkDetector(photoFile: File) {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        FirebaseVision.getInstance().visionCloudLandmarkDetector
            .detectInImage(FirebaseVisionImage.fromBitmap(bitmap))
            .addOnSuccessListener { landmarks ->
                Log.d(TAG, "success landmark recognition: landmarksSize=${landmarks.size}")
                showResultOnBottomSheet(
                    landmarks.joinToString("\n") {
                        "landmarkName: ${it.landmark}\nentityId: ${it.entityId}\nconfidence: ${it.confidence}\n"
                    },
                    photoFile,
                    RecogniserType.LANDMARK
                )
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "failed landmark recognition: errorMessage=${e.message}")
                e.printStackTrace()
            }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(resultBottomSheet)
        bottomSheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {

                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                        }
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {
                        }
                        BottomSheetBehavior.STATE_SETTLING -> {
                        }
                        else -> "not defined state"
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                }
            })

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun showResultOnBottomSheet(
        resultText: String,
        photoFile: File,
        recogniserType: RecogniserType,
    ) {
        onRecognitionFinished()

        topTitle.text = "Result of ${recogniserType.labelText}"
        resultTextView.text = resultText
        Glide.with(inputImageView).load(photoFile).into(inputImageView as ImageView)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun onRecognitionStarted() {
        progressBar.isVisible = true
        recognizeButton.isEnabled = false
        changeRecognizerTypeButton.isEnabled = false
    }

    private fun onRecognitionFinished() {
        progressBar.isVisible = false
        recognizeButton.isEnabled = true
        changeRecognizerTypeButton.isEnabled = true
    }

    private fun isBottomSheetShown() = bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }


    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}