package com.anthroteacher.multihasher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthroteacher.multihasher.ui.theme.MultihasherTheme
import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.internal.NoOpContinuation.context
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
//import kotlin.coroutines.jvm.internal.CompletedContinuation.context
import com.anthroteacher.sha3.NativeLib;

const val VERSION = "1.8"

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

        setContent {
            MultihasherTheme {
                MultiHasherApp(
                    currentLocale = sharedPreferences.getString("Language", "en") ?: "en",
                    onLanguageChange = { newLocale ->
                        saveLanguageToPreferences(newLocale)
                        setLocale(this, newLocale) // Apply the new locale
                        recreate() // Recreate the activity to reflect changes
                    },
                )
            }
        }
    }

    private fun loadLocale() {
        val savedLanguage = sharedPreferences.getString("Language", "en") ?: "en"
        setLocale(this, savedLanguage) // Apply the saved or default locale
    }

    // Function to save the selected language to SharedPreferences
    private fun saveLanguageToPreferences(languageCode: String) {
        sharedPreferences.edit().putString("Language", languageCode).apply()
    }

    // Function to set the app's locale
    private fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        context.createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    // Apply the locale whenever the activity is resumed
    override fun onResume() {
        super.onResume()
        loadLocale() // Reload and apply the locale when the activity resumes
    }
}

