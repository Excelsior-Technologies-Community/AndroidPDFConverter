package com.ext.pdf_converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.File
import java.io.FileOutputStream

object WordToPdfConverter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 50
    private const val LINE_HEIGHT = 20
    private const val MAX_IMAGE_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val MAX_IMAGE_HEIGHT = 400

    sealed class ContentItem {
        data class Text(val text: String) : ContentItem()
        data class Image(val bitmap: Bitmap) : ContentItem()
    }

    fun convert(context: Context, wordUri: Uri, outputFile: File) {
        val contentItems = extractContentFromWord(context, wordUri)

        val pdfDocument = PdfDocument()

        try {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            var pageNumber = 1
            var currentY = MARGIN.toFloat()
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            for (item in contentItems) {
                when (item) {
                    is ContentItem.Text -> {
                        val lines = item.text.split("\n")
                        for (line in lines) {
                            if (currentY + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                                pdfDocument.finishPage(page)
                                pageNumber++
                                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                                page = pdfDocument.startPage(pageInfo)
                                canvas = page.canvas
                                currentY = MARGIN.toFloat()
                            }

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
                    }

                    is ContentItem.Image -> {
                        val bitmap = item.bitmap
                        val scaledSize = calculateScaledSize(bitmap.width, bitmap.height)

                        // Check if image fits on current page
                        if (currentY + scaledSize.height > PAGE_HEIGHT - MARGIN) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = MARGIN.toFloat()
                        }

                        // Draw the image
                        val left = MARGIN.toFloat()
                        val top = currentY
                        val right = left + scaledSize.width
                        val bottom = top + scaledSize.height

                        canvas.drawBitmap(
                            bitmap,
                            null,
                            Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()),
                            null
                        )

                        currentY += scaledSize.height + LINE_HEIGHT // Add spacing after image
                    }
                }
            }

            pdfDocument.finishPage(page)

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

        } finally {
            pdfDocument.close()
        }
    }

    private fun extractContentFromWord(context: Context, uri: Uri): List<ContentItem> {
        // Try DOCX first
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = XWPFDocument(inputStream)
                val contentItems = mutableListOf<ContentItem>()

                // Extract paragraphs and images
                for (paragraph in document.paragraphs) {
                    // Extract text
                    val text = paragraph.text
                    if (text.isNotEmpty()) {
                        contentItems.add(ContentItem.Text(text))
                    }

                    // Extract images from runs
                    for (run in paragraph.runs) {
                        val pictures = run.embeddedPictures
                        for (picture in pictures) {
                            try {
                                val pictureData = picture.pictureData
                                val bitmap = BitmapFactory.decodeByteArray(
                                    pictureData.data,
                                    0,
                                    pictureData.data.size
                                )
                                if (bitmap != null) {
                                    contentItems.add(ContentItem.Image(bitmap))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                document.close()
                return contentItems
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // DOCX failed, try DOC format
        }

        // Try DOC format
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val contentItems = mutableListOf<ContentItem>()
                val document = HWPFDocument(inputStream)

                // Extract text
                val extractor = WordExtractor(document)
                val text = extractor.text
                if (text.isNotEmpty()) {
                    contentItems.add(ContentItem.Text(text))
                }

                // Extract images from DOC
                try {
                    val pictures = document.picturesTable.allPictures
                    for (picture in pictures) {
                        try {
                            val bitmap = BitmapFactory.decodeByteArray(
                                picture.content,
                                0,
                                picture.content.size
                            )
                            if (bitmap != null) {
                                contentItems.add(ContentItem.Image(bitmap))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                extractor.close()
                document.close()
                return contentItems
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(ContentItem.Text("Failed to extract content from Word document: ${e.message}"))
        }

        return listOf(ContentItem.Text("Unable to open file"))
    }

    private fun calculateScaledSize(width: Int, height: Int): Size {
        var scaledWidth = width.toFloat()
        var scaledHeight = height.toFloat()

        // Scale down if too wide
        if (scaledWidth > MAX_IMAGE_WIDTH) {
            val ratio = MAX_IMAGE_WIDTH / scaledWidth
            scaledWidth = MAX_IMAGE_WIDTH.toFloat()
            scaledHeight *= ratio
        }

        // Scale down if too tall
        if (scaledHeight > MAX_IMAGE_HEIGHT) {
            val ratio = MAX_IMAGE_HEIGHT / scaledHeight
            scaledHeight = MAX_IMAGE_HEIGHT.toFloat()
            scaledWidth *= ratio
        }

        return Size(scaledWidth.toInt(), scaledHeight.toInt())
    }

    private data class Size(val width: Int, val height: Int)

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        if (text.isEmpty()) return listOf("")

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