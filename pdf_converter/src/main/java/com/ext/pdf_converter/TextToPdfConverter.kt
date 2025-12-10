package com.ext.pdf_converter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object TextToPdfConverter {

    private const val PAGE_WIDTH = 595  // A4 width in points (72 DPI)
    private const val PAGE_HEIGHT = 842  // A4 height in points
    private const val MARGIN = 50
    private const val LINE_HEIGHT = 20

    fun convert(context: Context, textUri: Uri, outputFile: File) {
        // Read text content
        val text = context.contentResolver.openInputStream(textUri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: throw Exception("Failed to read text file")

        val pdfDocument = PdfDocument()

        try {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val lines = text.split("\n")
            var pageNumber = 1
            var currentY = MARGIN.toFloat()
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            for (line in lines) {
                // Check if we need a new page
                if (currentY + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN.toFloat()
                }

                // Wrap long lines
                val wrappedLines = wrapText(line, paint, PAGE_WIDTH - 2 * MARGIN)
                for (wrappedLine in wrappedLines) {
                    if (currentY + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN.toFloat()
                    }

                    canvas.drawText(wrappedLine, MARGIN.toFloat(), currentY, paint)
                    currentY += LINE_HEIGHT
                }
            }

            pdfDocument.finishPage(page)

            // Write to file
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

        } finally {
            pdfDocument.close()
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)

            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = word
            } else {
                currentLine = testLine
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines.ifEmpty { listOf("") }
    }
}