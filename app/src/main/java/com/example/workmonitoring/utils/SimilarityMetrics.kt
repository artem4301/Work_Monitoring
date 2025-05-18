package com.example.workmonitoring.utils

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility class for calculating similarity metrics between face embeddings.
 * Handles both normalized and non-normalized input vectors.
 */
object SimilarityMetrics {
    
    /**
     * Cosine similarity for any vectors.
     * Range: [0%, 100%], where 100% means identical vectors.
     * Formula: cos(θ) = (A·B)/(||A||·||B||)
     */
    fun cosineSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        val dotProduct = embedding1.zip(embedding2).sumOf { (a, b) -> (a * b).toDouble() }
        val norm1 = sqrt(embedding1.sumOf { it.toDouble().pow(2) })
        val norm2 = sqrt(embedding2.sumOf { it.toDouble().pow(2) })
        return ((dotProduct / (norm1 * norm2)).coerceIn(-1.0, 1.0) * 100).toFloat()
    }

    /**
     * Euclidean similarity for any vectors.
     * Range: [0%, 100%], where 100% means identical vectors.
     * Formula: similarity = 100 * (1 - d/max_d), where d is Euclidean distance
     */
    fun euclideanSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        val distance = sqrt(embedding1.zip(embedding2).sumOf { (a, b) -> (a - b).toDouble().pow(2) })
        val maxDistance = sqrt(embedding1.size.toDouble()) * 2 // Maximum possible distance
        return ((1.0 - (distance / maxDistance)).coerceIn(0.0, 1.0) * 100).toFloat()
    }

    enum class SimilarityMetric {
        COSINE,   // Works with any vectors
        EUCLIDEAN // Works with any vectors
    }
} 