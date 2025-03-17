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
    private lateinit var imageView: ImageView
    private var roppedFaceBitmap: Bitmap? = null

    private val getImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                homeViewModel.processImage(this, bitmap) { message ->
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
        homeViewModel = ViewModelProvider(this, factory).get(HomeViewModel::class.java)

        imageView = findViewById(R.id.imageView)
        val btnSelectImage = findViewById<Button>(R.id.btnSelectImage)
        val btnSaveEmbeddings = findViewById<Button>(R.id.btnSaveEmbeddings)
        val btnOnWork = findViewById<Button>(R.id.btnOnWork)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        homeViewModel.croppedFaceBitmap.observe(this) { bitmap ->
            imageView.setImageBitmap(bitmap)
        }

        btnSelectImage.setOnClickListener { getImageLauncher.launch("image/*") }

        btnSaveEmbeddings.setOnClickListener {
            homeViewModel.saveEmbeddings { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        btnOnWork.setOnClickListener {
            homeViewModel.checkUserEmbeddings({
                startActivity(Intent(this, FaceControlActivity::class.java))
            }, { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            })
        }

        logoutButton.setOnClickListener {
            homeViewModel.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

