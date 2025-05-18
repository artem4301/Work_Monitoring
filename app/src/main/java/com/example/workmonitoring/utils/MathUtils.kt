import kotlin.math.*

object MathUtils {
    fun calculateCosineDistance(embedding1: List<Double>, embedding2: List<Double>): Double {
        if (embedding1.size != embedding2.size) return Double.MAX_VALUE
        val dotProduct = embedding1.zip(embedding2).sumOf { (a, b) -> a * b }
        val norm1 = sqrt(embedding1.sumOf { it * it })
        val norm2 = sqrt(embedding2.sumOf { it * it })
        val cosineSimilarity = dotProduct / (norm1 * norm2)
        return 1.0 - cosineSimilarity
    }

    fun calculateEuclideanDistance(raw1: List<Double>, raw2: List<Double>): Double {
        if (raw1.size != raw2.size) return Double.MAX_VALUE
        return sqrt(raw1.zip(raw2).sumOf { (a, b) -> (a - b).pow(2) })
    }

    fun l2Normalize(embedding: List<Double>): List<Double> {
        val norm = sqrt(embedding.sumOf { it * it })
        return if (norm > 0) embedding.map { it / norm } else embedding
    }

    fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (v in embedding) {
            sum += v * v
        }
        val norm = kotlin.math.sqrt(sum)
        return if (norm > 0f) {
            FloatArray(embedding.size) { i -> embedding[i] / norm }
        } else {
            embedding
        }
    }

    fun calculateCosineSimilarity(emb1: List<Double>, emb2: List<Double>): Double {
        val dotProduct = emb1.zip(emb2).sumOf { (a, b) -> a * b }
        return (dotProduct.coerceIn(-1.0, 1.0) * 100).coerceIn(0.0, 100.0)
    }

    // Нормализованное евклидово сходство для сырых данных
    fun calculateNormalizedEuclidean(raw1: List<Double>, raw2: List<Double>, maxDistance: Double): Double {
        val distance = sqrt(raw1.zip(raw2).sumOf { (a, b) -> (a - b).pow(2) })
        return ((1 - (distance / maxDistance)).coerceIn(0.0, 1.0) * 100)
    }
}

