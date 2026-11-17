package dev.daymark.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.decrease
import dev.daymark.resources.edit
import dev.daymark.resources.increase
import dev.daymark.resources.save
import dev.daymark.settings.str
import kotlin.math.roundToInt

data class ValueSliderState(
    val label: String,
    val value: Int,
    val formattedValue: String,
    val min: Int,
    val max: Int,
    val step: Int = 1,
    val sliderSteps: Int = 0,
    val enabled: Boolean = true,
    val testTag: String? = null
)

data class ValueEditActions(
    val onStartEdit: () -> Unit,
    val onCommit: (Int) -> Unit,
    val onCancel: () -> Unit
)

private val MinusIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Minus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 13f)
        lineTo(5f, 13f)
        lineTo(5f, 11f)
        lineTo(19f, 11f)
        close()
    }.build()
}

@Composable
fun ValueSliderControl(
    state: ValueSliderState,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    val editActions = remember(state.value, onValueChange) {
        ValueEditActions(
            onStartEdit = { isEditing = true },
            onCommit = { newValue ->
                onValueChange(newValue)
                isEditing = false
            },
            onCancel = { isEditing = false }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ValueSliderHeader(
            state = state,
            isEditing = isEditing,
            actions = editActions
        )
        ValueSliderTrackRow(
            state = state,
            onValueChange = onValueChange
        )
    }
}

@Composable
private fun ValueSliderHeader(
    state: ValueSliderState,
    isEditing: Boolean,
    actions: ValueEditActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedContent(
            targetState = isEditing,
            label = "ValueBadgeMorph"
        ) { editing ->
            if (editing) {
                ValueInlineEditor(
                    state = state,
                    onCommit = actions.onCommit,
                    onCancel = actions.onCancel
                )
            } else {
                ValueDisplayBadge(
                    formattedText = state.formattedValue,
                    enabled = state.enabled,
                    onClick = actions.onStartEdit
                )
            }
        }
    }
}

@Composable
private fun ValueDisplayBadge(
    formattedText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .animateContentSize()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = formattedText,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith
                        (slideOutVertically { -it } + fadeOut())
                },
                label = "ValueTicker"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = str(Res.string.edit),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ValueInlineEditor(
    state: ValueSliderState,
    onCommit: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf(state.value.toString()) }
    val focusRequester = remember { FocusRequester() }
    val parsed = inputText.toIntOrNull()
    val isValid = parsed != null && parsed in state.min..state.max

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { newText ->
                if (newText.length <= 5 && newText.all { it.isDigit() }) {
                    inputText = newText
                }
            },
            singleLine = true,
            isError = !isValid && inputText.isNotEmpty(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isValid) onCommit(parsed!!) }
            ),
            textStyle = MaterialTheme.typography.labelMedium,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
                .width(84.dp)
                .height(48.dp)
                .focusRequester(focusRequester)
        )
        IconButton(
            onClick = { if (isValid) onCommit(parsed!!) },
            enabled = isValid,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = str(Res.string.save),
                tint = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = str(Res.string.cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ValueSliderTrackRow(
    state: ValueSliderState,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val canDecrease = state.enabled && state.value > state.min
    val canIncrease = state.enabled && state.value < state.max

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            icon = MinusIcon,
            description = str(Res.string.decrease),
            enabled = canDecrease,
            onClick = { onValueChange((state.value - state.step).coerceAtLeast(state.min)) }
        )

        Slider(
            value = state.value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = state.min.toFloat()..state.max.toFloat(),
            steps = state.sliderSteps,
            enabled = state.enabled,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (state.testTag != null) Modifier.testTag(state.testTag)
                    else Modifier
                )
                .semantics {
                    contentDescription = state.label
                    stateDescription = state.formattedValue
                }
        )

        StepperButton(
            icon = Icons.Default.Add,
            description = str(Res.string.increase),
            enabled = canIncrease,
            onClick = { onValueChange((state.value + state.step).coerceAtMost(state.max)) }
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outlineVariant
            else Color.Transparent
        ),
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
