package com.example.workmonitoring

import android.app.Application
import org.opencv.android.OpenCVLoader

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initDebug()) {
            throw RuntimeException("Ошибка инициализации OpenCV!")
        }
    }
}
