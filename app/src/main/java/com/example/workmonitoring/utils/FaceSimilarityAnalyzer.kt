package com.example.workmonitoring.utils

import android.util.Log

/**
 * Utility class for analyzing face similarity using cosine similarity.
 * Handles both normalized and non-normalized input vectors.
 */
class FaceSimilarityAnalyzer {
    companion object {
        private const val TAG = "FaceSimilarityAnalyzer"
        const val DEFAULT_THRESHOLD = 75f // Default threshold for face matching
    }

    /**
     * Result of face similarity analysis containing individual metric scores
     * and match decision.
     */
    data class SimilarityResult(
        val cosine: Float,    // Cosine similarity [0%, 100%]
        val euclidean: Float, // Euclidean similarity [0%, 100%]
        val isMatch: Boolean  // Whether the faces match based on threshold
    )

    /**
     * Analyzes similarity between two face embeddings using cosine similarity.
     * Returns detailed similarity scores and match decision.
     */
    fun analyzeSimilarity(
        embedding1: List<Float>,
        embedding2: List<Float>,
        threshold: Float = DEFAULT_THRESHOLD
    ): SimilarityResult {
        val cosine = SimilarityMetrics.cosineSimilarity(embedding1, embedding2)
        val euclidean = SimilarityMetrics.euclideanSimilarity(embedding1, embedding2)
        val isMatch = cosine >= threshold

        // Log results for analysis
        Log.d(TAG, """
            Face similarity analysis:
            Cosine: $cosine%
            Euclidean: $euclidean%
            Threshold: $threshold%
            Result: ${if (isMatch) "MATCH" else "NO MATCH"}
        """.trimIndent())

        return SimilarityResult(
            cosine = cosine,
            euclidean = euclidean,
            isMatch = isMatch
        )
    }

    /**
     * Finds the best matching face embedding from a list of candidates.
     * Returns the best match result and its index if similarity is above threshold.
     */
    fun findBestMatch(
        targetEmbedding: List<Float>,
        candidateEmbeddings: List<List<Float>>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Pair<SimilarityResult, Int>? {
        if (candidateEmbeddings.isEmpty()) return null

        var bestMatch: SimilarityResult? = null
        var bestMatchIndex = -1
        var maxCosine = -1f

        candidateEmbeddings.forEachIndexed { index, candidate ->
            val result = analyzeSimilarity(targetEmbedding, candidate, threshold)
            if (result.cosine > maxCosine) {
                maxCosine = result.cosine
                bestMatch = result
                bestMatchIndex = index
            }
        }

        return bestMatch?.let { result ->
            if (result.isMatch) {
                Pair(result, bestMatchIndex)
            } else null
        }
    }

    /**
     * Evaluates recognition accuracy on test data.
     */
    fun evaluateAccuracy(
        positiveTests: List<Pair<List<Float>, List<Float>>>, // pairs of same person
        negativeTests: List<Pair<List<Float>, List<Float>>>, // pairs of different people
        threshold: Float = DEFAULT_THRESHOLD
    ): AccuracyMetrics {
        var truePositives = 0
        var falsePositives = 0
        var trueNegatives = 0
        var falseNegatives = 0

        // Check positive tests (should match)
        positiveTests.forEach { (emb1, emb2) ->
            val result = analyzeSimilarity(emb1, emb2, threshold)
            if (result.isMatch) truePositives++ else falseNegatives++
        }

        // Check negative tests (should not match)
        negativeTests.forEach { (emb1, emb2) ->
            val result = analyzeSimilarity(emb1, emb2, threshold)
            if (result.isMatch) falsePositives++ else trueNegatives++
        }

        return AccuracyMetrics(
            accuracy = (truePositives + trueNegatives).toFloat() / (positiveTests.size + negativeTests.size),
            precision = truePositives.toFloat() / (truePositives + falsePositives),
            recall = truePositives.toFloat() / (truePositives + falseNegatives),
            falsePositiveRate = falsePositives.toFloat() / (falsePositives + trueNegatives)
        )
    }

    data class AccuracyMetrics(
        val accuracy: Float,  // overall accuracy
        val precision: Float, // precision of positive predictions
        val recall: Float,    // recall
        val falsePositiveRate: Float // false positive rate
    ) {
        fun toDetailedString(): String {
            return """
                Accuracy metrics:
                Overall accuracy: ${String.format("%.2f", accuracy * 100)}%
                Precision: ${String.format("%.2f", precision * 100)}%
                Recall: ${String.format("%.2f", recall * 100)}%
                False positive rate: ${String.format("%.2f", falsePositiveRate * 100)}%
            """.trimIndent()
        }
    }
} 