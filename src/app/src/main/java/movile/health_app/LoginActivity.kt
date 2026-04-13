package movile.health_app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val API_URL = "https://tu-servidor.com/api/login.php"

// Paleta extraida del CSS original
private val GreenLight  = Color(0xFF2EBF91)  // #2ebf91 - acento principal
private val GreenDark   = Color(0xFF1E9F75)  // #1e9f75 - acento oscuro
private val White       = Color(0xFFFFFFFF)
private val TextDark    = Color(0xFF333333)  // labels
private val TextSecond  = Color(0xFF666666)
private val BorderColor = Color(0xFFCCCCCC)  // #ccc inputs sin foco
private val ErrorBg     = Color(0xFFFFEEEE)
private val ErrorText   = Color(0xFFCC3333)

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getString("jwt", null) != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            LoginScreen(onLoginSuccess = { jwt ->
                prefs.edit().putString("jwt", jwt).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            })
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username    by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var userError   by remember { mutableStateOf<String?>(null) }
    var passError   by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scope        = rememberCoroutineScope()

    // Fondo: linear-gradient(135deg, #2ebf91, #1e9f75)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(GreenLight, GreenDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape     = RoundedCornerShape(12.dp),
            colors    = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Titulo: color #1e9f75
                Text(
                    text       = "movile.health",
                    color      = GreenDark,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Center
                )

                // Linea decorativa bajo el titulo (simula el ::after del CSS)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .background(GreenLight, RoundedCornerShape(2.dp))
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text       = "Inicia sesion",
                    color      = GreenDark,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Campo usuario
                Text(
                    text       = "Usuario",
                    color      = TextDark,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value           = username,
                    onValueChange   = { username = it; userError = null; errorMsg = null },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    placeholder     = { Text("nombre de usuario", color = TextSecond) },
                    leadingIcon     = { Icon(Icons.Filled.Person, null, tint = GreenLight) },
                    isError         = userError != null,
                    supportingText  = userError?.let { msg -> { Text(msg, color = ErrorText) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor        = TextDark,
                        unfocusedTextColor      = TextDark,
                        focusedContainerColor   = White,
                        unfocusedContainerColor = White,
                        focusedBorderColor      = GreenLight,   // #2ebf91 al enfocar
                        unfocusedBorderColor    = BorderColor,  // #ccc sin foco
                        cursorColor             = GreenLight
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(8.dp))

                // Campo contrasena
                Text(
                    text       = "Contrasena",
                    color      = TextDark,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value           = password,
                    onValueChange   = { password = it; passError = null; errorMsg = null },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    placeholder     = { Text("••••••••", color = TextSecond) },
                    leadingIcon     = { Icon(Icons.Filled.Lock, null, tint = GreenLight) },
                    trailingIcon    = {
                        TextButton(onClick = { passVisible = !passVisible }) {
                            Text(
                                text     = if (passVisible) "Ocultar" else "Mostrar",
                                color    = GreenLight,
                                fontSize = 12.sp
                            )
                        }
                    },
                    visualTransformation = if (passVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    isError         = passError != null,
                    supportingText  = passError?.let { msg -> { Text(msg, color = ErrorText) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor        = TextDark,
                        unfocusedTextColor      = TextDark,
                        focusedContainerColor   = White,
                        unfocusedContainerColor = White,
                        focusedBorderColor      = GreenLight,
                        unfocusedBorderColor    = BorderColor,
                        cursorColor             = GreenLight
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Error global
                AnimatedVisibility(visible = errorMsg != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color    = ErrorBg,
                        shape    = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text     = errorMsg ?: "",
                            color    = ErrorText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Boton: background #2ebf91, texto blanco
                Button(
                    onClick = {
                        var valid = true
                        if (username.isBlank()) { userError = "Introduce tu usuario"; valid = false }
                        if (password.isBlank()) { passError = "Introduce tu contrasena"; valid = false }
                        if (!valid) return@Button

                        isLoading = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                callLoginApi(username.trim(), password)
                            }
                            isLoading = false
                            result.fold(
                                onSuccess = { body ->
                                    try {
                                        val json = JSONObject(body)
                                        when {
                                            json.has("jwt")   -> onLoginSuccess(json.getString("jwt"))
                                            json.has("error") -> errorMsg = mapError(json.getString("error"))
                                            else              -> errorMsg = "Respuesta inesperada."
                                        }
                                    } catch (ex: Exception) {
                                        errorMsg = "Error al procesar la respuesta."
                                    }
                                },
                                onFailure = {
                                    errorMsg = "No se pudo conectar. Revisa tu conexion."
                                }
                            )
                        }
                    },
                    enabled  = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape  = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GreenLight,   // #2ebf91
                        disabledContainerColor = GreenLight.copy(alpha = 0.6f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color       = White,
                            strokeWidth = 2.dp,
                            modifier    = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text         = "Iniciar sesion",
                            color        = White,
                            fontSize     = 15.sp,
                            fontWeight   = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Text(
            text     = "v1.0.0",
            color    = White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

private fun callLoginApi(username: String, password: String): Result<String> = try {
    val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        doOutput       = true
        connectTimeout = 10_000
        readTimeout    = 15_000
    }
    OutputStreamWriter(connection.outputStream, "UTF-8").use {
        it.write(
            JSONObject().apply {
                put("nombre", username)
                put("pass", password)
            }.toString()
        )
    }
    val stream   = if (connection.responseCode in 200..299) connection.inputStream
    else connection.errorStream
    val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    connection.disconnect()
    Result.success(response)
} catch (e: Exception) {
    Result.failure(e)
}

private fun mapError(apiMessage: String) = when (apiMessage) {
    "Invalid Credentials"  -> "Usuario o contrasena incorrectos."
    "Something whet wrong" -> "Error interno del servidor."
    else                   -> "Error: $apiMessage"
}
