package com.ext.pdf_converter

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ExcelToPdfConverter {

    private const val PAGE_WIDTH = 842  // A4 landscape
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 30
    private const val ROW_HEIGHT = 25f
    private const val CELL_PADDING = 5f
    private const val MAX_COLUMNS = 10

    fun convert(context: Context, excelUri: Uri, outputFile: File) {
        val workbook = context.contentResolver.openInputStream(excelUri)?.use { inputStream ->
            WorkbookFactory.create(inputStream)
        } ?: throw Exception("Failed to read Excel file")

        val pdfDocument = PdfDocument()

        try {
            var pageNumber = 1

            // Process each sheet
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)

                if (sheet.physicalNumberOfRows == 0) continue

                // Calculate column widths based on content
                val columnWidths = calculateColumnWidths(sheet)
                val totalTableWidth = columnWidths.sum()
                val availableWidth = PAGE_WIDTH - 2 * MARGIN

                // Scale column widths if they exceed page width
                val scaleFactor = if (totalTableWidth > availableWidth) {
                    availableWidth / totalTableWidth
                } else {
                    1f
                }

                val scaledColumnWidths = columnWidths.map { it * scaleFactor }

                var currentY = MARGIN.toFloat()
                var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                // Draw sheet name
                val sheetNamePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("Sheet: ${sheet.sheetName}", MARGIN.toFloat(), currentY + 15, sheetNamePaint)
                currentY += 35f

                // Process rows
                var isFirstRow = true
                for (row in sheet) {
                    // Check if we need a new page
                    if (currentY + ROW_HEIGHT > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN.toFloat()

                        // Redraw header on new page if not the first row
                        if (!isFirstRow) {
                            val headerRow = sheet.getRow(0)
                            if (headerRow != null) {
                                drawTableRow(canvas, headerRow, scaledColumnWidths, currentY, true)
                                currentY += ROW_HEIGHT
                            }
                        }
                    }

                    // Draw the row
                    drawTableRow(canvas, row, scaledColumnWidths, currentY, isFirstRow)
                    currentY += ROW_HEIGHT
                    isFirstRow = false
                }

                pdfDocument.finishPage(page)

                // Start new page for next sheet
                if (sheetIndex < workbook.numberOfSheets - 1) {
                    pageNumber++
                }
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

        } finally {
            pdfDocument.close()
            workbook.close()
        }
    }

    private fun drawTableRow(
        canvas: android.graphics.Canvas,
        row: Row,
        columnWidths: List<Float>,
        y: Float,
        isHeader: Boolean
    ) {
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = if (isHeader) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val backgroundPaint = Paint().apply {
            color = if (isHeader) Color.LTGRAY else Color.WHITE
            style = Paint.Style.FILL
        }

        var currentX = MARGIN.toFloat()

        // Draw cells
        val maxColumns = min(columnWidths.size, MAX_COLUMNS)
        for (colIndex in 0 until maxColumns) {
            val cellWidth = columnWidths[colIndex]

            // Draw cell background
            canvas.drawRect(
                currentX,
                y,
                currentX + cellWidth,
                y + ROW_HEIGHT,
                backgroundPaint
            )

            // Draw cell border
            canvas.drawRect(
                currentX,
                y,
                currentX + cellWidth,
                y + ROW_HEIGHT,
                borderPaint
            )

            // Get cell value
            val cell = row.getCell(colIndex)
            val cellValue = if (cell != null) {
                when (cell.cellType) {
                    CellType.STRING -> cell.stringCellValue
                    CellType.NUMERIC -> {
                        // Format numeric values
                        val numValue = cell.numericCellValue
                        if (numValue % 1.0 == 0.0) {
                            numValue.toLong().toString()
                        } else {
                            String.format("%.2f", numValue)
                        }
                    }
                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                    CellType.FORMULA -> {
                        try {
                            cell.numericCellValue.toString()
                        } catch (e: Exception) {
                            try {
                                cell.stringCellValue
                            } catch (e: Exception) {
                                cell.cellFormula
                            }
                        }
                    }
                    else -> ""
                }
            } else {
                ""
            }

            // Truncate text if too long
            val maxChars = ((cellWidth - 2 * CELL_PADDING) / (textPaint.textSize * 0.6)).toInt()
            val displayText = if (cellValue.length > maxChars) {
                cellValue.substring(0, maxChars - 3) + "..."
            } else {
                cellValue
            }

            // Draw text centered vertically in cell
            val textY = y + ROW_HEIGHT / 2 + textPaint.textSize / 3
            canvas.drawText(
                displayText,
                currentX + CELL_PADDING,
                textY,
                textPaint
            )

            currentX += cellWidth
        }
    }

    private fun calculateColumnWidths(sheet: org.apache.poi.ss.usermodel.Sheet): List<Float> {
        val maxColumns = min(getMaxColumns(sheet), MAX_COLUMNS)
        val columnWidths = MutableList(maxColumns) { 50f } // Minimum width

        val paint = Paint().apply {
            textSize = 9f
        }

        // Calculate based on content in first few rows
        val rowsToCheck = min(sheet.physicalNumberOfRows, 20)
        for (rowIndex in 0 until rowsToCheck) {
            val row = sheet.getRow(rowIndex) ?: continue

            for (colIndex in 0 until maxColumns) {
                val cell = row.getCell(colIndex) ?: continue

                val cellValue = when (cell.cellType) {
                    CellType.STRING -> cell.stringCellValue
                    CellType.NUMERIC -> cell.numericCellValue.toString()
                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                    CellType.FORMULA -> cell.cellFormula
                    else -> ""
                }

                val textWidth = paint.measureText(cellValue) + 2 * CELL_PADDING + 10
                if (textWidth > columnWidths[colIndex]) {
                    columnWidths[colIndex] = min(textWidth, 150f) // Max width cap
                }
            }
        }

        return columnWidths
    }

    private fun getMaxColumns(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        var maxCols = 0
        for (row in sheet) {
            val lastCell = row.lastCellNum.toInt()
            if (lastCell > maxCols) {
                maxCols = lastCell
            }
        }
        return maxCols
    }
}