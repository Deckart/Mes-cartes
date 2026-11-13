package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BarcodeFormatType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.BarcodeGenerator
import com.example.util.BarcodeRenderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HighContrastBarcodeCard(
    barcodeValue: String,
    formatType: BarcodeFormatType,
    modifier: Modifier = Modifier,
    isLandscapeFullscreen: Boolean = false,
    onCopyNumber: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isLandscapeFullscreen) 20.dp else 24.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isLandscapeFullscreen) 28.dp else 20.dp,
                    vertical = if (isLandscapeFullscreen) 18.dp else 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (formatType.is2D) {
                            Modifier
                                .height(if (isLandscapeFullscreen) 200.dp else 230.dp)
                                .aspectRatio(1f)
                        } else {
                            Modifier.height(if (isLandscapeFullscreen) 170.dp else 138.dp)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val targetWidthPx = with(density) { maxWidth.roundToPx() }.coerceIn(240, 1400)
                val targetHeightPx = with(density) { maxHeight.roundToPx() }.coerceIn(180, 900)

                val renderResultState = produceState(
                    initialValue = BarcodeRenderResult(null),
                    key1 = barcodeValue,
                    key2 = formatType,
                    key3 = targetWidthPx to targetHeightPx
                ) {
                    value = withContext(Dispatchers.Default) {
                        BarcodeGenerator.generateBarcodeBitmap(
                            content = barcodeValue,
                            formatType = formatType,
                            widthPx = targetWidthPx,
                            heightPx = targetHeightPx
                        )
                    }
                }

                val bitmap: Bitmap? = renderResultState.value.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Code-barres $barcodeValue",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentScale = if (formatType.is2D) ContentScale.Fit else ContentScale.FillBounds,
                        filterQuality = FilterQuality.None
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Numéro non encodable en code-barres",
                            color = Color(0xFF4B5563),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (renderResultState.value.usedFallbackCode128) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "Affiché en Code 128",
                            color = Color(0xFF92400E),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscapeFullscreen) 12.dp else 16.dp))

            val formattedNumber = BarcodeGenerator.formatReadableNumber(barcodeValue, formatType)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (onCopyNumber != null) {
                            Modifier
                                .clickable(onClick = onCopyNumber)
                                .testTag("copy_barcode_number")
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedNumber,
                    color = Color.Black,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscapeFullscreen) 24.sp else 21.sp,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )
                if (onCopyNumber != null) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copier le numéro",
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!isLandscapeFullscreen) {
                Text(
                    text = formatType.labelFr,
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun MiniBarcodePreview(
    barcodeValue: String,
    formatType: BarcodeFormatType,
    height: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.roundToPx() }.coerceIn(180, 900)
            val heightPx = with(density) { maxHeight.roundToPx() }.coerceIn(80, 400)

            val result = produceState(
                initialValue = BarcodeRenderResult(null),
                key1 = barcodeValue,
                key2 = formatType
            ) {
                value = withContext(Dispatchers.Default) {
                    BarcodeGenerator.generateBarcodeBitmap(
                        content = barcodeValue,
                        formatType = formatType,
                        widthPx = if (formatType.is2D) heightPx else widthPx,
                        heightPx = heightPx
                    )
                }
            }

            val bmp = result.value.bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Aperçu du code-barres",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}
