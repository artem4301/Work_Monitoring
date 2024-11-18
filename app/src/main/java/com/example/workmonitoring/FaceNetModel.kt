package com.example.facedetection

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceNetModel(private val assetManager: AssetManager) {
    private val interpreter: Interpreter
    private val inputSize = 160

    init {
        // Конвертация модели из ByteArray в ByteBuffer
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

    fun getFaceEmbeddings(bitmap: Bitmap): FloatArray? {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = preprocessBitmap(resizedBitmap)

        val output = Array(1) { FloatArray(128) }
        interpreter.run(input, output)

        return output[0]
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
}
