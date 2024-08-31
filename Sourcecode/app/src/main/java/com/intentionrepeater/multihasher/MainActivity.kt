package com.intentionrepeater.multihasher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultiHasherApp()
        }
    }
}

@Composable
fun MultiHasherApp() {
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
        Text("Multi-Hasher by Anthro Teacher", fontSize = 20.sp)

        // Multiline Intention Box with 5 lines shown but allowing unlimited input
        OutlinedTextField(
            value = intentionText,
            onValueChange = { intentionText = it },
            label = { Text("Enter Intention to Multi-Hash") },
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
            label = { Text("Hash Levels [1-1000]: ") },
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
            label = { Text("Reps per Hash Level [1-100k]: ") },
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
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(hashDisplay))
                        Toast.makeText(context, "Hash copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                },
            maxLines = Int.MAX_VALUE // Allows wrapping if the hash is too long
        )

        Text(statusLabel, fontSize = 12.sp)

        // Start/Stop Button remains enabled
        Button(
            onClick = {
                if (isHashing) {
                    isHashing = false
                    hashingJob?.cancel()
                    statusLabel = "Hashing stopped."
                } else {
                    isHashing = true
                    statusLabel = "Calculating First Hash..."
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
                                statusLabel = "Hashing completed."
                            }
                        )
                    }
                }
            },
            enabled = isStartButtonEnabled,  // Button always enabled while toggling between Start/Stop
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isHashing) Color.Red else Color.Green,
                contentColor = Color.White
            )
        ) {
            Text(if (isHashing) "Stop" else "Start")
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

    Box {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(text = selectedOption)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf("64-Bit", "256-Bit", "512-Bit").forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
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
        for (level in 1..hashLevels) {
            repeatedText = hashedText.repeat(repsPerLevel) // Repeat using the current hash value
            hashedText = sha512(repeatedText)

            repeatedHash = ""
            for (i in 1..repsPerLevel) {
                repeatedHash += hashedText
            }
            hashedText = sha512(repeatedHash)

            // Update the UI after completing each hash level
            withContext(Dispatchers.Main) {
                onUpdateHashDisplay(
                    when (encoding) {
                        "64-Bit" -> sha64(hashedText)
                        "256-Bit" -> sha256(hashedText) // Display 256-bit hash on one line
                        "512-Bit" -> hashedText // Display 512-bit hash on one line
                        else -> hashedText.chunked(64).joinToString("") // Join without line breaks
                    }
                )
                onUpdateStatusLabel(
                    "$level / $hashLevels Hash Levels Converted."
                )
            }
        }

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
