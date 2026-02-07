package com.example.moneytap.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType

/**
 * Text component that allows users to select portions of text for field definition.
 *
 * Shows existing selections with colored highlights and allows new selections
 * by long-pressing and dragging.
 *
 * @param text The text to display and make selectable
 * @param existingSelections Already selected fields with their ranges
 * @param currentFieldType The field type being selected (for coloring)
 * @param onSelectionMade Callback when user completes a selection
 * @param modifier Modifier for the component
 */
@Composable
fun SelectableText(
    text: String,
    existingSelections: List<FieldSelection>,
    currentFieldType: FieldType,
    onSelectionMade: (startIndex: Int, endIndex: Int, selectedText: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectionStart by remember { mutableStateOf<Int?>(null) }
    var selectionEnd by remember { mutableStateOf<Int?>(null) }
    var textPosition by remember { mutableStateOf(Offset.Zero) }

    val annotatedText = remember(text, existingSelections, selectionStart, selectionEnd) {
        buildAnnotatedText(text, existingSelections, selectionStart, selectionEnd, currentFieldType)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
                .onGloballyPositioned { coordinates ->
                    textPosition = coordinates.positionInRoot()
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            // Calculate character index from tap position
                            // This is a simplified approach - in production you'd use more precise text layout
                            val charIndex = estimateCharIndexFromOffset(offset, text, size.width.toFloat())
                            selectionStart = charIndex
                            selectionEnd = charIndex
                        },
                        onPress = {
                            val offset = it
                            tryAwaitRelease()

                            if (selectionStart != null && selectionEnd != null) {
                                val start = minOf(selectionStart!!, selectionEnd!!)
                                val end = maxOf(selectionStart!!, selectionEnd!!)

                                if (end > start) {
                                    val selectedText = text.substring(start, end)
                                    onSelectionMade(start, end, selectedText)
                                }

                                selectionStart = null
                                selectionEnd = null
                            }
                        },
                    )
                },
        ) {
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = "Tap and drag to select ${currentFieldType.name.lowercase().replace('_', ' ')}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Build annotated string with highlights for existing selections.
 */
private fun buildAnnotatedText(
    text: String,
    existingSelections: List<FieldSelection>,
    selectionStart: Int?,
    selectionEnd: Int?,
    currentFieldType: FieldType,
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0

        // Sort selections by start index
        val sortedSelections = existingSelections.sortedBy { it.startIndex }

        sortedSelections.forEach { selection ->
            // Add text before this selection
            if (selection.startIndex > lastIndex) {
                append(text.substring(lastIndex, selection.startIndex))
            }

            // Add highlighted selection
            withStyle(
                style = SpanStyle(
                    background = getColorForFieldType(selection.fieldType),
                    color = Color.White,
                ),
            ) {
                append(selection.selectedText)
            }

            lastIndex = selection.endIndex
        }

        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }

        // Highlight current selection in progress
        if (selectionStart != null && selectionEnd != null) {
            val start = minOf(selectionStart, selectionEnd)
            val end = maxOf(selectionStart, selectionEnd)
            addStyle(
                style = SpanStyle(
                    background = getColorForFieldType(currentFieldType).copy(alpha = 0.5f),
                ),
                start = start,
                end = minOf(end, text.length),
            )
        }
    }
}

/**
 * Get highlight color for a field type.
 */
private fun getColorForFieldType(fieldType: FieldType): Color {
    return when (fieldType) {
        FieldType.AMOUNT -> Color(0xFF4CAF50) // Green
        FieldType.MERCHANT -> Color(0xFF2196F3) // Blue
        FieldType.BALANCE -> Color(0xFFFF9800) // Orange
        FieldType.CARD_LAST_4 -> Color(0xFF9C27B0) // Purple
        FieldType.DATE -> Color(0xFFE91E63) // Pink
        FieldType.TRANSACTION_TYPE -> Color(0xFF607D8B) // Blue Grey
    }
}

/**
 * Estimate character index from tap offset.
 * This is a simplified approach - in production you'd use TextLayoutResult.
 */
private fun estimateCharIndexFromOffset(offset: Offset, text: String, width: Float): Int {
    // Simple estimation: assume monospace and calculate based on offset
    val charWidth = width / text.length
    val estimatedIndex = (offset.x / charWidth).toInt()
    return estimatedIndex.coerceIn(0, text.length)
}
