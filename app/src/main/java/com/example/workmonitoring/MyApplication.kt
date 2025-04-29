package com.example.workmonitoring

import android.app.Application
import com.yandex.mapkit.MapKitFactory
import org.opencv.android.OpenCVLoader

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey("4659ffc0-0584-4295-9bb3-a57a4321726e")
        if (!OpenCVLoader.initDebug()) {
            throw RuntimeException("Ошибка инициализации OpenCV!")
        }
    }
}
