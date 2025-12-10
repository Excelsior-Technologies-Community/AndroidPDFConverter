package com.ext.pdf_converter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageToPdfConverter {

    fun convert(context: Context, imageUris: List<Uri>, outputFile: File) {
        val pdfDocument = PdfDocument()

        try {
            imageUris.forEachIndexed { index, uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    // Create page with bitmap dimensions
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        index + 1
                    ).create()

                    val page = pdfDocument.startPage(pageInfo)

                    // Draw bitmap on the page
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)

                    pdfDocument.finishPage(page)

                    // Recycle bitmap to free memory
                    bitmap.recycle()
                }
            }

            // Write the document to file
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

        } finally {
            pdfDocument.close()
        }
    }
}