@Composable
fun MultiHasherApp(
    currentLocale: String,
    onLanguageChange: (String) -> Unit,
) {
    var intentionText by remember { mutableStateOf(TextFieldValue("")) }
    var numHashLevels by remember { mutableStateOf("1") }
    var numRepsPerHashLevel by remember { mutableStateOf("1") }
    var encodingLevel by remember { mutableStateOf("512-Bit") }
    var hashDisplay by remember { mutableStateOf("") }
    var statusLabel by remember { mutableStateOf("") }
    var isHashing by remember { mutableStateOf(false) }
    var hashingJob: Job? by remember { mutableStateOf(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var selectedLanguage by remember { mutableStateOf(currentLocale) }

    val isStartButtonEnabled = intentionText.text.isNotBlank() &&
            numHashLevels.isNotBlank() && numRepsPerHashLevel.isNotBlank()

    val disableAllInputs = isHashing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color.White)
            .verticalScroll(scrollState)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Text(stringResource(R.string.multihasher_by), fontSize = 20.sp)

        // Correct placement: File picker launcher inside a @Composable function
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let {
                    val inputStream = context.contentResolver.openInputStream(it)
                    inputStream?.let {
                        val bytes = inputStream.readBytes()
                        val hash = sha512(bytes.toString(Charsets.UTF_8))
                        intentionText = TextFieldValue(intentionText.text + "\n" + hash)
                        inputStream.close()
                    }
                }
            }
        )

        // Multiline Intention Box with 5 lines shown but allowing unlimited input
        OutlinedTextField(
            value = intentionText,
            onValueChange = { intentionText = it },
            label = { Text(stringResource(R.string.enter_intention)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp), // Set height to show approximately 5 lines
            maxLines = Int.MAX_VALUE, // Allow unlimited lines
            enabled = !disableAllInputs,
            singleLine = false
        )

        // Hash Levels Box with Validation
        OutlinedTextField(
            value = numHashLevels,
            onValueChange = {
                val sanitizedInput = it.filter { char -> char.isDigit() }.take(4)
                numHashLevels = sanitizedInput.takeIf { input -> input.toIntOrNull() in 1..1000 } ?: numHashLevels
            },
            label = { Text(stringResource(R.string.hash_levels)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !disableAllInputs
        )

        // Repetitions Box with Validation
        OutlinedTextField(
            value = numRepsPerHashLevel,
            onValueChange = {
                val sanitizedInput = it
                    .replace(Regex("[^0-9kKmM.]"), "")  // Allow only digits, a single period, and K/k/M/m suffix
                    .replace(Regex("\\.(?=.*\\.)"), "")  // Allow only the first period
                    .replace(Regex("(?<=[kKmM]).*"), "")  // Remove any characters after K/k/M/m suffix

                // Parse and validate input, limiting to a maximum of 100,000
                val validatedInput = validateAndParseInput(sanitizedInput, 100000)
                numRepsPerHashLevel = validatedInput.toString()
            },
            label = { Text(stringResource(R.string.reps_per_hash)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !disableAllInputs
        )

        // Encoding Level Dropdown Menu
        EncodingDropdownMenu(
            selectedOption = encodingLevel,
            onOptionSelected = { encodingLevel = it },
            enabled = !disableAllInputs
        )

        Text(
            text = hashDisplay,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray)
                .padding(8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !disableAllInputs
                ) {
                    focusManager.clearFocus() // Hide keyboard
                    if (hashDisplay.isNotBlank()) { // Only copy if there is a value
                        clipboardManager.setText(
                            androidx.compose.ui.text.AnnotatedString(
                                hashDisplay
                            )
                        )
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.hash_copied),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                },
            maxLines = Int.MAX_VALUE // Allows wrapping if the hash is too long
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(statusLabel, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Adds space between buttons
        ) {
            // Load File Button on the left
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !disableAllInputs,
                modifier = Modifier
                    .weight(1f) // Makes the button take equal width
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.load_file))
            }

            // Start Button on the right
            Button(
                onClick = {
                    if (isHashing) {
                        isHashing = false
                        hashingJob?.cancel()
                        statusLabel = context.getString(R.string.hashing_stopped)
                    } else {
                        isHashing = true
                        statusLabel = context.getString(R.string.calculating_hash)
                        hashingJob = coroutineScope.launch {
                            startHashing(
                                intentionText.text,
                                numHashLevels,
                                numRepsPerHashLevel,
                                encodingLevel,
                                onUpdateHashDisplay = { hashDisplay = it },
                                onUpdateStatusLabel = { statusLabel = it },
                                onComplete = {
                                    isHashing = false
                                    statusLabel = context.getString(R.string.hashing_completed)
                                }
                            )
                        }
                    }
                },
                enabled = isStartButtonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHashing) Color.Red else Color.Green,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f) // Makes the button take equal width
                    .height(48.dp)
            ) {
                Text(if (isHashing) context.getString(R.string.stop) else context.getString(R.string.start))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LanguageDropdown(
            currentLocale = selectedLanguage,
            onLanguageSelected = { newLanguage ->
                selectedLanguage = newLanguage
            },
            disableAllInputs = disableAllInputs
        )
        Button(
            onClick = {
                onLanguageChange(selectedLanguage)  // Use selectedLanguage instead of currentLocale
            },
            enabled = !disableAllInputs,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.update_language))
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Add this line to display the version below the "Start" button
        Text(stringResource(R.string.version,VERSION), fontSize = 12.sp)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    currentLocale: String,
    onLanguageSelected: (String) -> Unit, // Renamed to onLanguageSelected for clarity
    disableAllInputs:Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(currentLocale) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if(!disableAllInputs){
                expanded = !expanded
            }else{
                expanded=false;
            } },
    ) {
        TextField(
            readOnly = true,
            enabled = !disableAllInputs,
            value = languages.find { it.code == selectedLanguage }?.displayName ?: "Select Language",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent, // Remove underline when focused
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Dropdown Icon"
                )
            },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        selectedLanguage = language.code
                        expanded = false
                        onLanguageSelected(selectedLanguage) // Pass the selected language up
                    }
                )
            }
        }
    }
}


