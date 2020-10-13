package com.example.firebasemlsample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import kotlinx.android.synthetic.main.activity_main.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
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
            recognizeButton.setOnClickListener {
                onClickRecognize()
            }

            // Attach the preview to preview view
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun onClickRecognize() {
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                Log.d(TAG, "onCaptureSuccess")
                val mediaImage = imageProxy.image
                mediaImage?.let {
                    val image = FirebaseVisionImage.fromMediaImage(
                        it,
                        degreesToFirebaseRotation(imageProxy.imageInfo.rotationDegrees)
                    )
                    // Pass image to an ML Vision API

                    // ヒントを与える
                    val options = FirebaseVisionCloudTextRecognizerOptions.Builder()
                        .setLanguageHints(listOf("ja"))
                        .build()
                    val detector = FirebaseVision.getInstance().getCloudTextRecognizer(options)

                    detector.processImage(image)
                        .addOnSuccessListener { firebaseVisionText ->
                            Log.d(TAG, "recognized text = ${firebaseVisionText.text}")
                            imageProxy.close()
                        }
                        .addOnFailureListener { e ->
                            Log.d(TAG, "failed recognizing: errorMessage=${e.message}")
                            e.printStackTrace()
                            imageProxy.close()
                        }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.d(TAG, "onError: errorMessage=${exception.message}")
                exception.printStackTrace()
            }
        })
    }

    private fun degreesToFirebaseRotation(degrees: Int): Int = when (degrees) {
        0 -> FirebaseVisionImageMetadata.ROTATION_0
        90 -> FirebaseVisionImageMetadata.ROTATION_90
        180 -> FirebaseVisionImageMetadata.ROTATION_180
        270 -> FirebaseVisionImageMetadata.ROTATION_270
        else -> throw Exception("Rotation must be 0, 90, 180, 270.")
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}