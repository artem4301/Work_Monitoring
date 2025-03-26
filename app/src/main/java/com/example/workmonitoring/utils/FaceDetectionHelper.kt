package com.example.workmonitoring.utils

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

object FaceDetectionHelper {

    fun detectFace(context: Context, bitmap: Bitmap, callback: (Bitmap?) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

        val detector = FaceDetection.getClient(options)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val boundingBox = face.boundingBox
                    val croppedFace = Bitmap.createBitmap(
                        bitmap,
                        boundingBox.left.coerceAtLeast(0),
                        boundingBox.top.coerceAtLeast(0),
                        boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                        boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
                    )
                    callback(croppedFace)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Ошибка распознавания лица: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }
}
