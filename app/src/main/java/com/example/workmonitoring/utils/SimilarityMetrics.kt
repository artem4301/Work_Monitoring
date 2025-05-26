package com.example.workmonitoring.utils

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility class for calculating similarity metrics between face embeddings.
 * Handles raw (non-normalized) FaceNet embeddings.
 * Implements metrics according to technical specification:
 * - Cosine similarity
 * - Euclidean distance
 * - Manhattan distance (L1)
 * - Pearson correlation coefficient
 * - Jaccard similarity
 */
object SimilarityMetrics {
    
    /**
     * Cosine similarity for raw FaceNet embeddings.
     * Range: [0%, 100%], where 100% means identical vectors.
     * Formula: cos(θ) = (A·B)/(||A||·||B||)
     */
    fun cosineSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        if (embedding1.size != embedding2.size) return 0f
        val dotProduct = embedding1.zip(embedding2).sumOf { (a, b) -> (a * b).toDouble() }
        val norm1 = sqrt(embedding1.sumOf { it.toDouble().pow(2) })
        val norm2 = sqrt(embedding2.sumOf { it.toDouble().pow(2) })
        return if (norm1 > 0 && norm2 > 0) {
            // Cosine similarity is in [-1, 1], convert to [0%, 100%]
            val cosineSim = (dotProduct / (norm1 * norm2)).coerceIn(-1.0, 1.0)
            ((cosineSim + 1.0) / 2.0 * 100).toFloat()
        } else 0f
    }

    /**
     * Euclidean similarity for raw FaceNet embeddings.
     * Range: [0%, 100%], where 100% means identical vectors.
     * For raw embeddings, we use adaptive normalization based on actual data range.
     */
    fun euclideanSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        if (embedding1.size != embedding2.size) return 0f
        val distance = sqrt(embedding1.zip(embedding2).sumOf { (a, b) -> (a - b).toDouble().pow(2) })
        
        // For raw FaceNet embeddings, use adaptive max distance based on vector magnitudes
        val maxMagnitude1 = sqrt(embedding1.sumOf { it.toDouble().pow(2) })
        val maxMagnitude2 = sqrt(embedding2.sumOf { it.toDouble().pow(2) })
        val maxDistance = maxMagnitude1 + maxMagnitude2 // Conservative estimate
        
        return if (maxDistance > 0) {
            ((1.0 - (distance / maxDistance)).coerceIn(0.0, 1.0) * 100).toFloat()
        } else 100f
    }

    /**
     * Manhattan distance similarity (L1 norm) for raw FaceNet embeddings.
     * Range: [0%, 100%], where 100% means identical vectors.
     */
    fun manhattanSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        if (embedding1.size != embedding2.size) return 0f
        val distance = embedding1.zip(embedding2).sumOf { (a, b) -> abs(a - b).toDouble() }
        
        // For raw embeddings, use adaptive max distance
        val maxL1_1 = embedding1.sumOf { abs(it).toDouble() }
        val maxL1_2 = embedding2.sumOf { abs(it).toDouble() }
        val maxDistance = maxL1_1 + maxL1_2
        
        return if (maxDistance > 0) {
            ((1.0 - (distance / maxDistance)).coerceIn(0.0, 1.0) * 100).toFloat()
        } else 100f
    }

    /**
     * Pearson correlation coefficient.
     * Range: [0%, 100%], where 100% means perfect positive correlation.
     * Formula: r = Σ((xi - x̄)(yi - ȳ)) / sqrt(Σ(xi - x̄)² * Σ(yi - ȳ)²)
     */
    fun pearsonSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        if (embedding1.size != embedding2.size || embedding1.size < 2) return 0f
        
        val mean1 = embedding1.average()
        val mean2 = embedding2.average()
        
        var numerator = 0.0
        var sumSq1 = 0.0
        var sumSq2 = 0.0
        
        for (i in embedding1.indices) {
            val diff1 = embedding1[i] - mean1
            val diff2 = embedding2[i] - mean2
            numerator += diff1 * diff2
            sumSq1 += diff1 * diff1
            sumSq2 += diff2 * diff2
        }
        
        val denominator = sqrt(sumSq1 * sumSq2)
        return if (denominator > 0) {
            // Pearson correlation is in [-1, 1], convert to [0%, 100%]
            val correlation = (numerator / denominator).coerceIn(-1.0, 1.0)
            ((correlation + 1.0) / 2.0 * 100).toFloat()
        } else 0f
    }

    /**
     * Modified Jaccard similarity for continuous vectors (raw embeddings).
     * Uses element-wise minimum ratio instead of traditional set intersection.
     * Range: [0%, 100%], where 100% means identical vectors.
     */
    fun jaccardSimilarity(embedding1: List<Float>, embedding2: List<Float>): Float {
        if (embedding1.size != embedding2.size) return 0f
        
        var similarity = 0.0
        var count = 0
        
        for (i in embedding1.indices) {
            val a = abs(embedding1[i])
            val b = abs(embedding2[i])
            if (a > 0 || b > 0) {
                val min = kotlin.math.min(a, b)
                val max = kotlin.math.max(a, b)
                similarity += min / max
                count++
            }
        }
        
        return if (count > 0) {
            ((similarity / count) * 100).toFloat()
        } else 0f
    }



    enum class SimilarityMetric {
        COSINE,     // Cosine similarity
        EUCLIDEAN,  // Euclidean distance
        MANHATTAN,  // Manhattan distance (L1)
        PEARSON,    // Pearson correlation
        JACCARD     // Jaccard similarity
    }

    /**
     * Calculate similarity using specified metric.
     */
    fun calculateSimilarity(
        embedding1: List<Float>, 
        embedding2: List<Float>, 
        metric: SimilarityMetric
    ): Float {
        return when (metric) {
            SimilarityMetric.COSINE -> cosineSimilarity(embedding1, embedding2)
            SimilarityMetric.EUCLIDEAN -> euclideanSimilarity(embedding1, embedding2)
            SimilarityMetric.MANHATTAN -> manhattanSimilarity(embedding1, embedding2)
            SimilarityMetric.PEARSON -> pearsonSimilarity(embedding1, embedding2)
            SimilarityMetric.JACCARD -> jaccardSimilarity(embedding1, embedding2)
        }
    }


} 