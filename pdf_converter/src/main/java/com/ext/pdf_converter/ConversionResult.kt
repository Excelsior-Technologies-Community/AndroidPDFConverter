package com.ext.pdf_converter

import java.io.File

data class ConversionResult(
    val success: Boolean,
    val outputFile: File,
    val message: String
)