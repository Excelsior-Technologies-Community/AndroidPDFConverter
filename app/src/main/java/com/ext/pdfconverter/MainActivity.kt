package com.ext.pdfconverter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ext.pdf_converter.ConversionResult
import com.ext.pdf_converter.PdfConverter

class MainActivity : AppCompatActivity() {

    private lateinit var pdfConverter: PdfConverter
    private lateinit var btnSelectFile: Button
    private lateinit var btnSelectMultipleImages: Button

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
//            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
//            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Single file picker
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            btnSelectFile.isEnabled = false
            btnSelectFile.text = "Converting..."

            pdfConverter.convertToPdf(
                context = this,
                fileUri = it,
                outputFileName = "converted_${System.currentTimeMillis()}",
                callback = object : PdfConverter.ConversionCallback {
                    override fun onSuccess(result: ConversionResult) {
                        runOnUiThread {
                            btnSelectFile.isEnabled = true
                            btnSelectFile.text = "Select File to Convert"

                            // Open the PDF
                            pdfConverter.openPdf(this@MainActivity, result.outputFile)
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            btnSelectFile.isEnabled = true
                            btnSelectFile.text = "Select File to Convert"
                        }
                    }
                }
            )
        }
    }

    // Multiple images picker
    private val multipleImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            btnSelectMultipleImages.isEnabled = false
            btnSelectMultipleImages.text = "Converting ${uris.size} images..."

            pdfConverter.convertImagesToPdf(
                context = this,
                imageUris = uris,
                outputFileName = "images_to_pdf_${System.currentTimeMillis()}",
                callback = object : PdfConverter.ConversionCallback {
                    override fun onSuccess(result: ConversionResult) {
                        runOnUiThread {
                            btnSelectMultipleImages.isEnabled = true
                            btnSelectMultipleImages.text = "Select Multiple Images"

                            // Open the PDF
                            pdfConverter.openPdf(this@MainActivity, result.outputFile)
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            btnSelectMultipleImages.isEnabled = true
                            btnSelectMultipleImages.text = "Select Multiple Images"
                        }
                    }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize PDF converter
        pdfConverter = PdfConverter.getInstance()

        // Initialize views
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnSelectMultipleImages = findViewById(R.id.btnSelectMultipleImages)

        // Request permissions
        checkAndRequestPermissions()

        // Set click listeners
        btnSelectFile.setOnClickListener {
            // Open file picker for any file type
            filePickerLauncher.launch("*/*")
        }

        btnSelectMultipleImages.setOnClickListener {
            // Open image picker for multiple images
            multipleImagesLauncher.launch("image/*")
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - no storage permission needed for own files
            return
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 - scoped storage
            return
        } else {
            // Android 10 and below
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}