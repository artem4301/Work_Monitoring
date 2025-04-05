package com.example.workmonitoring.utils

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

object ImageQualityChecker {

    // Проверка на размытие
    fun isImageBlurred(bitmap: Bitmap, threshold: Double = 10.0): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

        val laplacian = Mat()
        Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)

        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        Core.meanStdDev(laplacian, mean, stdDev)

        val stdDevValue = stdDev.toArray().firstOrNull() ?: 0.0
        return stdDevValue < threshold
    }



    // Проверка на темноту изображения
    fun isImageTooDark(bitmap: Bitmap, threshold: Int = 80): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var totalBrightness = 0.0
        var sampleCount = 0

        for (x in 0 until width step 10) {
            for (y in 0 until height step 10) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = 0.299 * r + 0.587 * g + 0.114 * b
                totalBrightness += brightness
                sampleCount++
            }
        }

        val avgBrightness = totalBrightness / sampleCount
        return avgBrightness < threshold
    }

}
