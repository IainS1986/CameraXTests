package com.stanford.cameraxtests

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.graphics.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.stanford.cameraxtests.ImageProcessing.LuminosityAnalyzer
import java.io.File

// This is an arbitrary number we are using to keep tab of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity(), LifecycleOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private lateinit var viewFinder: TextureView

    private fun startCamera()
    {
        startCameraAnalysis()
    }

    private fun startCameraPreview() {
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(1, 1))
            setTargetResolution(Size(640, 640))
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        CameraX.bindToLifecycle(this, preview)
    }

    private fun startCameraAnalysis() {
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        var resolution = Size(metrics.widthPixels, metrics.heightPixels)
        //resolution = Size(640, 480)

        val aspectRatio = Rational(resolution.width, resolution.height)
        val rotation = viewFinder.display.rotation

//        var imageCaptureConfig = ImageCaptureConfig.Builder()
//            .setFlashMode(FlashMode.ON)
//            .setTargetRotation(rotation)
//            .setTargetAspectRatio(aspectRatio)
//            .setTargetResolution(resolution)
//            .build()

        // Setup image analysis pipeline that computes average pixel luminance
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            // Use a worker thread for image analysis to prevent glitches
            val analyzerThread = HandlerThread(
                "LuminosityAnalysis").apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))
            // In our analysis, we care more about the latest image than
            // analyzing *every* image
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetRotation(rotation)
            setTargetAspectRatio(aspectRatio)
            setTargetResolution(resolution)
        }.build()

        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetRotation(rotation)
            setTargetAspectRatio(aspectRatio)
            setTargetResolution(resolution)
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)
        preview.enableTorch(true)

        // Build the image analysis use case and instantiate our analyzer
        val analyzer = ImageAnalysis(analyzerConfig)
        analyzer.analyzer = LuminosityAnalyzer()

//        val imageCapture = ImageCapture(imageCaptureConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Set up image capture button
        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
            preview.enableTorch(!preview.isTorchOn)
//            val file = File(externalMediaDirs.first(),
//                "${System.currentTimeMillis()}.jpg")
//            imageCapture.takePicture(file,
//                object : ImageCapture.OnImageSavedListener {
//                    override fun onError(error: ImageCapture.UseCaseError,
//                                         message: String, exc: Throwable?) {
//                        val msg = "Photo capture failed: $message"
//                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        Log.e("CameraXApp", msg)
//                        exc?.printStackTrace()
//                    }
//
//                    override fun onImageSaved(file: File) {
//                        val msg = "Photo capture succeeded: ${file.absolutePath}"
//                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        Log.d("CameraXApp", msg)
//                    }
//                })
        }

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        CameraX.bindToLifecycle(this, preview, analyzer )
        preview.enableTorch(true)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
