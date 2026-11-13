package com.example.model

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import kotlinx.serialization.Serializable

@Serializable
enum class BarcodeFormatType(
    val labelFr: String,
    val zxingFormat: BarcodeFormat,
    val is2D: Boolean,
    val hintFr: String
) {
    EAN_13(
        labelFr = "EAN-13",
        zxingFormat = BarcodeFormat.EAN_13,
        is2D = false,
        hintFr = "13 chiffres (ex. 3268840001046)"
    ),
    EAN_8(
        labelFr = "EAN-8",
        zxingFormat = BarcodeFormat.EAN_8,
        is2D = false,
        hintFr = "8 chiffres (ex. 96385074)"
    ),
    CODE_128(
        labelFr = "Code 128",
        zxingFormat = BarcodeFormat.CODE_128,
        is2D = false,
        hintFr = "Lettres et chiffres"
    ),
    CODE_39(
        labelFr = "Code 39",
        zxingFormat = BarcodeFormat.CODE_39,
        is2D = false,
        hintFr = "Majuscules et chiffres"
    ),
    UPC_A(
        labelFr = "UPC-A",
        zxingFormat = BarcodeFormat.UPC_A,
        is2D = false,
        hintFr = "12 chiffres"
    ),
    ITF(
        labelFr = "ITF",
        zxingFormat = BarcodeFormat.ITF,
        is2D = false,
        hintFr = "Nombre pair de chiffres"
    ),
    QR_CODE(
        labelFr = "QR Code",
        zxingFormat = BarcodeFormat.QR_CODE,
        is2D = true,
        hintFr = "Texte, numéro ou identifiant"
    ),
    DATA_MATRIX(
        labelFr = "Data Matrix",
        zxingFormat = BarcodeFormat.DATA_MATRIX,
        is2D = true,
        hintFr = "Code compact 2D"
    ),
    AZTEC(
        labelFr = "Aztec",
        zxingFormat = BarcodeFormat.AZTEC,
        is2D = true,
        hintFr = "Code carré 2D"
    ),
    PDF_417(
        labelFr = "PDF417",
        zxingFormat = BarcodeFormat.PDF_417,
        is2D = false,
        hintFr = "Code-barres empilé"
    );

    companion object {
        fun fromMlKitFormat(mlKitFormat: Int): BarcodeFormatType = when (mlKitFormat) {
            Barcode.FORMAT_EAN_13 -> EAN_13
            Barcode.FORMAT_EAN_8 -> EAN_8
            Barcode.FORMAT_CODE_128 -> CODE_128
            Barcode.FORMAT_CODE_39 -> CODE_39
            Barcode.FORMAT_UPC_A -> UPC_A
            Barcode.FORMAT_ITF -> ITF
            Barcode.FORMAT_QR_CODE -> QR_CODE
            Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
            Barcode.FORMAT_AZTEC -> AZTEC
            Barcode.FORMAT_PDF417 -> PDF_417
            else -> CODE_128
        }
    }
}
