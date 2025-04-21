package com.anthroteacher.multihasher

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthroteacher.sha3.NativeLib
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

// Represents the UI State
data class MultiHasherUiState(
    val intentionText: TextFieldValue = TextFieldValue(""),
    val numHashLevels: String = AppConstants.DEFAULT_HASH_LEVELS,
    val numRepsPerHashLevel: String = AppConstants.DEFAULT_REPS_PER_LEVEL,
    val encodingLevel: String = AppConstants.DEFAULT_ENCODING,
    val hashDisplay: String = "",
    val statusLabel: String = "",
    val isHashing: Boolean = false,
    val isStartEnabled: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MultiHasherUiState())
    val uiState: StateFlow<MultiHasherUiState> = _uiState.asStateFlow()

    private var hashingJob: Job? = null
    private val nativeLib = NativeLib.InitInstance() // Initialize native lib once

    init {
        // Initial validation check
        updateStartButtonState()
    }

    fun updateIntentionText(newValue: TextFieldValue) {
        val cappedText = if (newValue.text.length > AppConstants.MAX_INTENTION_LENGTH) {
            newValue.text.substring(0, AppConstants.MAX_INTENTION_LENGTH)
        } else {
            newValue.text
        }
        // Keep cursor position if possible
        val cappedValue = if (cappedText.length < newValue.text.length) {
            TextFieldValue(cappedText, newValue.selection)
        } else {
            newValue
        }

        _uiState.update { it.copy(intentionText = cappedValue) }
        updateStartButtonState()
    }

    fun updateNumHashLevels(input: String) {
        val sanitized = input.filter { it.isDigit() }
        val value = sanitized.toIntOrNull()
        val validValue = when {
            sanitized.isEmpty() -> "" // Allow empty field for typing
            value == null -> _uiState.value.numHashLevels // Invalid number, keep old
            value < AppConstants.MIN_HASH_LEVELS -> AppConstants.MIN_HASH_LEVELS.toString() // Clamp min
            value > AppConstants.MAX_HASH_LEVELS -> AppConstants.MAX_HASH_LEVELS.toString() // Clamp max
            else -> sanitized // Valid input within range
        }
        _uiState.update { it.copy(numHashLevels = validValue) }
        updateStartButtonState()
    }

    fun updateNumRepsPerLevel(input: String) {
        val sanitized = input.uppercase(Locale.ROOT).replace(Regex("[^0-9KMG.]"), "")
            .replace(Regex("\\.(?=.*\\.)"), "") // Keep only first dot
            .replace(Regex("(?<=[KMG]).*"), "") // Remove chars after suffix

        // Basic validation if it looks like a valid number structure before full parsing
        val looksValid = sanitized.isEmpty() || sanitized.matches(Regex("^\\d*\\.?\\d*[KMG]?$"))

        if (looksValid) {
            _uiState.update { it.copy(numRepsPerHashLevel = sanitized) }
            updateStartButtonState() // Validate based on parsed value later if needed
        }
        // No need to parse/clamp here, do it just before hashing. Keep raw valid input.
    }

    fun updateEncodingLevel(level: String) {
        _uiState.update { it.copy(encodingLevel = level) }
        // If hashing finished, reformat displayed hash
        if (!uiState.value.isHashing && uiState.value.hashDisplay.isNotEmpty()) {
            val currentRawHash = uiState.value.hashDisplay // Assuming hashDisplay holds the RAW 512 hash
            _uiState.update { it.copy(hashDisplay = formatHash(currentRawHash, level)) }
        }
    }

    fun processLoadedFileContent(content: String) {
        val currentText = _uiState.value.intentionText.text
        val fileHash512 = CryptoUtils.sha512(content) // Hash loaded file content
        val newText = if (currentText.isEmpty()) fileHash512 else "$currentText\n$fileHash512"
        updateIntentionText(TextFieldValue(newText)) // Use the update function for consistency
    }

    fun toggleHashing() {
        if (_uiState.value.isHashing) {
            stopHashing()
        } else {
            startHashing()
        }
    }

    private fun startHashing() {
        val currentState = _uiState.value
        val hashLevels = parseInputWithSuffixes(currentState.numHashLevels, AppConstants.MAX_HASH_LEVELS).coerceAtLeast(AppConstants.MIN_HASH_LEVELS)
        val repsPerLevel = parseInputWithSuffixes(currentState.numRepsPerHashLevel, AppConstants.MAX_REPS_PER_LEVEL).coerceAtLeast(AppConstants.MIN_REPS_PER_LEVEL)
        val intention = currentState.intentionText.text

        _uiState.update {
            it.copy(
                isHashing = true,
                statusLabel = "Calculating Hash...", // Use stringResource in Composable
                hashDisplay = "" // Clear previous hash
            )
        }

        hashingJob?.cancel() // Cancel any previous job
        hashingJob = viewModelScope.launch {
            try {
                // Run computation on Default dispatcher
                val finalHash = withContext(Dispatchers.Default) {
                    nativeLib.CalcHash(intention, repsPerLevel, hashLevels, 0)
                }

                // Update UI on Main dispatcher
                if (isActive) { // Check if job wasn't cancelled
                    _uiState.update {
                        it.copy(
                            hashDisplay = formatHash(finalHash, currentState.encodingLevel),
                            statusLabel = "$hashLevels Levels, $repsPerLevel Reps/Level Completed.", // String resource
                            isHashing = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(statusLabel = "Hashing cancelled.", isHashing = false) }
                }

            } catch (e: CancellationException) {
                // Expected when job is cancelled
                _uiState.update { it.copy(statusLabel = "Hashing stopped by user.", isHashing = false) }
                println("Hashing Job Cancelled: ${e.message}")
            } catch (e: Exception) {
                // Handle other potential errors from native code or formatting
                _uiState.update { it.copy(
                    statusLabel = "Error during hashing: ${e.localizedMessage}", // String resource
                    isHashing = false
                )}
                println("Hashing Error: ${e.stackTraceToString()}") // Log detailed error
            } finally {
                updateStartButtonState() // Re-evaluate button state
            }
        }
    }

    private fun stopHashing() {
        hashingJob?.cancel()
        hashingJob = null
        // Status update happens in the cancellation handler or finally block of the job
        _uiState.update { it.copy(isHashing = false, statusLabel = "Stopping hash calculation...") } // Temporary status
    }

    private fun updateStartButtonState() {
        val currentState = _uiState.value
        val isValid = currentState.intentionText.text.isNotBlank() &&
                currentState.numHashLevels.isNotBlank() &&
                currentState.numHashLevels.toIntOrNull() != null && // Ensure it's a number
                currentState.numRepsPerHashLevel.isNotBlank() &&
                parseInputWithSuffixes(currentState.numRepsPerHashLevel, AppConstants.MAX_REPS_PER_LEVEL) >= AppConstants.MIN_REPS_PER_LEVEL // Ensure valid parse

        _uiState.update { it.copy(isStartEnabled = isValid) }
    }

    // Helper to parse inputs like "100", "10k", "1.5m"
    private fun parseInputWithSuffixes(input: String, maxValue: Int): Int {
        val trimmedInput = input.trim().uppercase(Locale.ROOT)
        if (trimmedInput.isEmpty()) return 0 // Or return MIN_VALUE appropriate for context

        return try {
            val multiplier = when {
                trimmedInput.endsWith("K") -> 1000.0
                trimmedInput.endsWith("M") -> 1000000.0
                trimmedInput.endsWith("G") -> 1000000000.0 // Added Giga
                else -> 1.0
            }
            val numberPart = if (multiplier > 1.0) trimmedInput.dropLast(1) else trimmedInput
            val value = numberPart.toDouble() * multiplier
            value.toInt().coerceAtMost(maxValue) // Clamp to max value
        } catch (e: NumberFormatException) {
            0 // Or return MIN_VALUE, Invalid format
        }
    }

    // Formats the final raw SHA512 hash based on selected encoding
    private fun formatHash(rawHash: String, encoding: String): String {
        // Assuming rawHash is always the full 512-bit hex string from nativeLib
        return when (encoding) {
            AppConstants.ENCODING_64_BIT -> CryptoUtils.sha64From512(rawHash) // Use a helper for clarity
            AppConstants.ENCODING_256_BIT -> CryptoUtils.sha256From512(rawHash) // Use a helper
            AppConstants.ENCODING_512_BIT -> rawHash // Already 512-bit
            else -> rawHash // Default fallback
        }
    }

    // Clear resources if needed
    override fun onCleared() {
        super.onCleared()
        hashingJob?.cancel() // Ensure job is cancelled when ViewModel is destroyed
    }
}

