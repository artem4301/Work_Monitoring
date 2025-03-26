package com.example.workmonitoring.utils

import kotlin.math.sqrt

object MathUtils {
    fun calculateCosineDistance(embedding1: List<Double>, embedding2: List<Double>): Double {
        if (embedding1.size != embedding2.size) return Double.MAX_VALUE

        val dotProduct = embedding1.zip(embedding2).sumOf { (a, b) -> a * b }
        val norm1 = sqrt(embedding1.sumOf { it * it })
        val norm2 = sqrt(embedding2.sumOf { it * it })
        val cosineSimilarity = dotProduct / (norm1 * norm2)
        return 1.0 - cosineSimilarity
    }
}
