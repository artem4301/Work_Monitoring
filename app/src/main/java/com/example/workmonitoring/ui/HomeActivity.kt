package com.example.workmonitoring.ui

import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.R
import com.example.workmonitoring.viewmodel.HomeViewModel
import com.example.workmonitoring.viewmodel.HomeViewModelFactory
import java.io.IOException

class HomeActivity : AppCompatActivity() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var facePreviewImage: ImageView

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri: Uri? ->
        imageUri?.let {
            try {
                val selectedBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                homeViewModel.processSelectedImage(selectedBitmap) { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val factory = HomeViewModelFactory(assets)
        homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        facePreviewImage = findViewById(R.id.facePreviewImage)
        val btnSelectFaceImage = findViewById<Button>(R.id.btnSelectFaceImage)
        val btnSaveFaceEmbeddings = findViewById<Button>(R.id.btnSaveFaceEmbeddings)
        val btnStartFaceControl = findViewById<Button>(R.id.btnStartFaceControl)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        homeViewModel.detectedFaceBitmap.observe(this) { detectedFaceBitmap ->
            facePreviewImage.setImageBitmap(detectedFaceBitmap)
        }

        btnSelectFaceImage.setOnClickListener { imagePickerLauncher.launch("image/*") }

        btnSaveFaceEmbeddings.setOnClickListener {
            homeViewModel.saveFaceEmbeddings { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        btnStartFaceControl.setOnClickListener {
            homeViewModel.checkStoredEmbeddings({
                startActivity(Intent(this, FaceControlActivity::class.java))
            }, { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            })
        }

        btnLogout.setOnClickListener {
            homeViewModel.logoutUser()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}


