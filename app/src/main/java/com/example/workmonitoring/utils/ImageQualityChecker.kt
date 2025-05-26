package com.example.workmonitoring.utils

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Утилита для проверки качества изображения согласно ТЗ.
 * Проверяет:
 * - Освещенность кадра
 * - Резкость (размытие)
 * - Контрастность
 * - Общее качество
 */
object ImageQualityChecker {

    data class QualityResult(
        val isGoodQuality: Boolean,
        val brightness: Double,
        val contrast: Double,
        val sharpness: Double,
        val overallScore: Double,
        val issues: List<String>
    )

    /**
     * Комплексная проверка качества изображения
     */
    fun checkImageQuality(bitmap: Bitmap): QualityResult {
        val brightness = calculateBrightness(bitmap)
        val contrast = calculateContrast(bitmap)
        val sharpness = calculateSharpness(bitmap)
        val issues = mutableListOf<String>()
        
        // Проверка освещенности
        when {
            brightness < 50 -> issues.add("Изображение слишком темное")
            brightness > 200 -> issues.add("Изображение слишком яркое (переэкспонировано)")
        }
        
        // Проверка контрастности
        if (contrast < 30) {
            issues.add("Низкий контраст изображения")
        }
        
        // Проверка резкости
        if (sharpness < 15) {
            issues.add("Изображение размыто")
        }
        
        // Расчет общего балла качества (0-100)
        val brightnessScore = when {
            brightness in 80.0..180.0 -> 100.0
            brightness in 60.0..200.0 -> 80.0
            brightness in 40.0..220.0 -> 60.0
            else -> 20.0
        }
        
        val contrastScore = when {
            contrast >= 50 -> 100.0
            contrast >= 30 -> 80.0
            contrast >= 20 -> 60.0
            else -> 20.0
        }
        
        val sharpnessScore = when {
            sharpness >= 25 -> 100.0
            sharpness >= 15 -> 80.0
            sharpness >= 10 -> 60.0
            else -> 20.0
        }
        
        val overallScore = (brightnessScore * 0.3 + contrastScore * 0.3 + sharpnessScore * 0.4)
        val isGoodQuality = overallScore >= 70 && issues.isEmpty()
        
        return QualityResult(
            isGoodQuality = isGoodQuality,
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            overallScore = overallScore,
            issues = issues
        )
    }

    /**
     * Расчет яркости изображения
     */
    private fun calculateBrightness(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        var totalBrightness = 0.0
        var sampleCount = 0

        // Сэмплируем каждый 5-й пиксель для производительности
        for (x in 0 until width step 5) {
            for (y in 0 until height step 5) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Используем стандартную формулу яркости
                val brightness = 0.299 * r + 0.587 * g + 0.114 * b
                totalBrightness += brightness
                sampleCount++
            }
        }

        return totalBrightness / sampleCount
    }

    /**
     * Расчет контрастности изображения
     */
    private fun calculateContrast(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val brightnesses = mutableListOf<Double>()

        // Собираем значения яркости
        for (x in 0 until width step 8) {
            for (y in 0 until height step 8) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = 0.299 * r + 0.587 * g + 0.114 * b
                brightnesses.add(brightness)
            }
        }

        if (brightnesses.isEmpty()) return 0.0

        // Рассчитываем стандартное отклонение как меру контрастности
        val mean = brightnesses.average()
        val variance = brightnesses.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    /**
     * Расчет резкости изображения с использованием OpenCV
     */
    private fun calculateSharpness(bitmap: Bitmap): Double {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

            val laplacian = Mat()
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)

            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stdDev)

            stdDev.toArray().firstOrNull() ?: 0.0
        } catch (e: Exception) {
            // Fallback метод без OpenCV
            calculateSharpnessFallback(bitmap)
        }
    }

    /**
     * Fallback метод расчета резкости без OpenCV
     */
    private fun calculateSharpnessFallback(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        var totalVariance = 0.0
        var count = 0

        for (x in 1 until width - 1 step 4) {
            for (y in 1 until height - 1 step 4) {
                val center = getGrayValue(bitmap.getPixel(x, y))
                val left = getGrayValue(bitmap.getPixel(x - 1, y))
                val right = getGrayValue(bitmap.getPixel(x + 1, y))
                val top = getGrayValue(bitmap.getPixel(x, y - 1))
                val bottom = getGrayValue(bitmap.getPixel(x, y + 1))

                val variance = ((center - left) * (center - left) +
                        (center - right) * (center - right) +
                        (center - top) * (center - top) +
                        (center - bottom) * (center - bottom)) / 4.0

                totalVariance += variance
                count++
            }
        }

        return if (count > 0) sqrt(totalVariance / count) else 0.0
    }

    private fun getGrayValue(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    /**
     * Проверяет, слишком ли темное изображение
     */
    fun isImageTooDark(bitmap: Bitmap): Boolean {
        val brightness = calculateBrightness(bitmap)
        return brightness < 50
    }

    /**
     * Проверяет, размыто ли изображение
     */
    fun isImageBlurred(bitmap: Bitmap): Boolean {
        val sharpness = calculateSharpness(bitmap)
        return sharpness < 15
    }

    /**
     * Проверяет, слишком ли яркое изображение
     */
    fun isImageTooBright(bitmap: Bitmap): Boolean {
        val brightness = calculateBrightness(bitmap)
        return brightness > 200
    }

    /**
     * Проверяет, низкий ли контраст изображения
     */
    fun isImageLowContrast(bitmap: Bitmap): Boolean {
        val contrast = calculateContrast(bitmap)
        return contrast < 30
    }
}

