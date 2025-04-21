package com.anthroteacher.multihasher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
// import androidx.compose.foundation.clickable // No longer needed on the Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource // Still used elsewhere maybe
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy // Import for Copy Icon
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anthroteacher.multihasher.ui.theme.MultihasherTheme // Import your app theme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultihasherTheme { // Apply Material 3 Theme
                MultihasherApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultihasherApp(viewModel: MainViewModel) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle() // Observe state
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // --- Resolve strings needed outside direct Composable calls ONCE ---
    // (This avoids calling stringResource repeatedly inside loops or modifier chains)
    val hashResultTextContentDesc = stringResource(R.string.cd_hash_result_text)
    val copyHashContentDesc = stringResource(R.string.cd_copy_hash)
    val placeholderHashText = stringResource(R.string.placeholder_hash_display)
    val hashCopiedToastText = stringResource(R.string.toast_hash_copied)
    val statusIdleText = stringResource(R.string.status_idle)
    val loadFileContentDesc = stringResource(R.string.cd_load_file)
    val startHashingContentDesc = stringResource(R.string.cd_start_hashing)
    val stopHashingContentDesc = stringResource(R.string.cd_stop_hashing)
    val appTitleText = stringResource(R.string.app_name)
    val intentionLabelText = stringResource(R.string.label_intention)
    val hashLevelsLabelText = stringResource(R.string.label_hash_levels, AppConstants.MIN_HASH_LEVELS, AppConstants.MAX_HASH_LEVELS)
    val repsLabelText = stringResource(R.string.label_reps_per_level, AppConstants.MIN_REPS_PER_LEVEL, AppConstants.MAX_REPS_PER_LEVEL / 1000)
    val loadFileButtonText = stringResource(R.string.button_load_file)
    val startButtonText = stringResource(R.string.button_start)
    val stopButtonText = stringResource(R.string.button_stop)

    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val fileContent = inputStream.readBytes().toString(Charsets.UTF_8)
                        viewModel.processLoadedFileContent(fileContent)
                    }
                    // Error Toast needs context but stringResource needs @Composable scope,
                    // so construct the string here or pass context to VM (less ideal).
                    // Best practice is usually to have the VM expose an error state/event
                    // and let the Composable react to it. For simplicity here, we keep it.
                } catch (e: Exception) {
                    val errorMsg = context.getString(R.string.toast_error_reading_file, e.localizedMessage ?: "Unknown error")
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appTitleText) }, // Use resolved string
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(AppConstants.SCREEN_PADDING)
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(AppConstants.ITEM_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Intention Input
            OutlinedTextField(
                value = uiState.intentionText,
                onValueChange = { viewModel.updateIntentionText(it) },
                label = { Text(intentionLabelText) }, // Use resolved string
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppConstants.INTENTION_BOX_HEIGHT, max = AppConstants.INTENTION_BOX_HEIGHT),
                maxLines = Int.MAX_VALUE,
                enabled = !uiState.isHashing,
                singleLine = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Hash Levels Input
            OutlinedTextField(
                value = uiState.numHashLevels,
                onValueChange = { viewModel.updateNumHashLevels(it) },
                label = { Text(hashLevelsLabelText) }, // Use resolved string
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isHashing,
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Repetitions Input
            OutlinedTextField(
                value = uiState.numRepsPerHashLevel,
                onValueChange = { viewModel.updateNumRepsPerLevel(it) },
                label = { Text(repsLabelText) }, // Use resolved string
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isHashing,
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Encoding Dropdown
            EncodingDropdownMenu( // Assuming EncodingDropdownMenu is in the same file or has its own string resolution
                selectedOption = uiState.encodingLevel,
                options = AppConstants.ENCODING_OPTIONS,
                onOptionSelected = { viewModel.updateEncodingLevel(it) },
                enabled = !uiState.isHashing
            )

            // --- Hash Display Area with Copy Button ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = AppConstants.HASH_DISPLAY_PADDING, end = AppConstants.HASH_DISPLAY_PADDING / 2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.hashDisplay.ifBlank { placeholderHashText }, // Use resolved string
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = AppConstants.HASH_DISPLAY_PADDING / 2)
                        .semantics {
                            contentDescription = hashResultTextContentDesc // Assign the resolved String variable
                        }
                )

                Spacer(modifier = Modifier.width(AppConstants.BUTTON_SPACING / 2))

                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.hashDisplay))
                        Toast.makeText(context, hashCopiedToastText, Toast.LENGTH_SHORT).show() // Use resolved string
                    },
                    enabled = uiState.hashDisplay.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyHashContentDesc, // Use resolved string
                        tint = if (uiState.hashDisplay.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
            // --- End Hash Display Area ---


            // Progress Indicator
            if (uiState.isHashing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Status Label
            Text(
                text = uiState.statusLabel.ifBlank { statusIdleText } , // Use resolved string
                fontSize = 16.sp,
                color = if (uiState.statusLabel.contains("Error", ignoreCase = true)) MaterialTheme.colorScheme.error else LocalContentColor.current,
                modifier = Modifier.padding(top = AppConstants.ITEM_SPACING / 2)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppConstants.BUTTON_SPACING)
            ) {
                // Load File Button
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !uiState.isHashing,
                    modifier = Modifier
                        .weight(1f)
                        .height(AppConstants.BUTTON_HEIGHT)
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = loadFileContentDesc) // Use resolved string
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(loadFileButtonText) // Use resolved string
                }

                // Start/Stop Button
                Button(
                    onClick = { viewModel.toggleHashing() },
                    enabled = uiState.isStartEnabled || uiState.isHashing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isHashing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (uiState.isHashing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(AppConstants.BUTTON_HEIGHT)
                ) {
                    val icon = if (uiState.isHashing) Icons.Filled.Stop else Icons.Filled.PlayArrow
                    // Use resolved strings for text and content description
                    val text = if (uiState.isHashing) stopButtonText else startButtonText
                    val cd = if (uiState.isHashing) stopHashingContentDesc else startHashingContentDesc

                    Icon(icon, contentDescription = cd) // Use resolved string
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text) // Use resolved string
                }
            }

            // Version Text
            Text(
                AppConstants.VERSION,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = AppConstants.ITEM_SPACING / 2)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncodingDropdownMenu( // Make sure strings here are also resolved correctly if needed
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val encodingLabelText = stringResource(R.string.label_encoding) // Resolve string here

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(encodingLabelText) }, // Use resolved string
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = enabled,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                // Create content description string dynamically or predefine if fixed options
                val dropdownItemContentDesc = "Select $option encoding"
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                    modifier = Modifier.semantics { contentDescription = dropdownItemContentDesc } // OK to construct string here
                )
            }
        }
    }
}