// Separate Utils class for cryptographic operations keeps ViewModel cleaner
object CryptoUtils {
    fun sha512(input: String): String {
        val md = MessageDigest.getInstance("SHA-512")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.toHexString()
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.toHexString()
    }

    // Assumes input is a valid 128-char hex string (SHA-512 output)
    fun sha256From512(sha512Hex: String): String {
        if (sha512Hex.length != 128) return "Invalid SHA512 Input" // Or throw exception
        // Re-hash the 512 hash to get 256, or take first 64 chars if that's the desired logic
        // OPTION 1: Re-hash the *original* input with SHA-256 (if possible)
        // OPTION 2: Hash the *512 hex string* itself with SHA-256 (less common)
        // OPTION 3: Truncate the 512 hex string (simplest, often sufficient for display)
        return sha512Hex.take(64).uppercase(Locale.ROOT) // Using truncation
    }

    // Assumes input is a valid 128-char hex string (SHA-512 output)
    fun sha64From512(sha512Hex: String): String {
        if (sha512Hex.length != 128) return "Invalid SHA512 Input" // Or throw exception
        return try {
            val chunks = sha512Hex.chunked(16).map { BigInteger(it, 16) }
            // Simple folding: XOR or ADD chunks. Using ADD here.
            val folded = chunks.reduce { acc, next -> acc.add(next) }
            // Take lower 64 bits, format as 16 hex chars
            folded.toString(16).takeLast(16).padStart(16, '0').uppercase(Locale.ROOT)
        } catch (e: Exception){
            "Error folding hash" // Handle potential BigInteger parsing errors
        }
    }

    // Extension function for consistent hex formatting
    private fun ByteArray.toHexString(): String =
        BigInteger(1, this).toString(16).padStart(this.size * 2, '0').uppercase(Locale.ROOT)
}