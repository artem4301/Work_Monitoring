package com.example.workmonitoring.face

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.example.workmonitoring.utils.MathUtils
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceNetModel(private val assetManager: AssetManager) {
    private val interpreter: Interpreter
    private val inputSize = 160

    init {
        val modelFile = loadModelFile("facenet.tflite")
        interpreter = Interpreter(modelFile)
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val assetInputStream = assetManager.open(fileName)
        val fileBytes = assetInputStream.readBytes()
        val byteBuffer = ByteBuffer.allocateDirect(fileBytes.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(fileBytes)
        return byteBuffer
    }

    fun getFaceEmbeddings(bitmap: Bitmap): Pair<FloatArray, FloatArray>? {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = preprocessBitmap(resizedBitmap)
        val output = Array(1) { FloatArray(128) }
        interpreter.run(input, output)
        val rawEmbedding = output[0]
        val l2Embedding = MathUtils.l2Normalize(rawEmbedding)
        return Pair(rawEmbedding, l2Embedding)
    }


    private fun preprocessBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val input = Array(1) {
            Array(inputSize) {
                Array(inputSize) {
                    FloatArray(3)
                }
            }
        }
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = bitmap.getPixel(x, y)
                input[0][y][x][0] = (pixel shr 16 and 0xFF) / 255.0f
                input[0][y][x][1] = (pixel shr 8 and 0xFF) / 255.0f
                input[0][y][x][2] = (pixel and 0xFF) / 255.0f
            }
        }
        return input
    }

    fun close() {
        interpreter.close()
    }
}