@Composable
fun EncodingDropdownMenu(
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    data class Option(val title: String, val value: String)
    var options = listOf(
        Option(stringResource(R.string.bit64),"64-Bit"),
        Option(stringResource(R.string.bit256),"256-Bit"),
        Option(stringResource(R.string.bit512),"512-Bit"),
    )

    Box {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(text =  if (selectedOption == "64-Bit") options[0].title else if (selectedOption == "256-Bit") options[1].title else if (selectedOption == "512-Bit") options[2].title else options[3].title )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
           options.forEach { option ->
                DropdownMenuItem(
                    text = {Text(option.value)},
                    onClick = {
                        expanded = false
                        onOptionSelected(option.value)
                    }
                )
            }
        }
    }
}

suspend fun startHashing(
    originalText: String,
    numHashLevels: String,
    numRepsPerHashLevel: String,
    encoding: String,
    onUpdateHashDisplay: (String) -> Unit,
    onUpdateStatusLabel: (String) -> Unit,
    onComplete: () -> Unit
) {
    val hashLevels = validateAndParseInput(numHashLevels, 1000)
    val repsPerLevel = validateAndParseInput(numRepsPerHashLevel, 100000)
    var repeatedText: String
    var repeatedHash: String
    var hashedText: String = originalText // Start with the original text for hashing

    withContext(Dispatchers.Default) {
        hashedText = NativeLib.InitInstance().CalcHash(originalText, repsPerLevel, hashLevels, 0);
//        for (level in 1..hashLevels) {
//            repeatedText = (1..repsPerLevel).joinToString("\n") { hashedText } // Repeat using the current hash value
//            hashedText = sha512(repeatedText)
//
//            repeatedHash = ""
//            for (i in 1..repsPerLevel) {
//                repeatedHash += "$originalText: $hashedText\n"
//            }
//            hashedText = sha512("$originalText: $repeatedHash")
//
//            // Update the UI after completing each hash level
//            withContext(Dispatchers.Main) {
//                onUpdateHashDisplay(
//                    when (encoding) {
//                        "64-Bit" -> sha64(hashedText)
//                        "256-Bit" -> sha256(hashedText) // Display 256-bit hash on one line
//                        "512-Bit" -> hashedText // Display 512-bit hash on one line
//                        else -> hashedText.chunked(64).joinToString("") // Join without line breaks
//                    }
//                )
//                onUpdateStatusLabel(
//                    "$level / $hashLevels Hash Levels Converted."
//                )
//            }
//        }

        // Ensure the final hash value is displayed when complete
        withContext(Dispatchers.Main) {
            onUpdateHashDisplay(
                when (encoding) {
                    "64-Bit" -> sha64(hashedText)
                    "256-Bit" -> sha256(hashedText)
                    "512-Bit" -> hashedText
                    else -> hashedText.chunked(64).joinToString("")
                }
            )
            onComplete()
        }
    }
}

fun sha512(input: String): String {
    val md = MessageDigest.getInstance("SHA-512")
    val digest = md.digest(input.toByteArray())
    return BigInteger(1, digest).toString(16).padStart(128, '0').toUpperCase(Locale.ROOT)
}

fun sha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray())
    return BigInteger(1, digest).toString(16).padStart(64, '0').toUpperCase(Locale.ROOT)
}

fun sha64(input: String): String {
    val sha512Hash = sha512(input)
    val chunks = sha512Hash.chunked(16).map { BigInteger(it, 16) }
    val sum = chunks.reduce { acc, next -> acc.add(next) }
    return sum.toString(16).padStart(16, '0').take(16).toUpperCase(Locale.ROOT)
}

fun validateAndParseInput(input: String, maxValue: Int): Int {
    val normalizedInput = input.trim().toUpperCase(Locale.ROOT)
    val value = when {
        normalizedInput.endsWith("K") -> (normalizedInput.dropLast(1).toDouble() * 1000).toInt()
        normalizedInput.endsWith("M") -> (normalizedInput.dropLast(1).toDouble() * 1000000).toInt()
        else -> input.toIntOrNull() ?: 1
    }
    return value.coerceAtMost(maxValue)
}
