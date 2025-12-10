package com.ext.pdf_converter

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfConverter private constructor() {

    companion object {
        @Volatile
        private var instance: PdfConverter? = null

        fun getInstance(): PdfConverter {
            return instance ?: synchronized(this) {
                instance ?: PdfConverter().also { instance = it }
            }
        }
    }

    interface ConversionCallback {
        fun onSuccess(result: ConversionResult)
        fun onError(error: String)
    }

    /**
     * Convert any supported file to PDF
     */
    fun convertToPdf(
        context: Context,
        fileUri: Uri,
        outputFileName: String,
        callback: ConversionCallback
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mimeType = getMimeType(context, fileUri)
                val outputFile = createOutputFile(context, outputFileName)

                when {
                    mimeType?.startsWith("image/") == true -> {
                        ImageToPdfConverter.convert(context, listOf(fileUri), outputFile)
                    }

                    mimeType == "text/plain" -> {
                        TextToPdfConverter.convert(context, fileUri, outputFile)
                    }

                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                            mimeType == "application/msword" -> {
                        WordToPdfConverter.convert(context, fileUri, outputFile)
                    }

                    mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                            mimeType == "application/vnd.ms-excel" -> {
                        ExcelToPdfConverter.convert(context, fileUri, outputFile)
                    }

                    mimeType == "application/pdf" -> {
                        // Already a PDF, just copy it
                        copyFile(context, fileUri, outputFile)
                    }

                    else -> {
                        // Try to convert as text
                        TextToPdfConverter.convert(context, fileUri, outputFile)
                    }
                }

                val result = ConversionResult(
                    success = true,
                    outputFile = outputFile,
                    message = "Conversion successful"
                )

                callback.onSuccess(result)

            } catch (e: Exception) {
                callback.onError(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Convert multiple images to a single PDF
     */
    fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFileName: String,
        callback: ConversionCallback
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outputFile = createOutputFile(context, outputFileName)
                ImageToPdfConverter.convert(context, imageUris, outputFile)

                val result = ConversionResult(
                    success = true,
                    outputFile = outputFile,
                    message = "Converted ${imageUris.size} images to PDF"
                )

                callback.onSuccess(result)

            } catch (e: Exception) {
                callback.onError(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Open the generated PDF file
     */
    fun openPdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(Intent.createChooser(intent, "Open PDF with"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createOutputFile(context: Context, fileName: String): File {
        val pdfDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "PDFs"
        )
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }

        val finalFileName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
        return File(pdfDir, finalFileName)
    }

    private fun getMimeType(context: Context, uri: Uri): String? {
        return if (uri.scheme == "content") {
            context.contentResolver.getType(uri)
        } else {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }
    }

    private fun copyFile(context: Context, sourceUri: Uri, destFile: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}