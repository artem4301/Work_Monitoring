package com.example.workmonitoring.utils

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc

object ImageQualityChecker {

    fun isImageBlurred(bitmap: Bitmap, threshold: Double = 100.0): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

        val laplacian = Mat()
        Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)

        val mean = Core.mean(laplacian).`val`[0]
        val reshaped = laplacian.reshape(1, 1)
        val dot = reshaped.dot(reshaped)
        val variance = if (laplacian.total() > 0) {
            dot / laplacian.total() - mean * mean
        } else {
            0.0
        }

        return variance < threshold
    }

    fun isImageTooDark(bitmap: Bitmap, threshold: Int = 50): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var totalBrightness = 0L

        for (x in 0 until width step 10) { // ускорим за счёт пропуска
            for (y in 0 until height step 10) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3
                totalBrightness += brightness
            }
        }

        val sampleCount = (width / 10) * (height / 10)
        val avgBrightness = totalBrightness / sampleCount
        return avgBrightness < threshold
    }
}
