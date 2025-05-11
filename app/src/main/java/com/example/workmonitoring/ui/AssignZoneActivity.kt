package com.example.workmonitoring.ui

import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CircleMapObject
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import java.util.Locale

class AssignZoneActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var placemark: PlacemarkMapObject? = null
    private var circleMapObject: CircleMapObject? = null

    private lateinit var editRadius: EditText
    private lateinit var btnSaveZone: Button

    private lateinit var mapTapListener: InputListener

    private var selectedPoint: Point? = null

    private lateinit var workerId: String
    private val firebaseRepository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.initialize(this)
        setContentView(R.layout.activity_assign_zone)

        mapView = findViewById(R.id.mapView)
        editRadius = findViewById(R.id.editRadius)
        btnSaveZone = findViewById(R.id.btnSaveZone)

        workerId = intent.getStringExtra("workerId") ?: ""

        moveCameraToMoscow()

        editRadius.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCircle()
            }
        })

        mapTapListener = object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                selectedPoint = point
                placeMarker(point)
                updateCircle()
            }

            override fun onMapLongTap(map: Map, point: Point) {}
        }
        mapView.map.addInputListener(mapTapListener)

        btnSaveZone.setOnClickListener {
            saveZone()
        }
    }

    private fun moveCameraToMoscow() {
        val moscowPoint = Point(55.751244, 37.618423)
        // Упрощенная версия без анимации
        mapView.map.move(
            CameraPosition(moscowPoint, 12.0f, 0.0f, 0.0f)
        )
    }

    private fun placeMarker(point: Point) {
        val mapObjects = mapView.map.mapObjects
        placemark?.let { mapObjects.remove(it) }
        placemark = mapObjects.addPlacemark(point)
        placemark?.setIcon(
            ImageProvider.fromResource(this, android.R.drawable.ic_menu_mylocation)
        )
    }

    private fun updateCircle() {
        val point = selectedPoint ?: return
        val radiusText = editRadius.text.toString()
        val radius = radiusText.toFloatOrNull() ?: return

        if (radius <= 0) return

        val circle = Circle(point, radius)
        val mapObjects = mapView.map.mapObjects

        circleMapObject?.let { mapObjects.remove(it) }
        circleMapObject = mapObjects.addCircle(circle).apply {
            fillColor = 0x5500FF00
            strokeColor = 0xFF00FF00.toInt()
            strokeWidth = 2f
        }
    }

    private fun saveZone() {
        val point = selectedPoint
        if (point == null) {
            Toast.makeText(this, "Сначала выберите точку на карте", Toast.LENGTH_SHORT).show()
            return
        }

        val radius = editRadius.text.toString().toFloatOrNull()
        if (radius == null || radius <= 0) {
            Toast.makeText(this, "Введите корректный радиус", Toast.LENGTH_SHORT).show()
            return
        }

        val geocoder = Geocoder(this, Locale.getDefault())
        var addressText = "Неизвестный адрес"
        try {
            val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressParts = mutableListOf<String>()

                // Собираем части адреса
                address.countryName?.takeIf { it.isNotEmpty() }?.let { addressParts.add(it) }
                address.locality?.takeIf { it.isNotEmpty() }?.let { addressParts.add(it) }
                address.thoroughfare?.takeIf { it.isNotEmpty() }?.let { addressParts.add(it) }
                address.subThoroughfare?.takeIf { it.isNotEmpty() }?.let { addressParts.add(it) }

                addressText = if (addressParts.isNotEmpty()) {
                    addressParts.joinToString(", ")
                } else {
                    "Неизвестный адрес"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        firebaseRepository.assignWorkZone(
            workerId,
            addressText,
            point.latitude,
            point.longitude,
            radius.toDouble()
        ) { success ->
            if (success) {
                Toast.makeText(this, "Зона сохранена!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Ошибка сохранения зоны", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}