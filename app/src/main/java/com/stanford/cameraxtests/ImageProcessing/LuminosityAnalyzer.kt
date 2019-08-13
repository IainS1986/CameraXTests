package com.stanford.cameraxtests.ImageProcessing

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class LuminosityAnalyzer : ImageAnalysis.Analyzer {
    private var lastAnalyzedTimestamp = 0L

    /**
     * Helper extension function used to extract a byte array from an
     * image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        val currentTimestamp = System.currentTimeMillis()
        // Since format in ImageAnalysis is YUV, image.planes[0]
        // contains the Y (luminance) plane
        //val buffer = image.planes[0].buffer
        // Extract image data from callback object
        //val data = buffer.toByteArray()
        // Convert the data into an array of pixel values
        //val pixels = data.map { it.toInt() and 0xFF }
        // Compute average luminance for the image
        //val luma = pixels.average()
        // Log the new luma value
        val duration = currentTimestamp - lastAnalyzedTimestamp
        //Log.d("CameraXApp", "Average luminosity: $luma")
        Log.d("CameraXApp", "Frame Size: (${image.width} x ${image.height}) at ${duration}ms (${1000 / duration}fps)")
        // Update timestamp of last analyzed frame
        lastAnalyzedTimestamp = currentTimestamp
    }
}