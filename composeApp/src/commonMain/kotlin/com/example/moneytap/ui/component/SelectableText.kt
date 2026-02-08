package com.example.moneytap.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType

/**
 * Text component that allows users to select portions of text for field definition.
 *
 * Uses native Android text selection with familiar drag handles.
 * User selects text, then taps "Next" to confirm the selection.
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
    // Build annotated text with existing selections highlighted
    val annotatedText = remember(text, existingSelections) {
        buildAnnotatedText(text, existingSelections, currentFieldType)
    }

    // Track the current text field value with selection
    var textFieldValue by remember(text) {
        mutableStateOf(TextFieldValue(annotatedString = annotatedText, selection = TextRange.Zero))
    }

    // Extract current selection info
    val hasSelection = textFieldValue.selection.start != textFieldValue.selection.end
    val selectedText = if (hasSelection) {
        // Handle both forward and backward selection
        val start = minOf(textFieldValue.selection.start, textFieldValue.selection.end)
        val end = maxOf(textFieldValue.selection.start, textFieldValue.selection.end)
        text.substring(start, end)
    } else {
        ""
    }

    val instructionText = if (hasSelection) {
        "Selection made. Tap Next to confirm."
    } else {
        "Select ${currentFieldType.name.lowercase().replace('_', ' ')} using the handles"
    }

    Column(modifier = modifier) {
        Text(
            text = instructionText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // BasicTextField with readOnly=true for native selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    // Update selection but keep the same text
                    textFieldValue = newValue.copy(annotatedString = annotatedText)
                },
                readOnly = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Show selected text preview
        if (hasSelection) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Selected: \"$selectedText\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasSelection) {
                TextButton(
                    onClick = {
                        // Clear selection
                        textFieldValue = textFieldValue.copy(selection = TextRange.Zero)
                    },
                ) {
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // Handle both forward and backward selection
                        val start = minOf(textFieldValue.selection.start, textFieldValue.selection.end)
                        val end = maxOf(textFieldValue.selection.start, textFieldValue.selection.end)
                        onSelectionMade(start, end, selectedText)

                        // Clear selection for next field
                        textFieldValue = textFieldValue.copy(selection = TextRange.Zero)
                    },
                ) {
                    Text("Next")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Build annotated string with highlights for existing selections.
 */
private fun buildAnnotatedText(
    text: String,
    existingSelections: List<FieldSelection>,
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

