package com.mobileai.assistant.presentation.components.genui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.genui.GenUiForm
import com.mobileai.assistant.domain.genui.GenUiFormField
import com.mobileai.assistant.domain.genui.GenUiSelectInput
import com.mobileai.assistant.domain.genui.GenUiSliderInput
import com.mobileai.assistant.domain.genui.GenUiSwitchInput
import com.mobileai.assistant.domain.genui.GenUiTextInput
import com.mobileai.assistant.domain.genui.GenUiUnsupportedField
import com.mobileai.assistant.domain.genui.TextInputType

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormRenderer(
    form: GenUiForm,
    isSubmitted: Boolean = false,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val submittedLocked = form.isSubmitted || isSubmitted

    // State holding field values
    val fieldValues = rememberSaveable {
        val initialMap = mutableMapOf<String, String>()
        form.fields.forEach { field ->
            when (field) {
                is GenUiTextInput -> initialMap[field.fieldId] = field.defaultValue
                is GenUiSelectInput -> initialMap[field.fieldId] = field.defaultSelectedKey ?: field.options.firstOrNull()?.key ?: ""
                is GenUiSliderInput -> initialMap[field.fieldId] = field.defaultValue.toString()
                is GenUiSwitchInput -> initialMap[field.fieldId] = field.defaultValue.toString()
                else -> {}
            }
        }
        mutableStateMapOf<String, String>().apply { putAll(initialMap) }
    }

    val fieldErrors = remember { mutableStateMapOf<String, String>() }

    fun validateAll(): Boolean {
        fieldErrors.clear()
        var isValid = true

        form.fields.forEach { field ->
            val value = fieldValues[field.fieldId].orEmpty()
            if (field.required && value.isBlank()) {
                fieldErrors[field.fieldId] = "${field.label} is required"
                isValid = false
            } else if (field is GenUiTextInput && value.isNotBlank()) {
                when (field.inputType) {
                    TextInputType.NUMBER -> {
                        if (value.toDoubleOrNull() == null) {
                            fieldErrors[field.fieldId] = "Must be a valid number"
                            isValid = false
                        }
                    }
                    TextInputType.EMAIL -> {
                        if (!EMAIL_REGEX.matches(value)) {
                            fieldErrors[field.fieldId] = "Invalid email address"
                            isValid = false
                        }
                    }
                    else -> {}
                }
            }
        }
        return isValid
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (!form.title.isNullOrBlank()) {
                Text(
                    text = form.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Submitted Banner
            if (submittedLocked) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Submitted • Response recorded",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF10B981)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Form Fields
            form.fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))

                val error = fieldErrors[field.fieldId]

                when (field) {
                    is GenUiTextInput -> {
                        val keyboardType = when (field.inputType) {
                            TextInputType.NUMBER -> KeyboardType.Number
                            TextInputType.EMAIL -> KeyboardType.Email
                            else -> KeyboardType.Text
                        }

                        OutlinedTextField(
                            value = fieldValues[field.fieldId] ?: "",
                            onValueChange = { newVal ->
                                if (!submittedLocked) {
                                    fieldValues[field.fieldId] = newVal
                                    fieldErrors.remove(field.fieldId)
                                }
                            },
                            label = {
                                Text(
                                    text = if (field.required) "${field.label} *" else field.label
                                )
                            },
                            placeholder = if (!field.placeholder.isNullOrBlank()) {
                                { Text(field.placeholder) }
                            } else null,
                            supportingText = if (error != null) {
                                { Text(text = error, color = MaterialTheme.colorScheme.error) }
                            } else if (!field.helperText.isNullOrBlank()) {
                                { Text(text = field.helperText!!) }
                            } else null,
                            isError = error != null,
                            singleLine = field.inputType != TextInputType.MULTILINE,
                            maxLines = if (field.inputType == TextInputType.MULTILINE) 4 else 1,
                            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                            enabled = !submittedLocked,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is GenUiSelectInput -> {
                        var expanded by remember { mutableStateOf(false) }
                        val currentValKey = fieldValues[field.fieldId] ?: field.defaultSelectedKey ?: ""
                        val selectedLabel = field.options.find { it.key == currentValKey }?.label ?: currentValKey

                        ExposedDropdownMenuBox(
                            expanded = expanded && !submittedLocked,
                            onExpandedChange = { if (!submittedLocked) expanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (field.required) "${field.label} *" else field.label) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                enabled = !submittedLocked,
                                supportingText = if (error != null) {
                                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                                } else if (!field.helperText.isNullOrBlank()) {
                                    { Text(text = field.helperText!!) }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded && !submittedLocked,
                                onDismissRequest = { expanded = false }
                            ) {
                                field.options.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.label) },
                                        onClick = {
                                            fieldValues[field.fieldId] = opt.key
                                            fieldErrors.remove(field.fieldId)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is GenUiSliderInput -> {
                        val currentVal = (fieldValues[field.fieldId]?.toFloatOrNull() ?: field.defaultValue).coerceIn(field.min, field.max)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (field.required) "${field.label} *" else field.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("%.1f", currentVal),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = currentVal,
                                onValueChange = { newVal ->
                                    if (!submittedLocked) {
                                        fieldValues[field.fieldId] = String.format("%.1f", newVal)
                                    }
                                },
                                valueRange = field.min..field.max,
                                steps = if (field.step > 0 && (field.max - field.min) / field.step > 1) {
                                    ((field.max - field.min) / field.step).toInt() - 1
                                } else 0,
                                enabled = !submittedLocked,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!field.helperText.isNullOrBlank()) {
                                Text(
                                    text = field.helperText!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is GenUiSwitchInput -> {
                        val isChecked = fieldValues[field.fieldId]?.toBooleanStrictOrNull() ?: field.defaultValue

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (field.required) "${field.label} *" else field.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!field.helperText.isNullOrBlank()) {
                                    Text(
                                        text = field.helperText!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { newVal ->
                                    if (!submittedLocked) {
                                        fieldValues[field.fieldId] = newVal.toString()
                                    }
                                },
                                enabled = !submittedLocked
                            )
                        }
                    }

                    is GenUiUnsupportedField -> {
                        Text(
                            text = "Unsupported field: ${field.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            Button(
                onClick = {
                    if (!submittedLocked && validateAll()) {
                        onEvent(
                            GenUiEvent.FormSubmit(
                                formId = form.id,
                                actionId = form.submitActionId,
                                fieldValues = fieldValues.toMap()
                            )
                        )
                    }
                },
                enabled = !submittedLocked,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = form.submitLabel)
            }
        }
    }
}

