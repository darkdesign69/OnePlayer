package app.marlboroadvance.mpvex.ui.browser.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom

fun hashPrivacyPin(
  salt: String,
  pin: String,
): String {
  val bytes = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray())
  return bytes.joinToString("") { "%02x".format(it) }
}

fun newPrivacySalt(): String {
  val bytes = ByteArray(16)
  SecureRandom().nextBytes(bytes)
  return bytes.joinToString("") { "%02x".format(it) }
}

@Composable
fun PinField(
  value: String,
  label: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = { text ->
      if (text.length <= 8 && text.all { it.isDigit() }) onValueChange(text)
    },
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    modifier = modifier.fillMaxWidth(),
  )
}

/** Asks for a new PIN (twice). Used the first time someone hides a video. */
@Composable
fun CreatePinDialog(
  onDismiss: () -> Unit,
  onPinCreated: (String) -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }
  val mismatch = confirm.isNotEmpty() && pin != confirm

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create a PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Hidden videos can only be opened from Privacy, after you enter this PIN. Remember it: it can't be reset without clearing the app's data.",
          style = MaterialTheme.typography.bodyMedium,
        )
        PinField(value = pin, label = "PIN (4 to 8 digits)", onValueChange = { pin = it })
        PinField(value = confirm, label = "Confirm PIN", onValueChange = { confirm = it })
        if (mismatch) {
          Text(
            text = "The PINs don't match",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onPinCreated(pin) }, enabled = pin.length >= 4 && pin == confirm) {
        Text("Create PIN")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
