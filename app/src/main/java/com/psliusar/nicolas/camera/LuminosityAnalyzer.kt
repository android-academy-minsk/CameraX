package com.psliusar.nicolas.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

private val FRAME_THRESHOLD = TimeUnit.SECONDS.toMillis(1)

class LuminosityAnalyzer : ImageAnalysis.Analyzer {
    private val frameRateWindow = 8
    private val frameTimestamps = ArrayDeque<Long>(5)
    private val listeners = mutableListOf<LumaListener>()
    private var lastAnalyzedTimestamp = 0L
    var framesPerSecond: Double = -1.0
        private set

    /**
     * Used to add listeners that will be called with each luma computed
     */
    fun addListener(listener: LumaListener) = listeners.add(listener)

    /**
     * Helper extension function used to extract a byte array from an
     * image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind() // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data) // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {
        // If there are no listeners attached, we don't need to perform analysis
        if (listeners.isEmpty()) return

        // Keep track of frames analyzed
        frameTimestamps.push(System.currentTimeMillis())

        // Compute the FPS using a moving average
        while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
        framesPerSecond = 1.0 / ((frameTimestamps.peekFirst() -
            frameTimestamps.peekLast())  / frameTimestamps.size.toDouble()) * 1000.0

        // Calculate the average luma no more often than every second
        if (frameTimestamps.first - lastAnalyzedTimestamp >= FRAME_THRESHOLD) {
            // Since format in ImageAnalysis is YUV, image.planes[0] contains the Y (luminance) plane
            val buffer = image.planes[0].buffer
            // Extract image data from callback object
            val data = buffer.toByteArray()
            // Convert the data into an array of pixel values
            val pixels = data.map { it.toInt() and 0xFF }
            // Compute average luminance for the image
            val luma = pixels.average()

            // Call all listeners with new value
            listeners.forEach { it(luma) }

            // Update timestamp of last analyzed frame
            lastAnalyzedTimestamp = frameTimestamps.first
        }

        image.close()
    }
}