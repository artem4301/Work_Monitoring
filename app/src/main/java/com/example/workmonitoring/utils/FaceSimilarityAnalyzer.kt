package com.example.workmonitoring.utils

/**
 * Анализатор сходства лиц - обертка над SimilarityMetrics для удобства использования
 */
class FaceSimilarityAnalyzer {

    data class SimilarityResult(
        val cosine: Float,
        val euclidean: Float,
        val manhattan: Float,
        val pearson: Float,
        val jaccard: Float,
        val bestScore: Float,
        val bestMetric: String,
        val isMatch: Boolean
    )

    /**
     * Анализирует сходство между двумя эмбеддингами лиц
     */
    fun analyzeSimilarity(
        embedding1: List<Float>,
        embedding2: List<Float>,
        threshold: Float = 0.75f
    ): SimilarityResult {
        
        val cosine = SimilarityMetrics.calculateSimilarity(
            embedding1, embedding2, SimilarityMetrics.SimilarityMetric.COSINE
        )
        
        val euclidean = SimilarityMetrics.calculateSimilarity(
            embedding1, embedding2, SimilarityMetrics.SimilarityMetric.EUCLIDEAN
        )
        
        val manhattan = SimilarityMetrics.calculateSimilarity(
            embedding1, embedding2, SimilarityMetrics.SimilarityMetric.MANHATTAN
        )
        
        val pearson = SimilarityMetrics.calculateSimilarity(
            embedding1, embedding2, SimilarityMetrics.SimilarityMetric.PEARSON
        )
        
        val jaccard = SimilarityMetrics.calculateSimilarity(
            embedding1, embedding2, SimilarityMetrics.SimilarityMetric.JACCARD
        )

        // Определяем лучший результат (теперь только по косинусной метрике)
        val metrics = mapOf(
            "Косинусная" to cosine,
            "Евклидова" to euclidean,
            "Манхэттенская" to manhattan,
            "Пирсона" to pearson,
            "Жаккара" to jaccard
        )
        
        // Верификация основывается только на косинусной метрике
        val bestScore = cosine
        val bestMetric = "Косинусная"
        val isMatch = cosine >= threshold

        return SimilarityResult(
            cosine = cosine,
            euclidean = euclidean,
            manhattan = manhattan,
            pearson = pearson,
            jaccard = jaccard,
            bestScore = bestScore,
            bestMetric = bestMetric,
            isMatch = isMatch
        )
    }

    /**
     * Анализирует сходство с множественными сохраненными эмбеддингами
     */
    fun analyzeMultipleSimilarity(
        currentEmbedding: List<Float>,
        storedEmbeddings: List<List<Float>>,
        threshold: Float = 0.75f
    ): SimilarityResult {
        
        var bestResult: SimilarityResult? = null
        
        storedEmbeddings.forEach { stored ->
            val result = analyzeSimilarity(currentEmbedding, stored, threshold)
            if (bestResult == null || result.bestScore > bestResult!!.bestScore) {
                bestResult = result
            }
        }
        
        return bestResult ?: SimilarityResult(
            cosine = 0f,
            euclidean = 0f,
            manhattan = 0f,
            pearson = 0f,
            jaccard = 0f,
            bestScore = 0f,
            bestMetric = "Нет данных",
            isMatch = false
        )
    }
} 