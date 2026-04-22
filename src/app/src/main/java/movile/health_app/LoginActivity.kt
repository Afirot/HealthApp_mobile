package movile.health_app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

private const val LOGIN_API_URL    = "http://10.0.2.2/api/login.php"
private const val REGISTER_API_URL = "http://10.0.2.2/api/registro.php"

// Paleta extraida del CSS original
private val GreenLight  = Color(0xFF2EBF91)
private val GreenDark   = Color(0xFF1E9F75)
private val White       = Color(0xFFFFFFFF)
private val TextDark    = Color(0xFF333333)
private val TextSecond  = Color(0xFF666666)
private val BorderColor = Color(0xFFCCCCCC)
private val ErrorBg     = Color(0xFFFFEEEE)
private val ErrorText   = Color(0xFFCC3333)
private val SuccessBg   = Color(0xFFEEFFF6)
private val SuccessText = Color(0xFF1E9F75)

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
            var showRegister by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                // Panel de Login (siempre presente)
                AnimatedVisibility(
                    visible = !showRegister,
                    enter   = slideInHorizontally { -it },
                    exit    = slideOutHorizontally { -it }
                ) {
                    LoginScreen(
                        onLoginSuccess = { jwt ->
                            prefs.edit().putString("jwt", jwt).apply()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        },
                        onGoToRegister = { showRegister = true }
                    )
                }

                // Panel de Registro (se desliza desde la derecha)
                AnimatedVisibility(
                    visible = showRegister,
                    enter   = slideInHorizontally { it },
                    exit    = slideOutHorizontally { it }
                ) {
                    RegisterScreen(
                        onRegisterSuccess = { showRegister = false },
                        onGoToLogin       = { showRegister = false }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// PANTALLA DE LOGIN
// ─────────────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onGoToRegister: () -> Unit
) {
    var username    by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var userError   by remember { mutableStateOf<String?>(null) }
    var passError   by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scope        = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors = listOf(GreenLight, GreenDark))),
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
                Text(
                    text       = "movile.health",
                    color      = GreenDark,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .background(GreenLight, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = "Inicia sesión",
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
                        focusedBorderColor      = GreenLight,
                        unfocusedBorderColor    = BorderColor,
                        cursorColor             = GreenLight
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(8.dp))

                // Campo contraseña
                Text(
                    text       = "Contraseña",
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

                // Botón iniciar sesión
                Button(
                    onClick = {
                        var valid = true
                        if (username.isBlank()) { userError = "Introduce tu usuario"; valid = false }
                        if (password.isBlank()) { passError = "Introduce tu contraseña"; valid = false }
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
                                            json.has("error") -> errorMsg = mapLoginError(json.getString("error"))
                                            else              -> errorMsg = "Respuesta inesperada."
                                        }
                                    } catch (ex: Exception) {
                                        errorMsg = "Error al procesar la respuesta."
                                    }
                                },
                                onFailure = { errorMsg = "No se pudo conectar. Revisa tu conexión." }
                            )
                        }
                    },
                    enabled  = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape  = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GreenLight,
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
                            text       = "Iniciar sesión",
                            color      = White,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Enlace a registro
                TextButton(onClick = onGoToRegister) {
                    Text(
                        text     = "¿No tienes cuenta? Regístrate",
                        color    = GreenDark,
                        fontSize = 13.sp
                    )
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

// ─────────────────────────────────────────────
// PANTALLA DE REGISTRO
// ─────────────────────────────────────────────
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onGoToLogin:       () -> Unit
) {
    var usuario         by remember { mutableStateOf("") }
    var nombre          by remember { mutableStateOf("") }
    var apellidos       by remember { mutableStateOf("") }
    var fechaNacimiento by remember { mutableStateOf("") }
    var pass            by remember { mutableStateOf("") }
    var pass2           by remember { mutableStateOf("") }
    var passVisible     by remember { mutableStateOf(false) }
    var pass2Visible    by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }
    var successMsg      by remember { mutableStateOf<String?>(null) }

    // Errores por campo
    var usuarioError  by remember { mutableStateOf<String?>(null) }
    var nombreError   by remember { mutableStateOf<String?>(null) }
    var apellidosError by remember { mutableStateOf<String?>(null) }
    var fechaError    by remember { mutableStateOf<String?>(null) }
    var passError     by remember { mutableStateOf<String?>(null) }
    var pass2Error    by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scope        = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors = listOf(GreenLight, GreenDark))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            shape     = RoundedCornerShape(12.dp),
            colors    = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text       = "movile.health",
                    color      = GreenDark,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .background(GreenLight, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = "Crear cuenta",
                    color      = GreenDark,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                // ── Usuario ──
                RegisterField(
                    label       = "Usuario",
                    value       = usuario,
                    placeholder = "ej: juan123",
                    error       = usuarioError,
                    imeAction   = ImeAction.Next,
                    onValueChange = { usuario = it; usuarioError = null; errorMsg = null },
                    onNext        = { focusManager.moveFocus(FocusDirection.Down) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Nombre ──
                RegisterField(
                    label       = "Nombre",
                    value       = nombre,
                    placeholder = "ej: Juan",
                    error       = nombreError,
                    imeAction   = ImeAction.Next,
                    onValueChange = { nombre = it; nombreError = null; errorMsg = null },
                    onNext        = { focusManager.moveFocus(FocusDirection.Down) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Apellidos ──
                RegisterField(
                    label       = "Apellidos",
                    value       = apellidos,
                    placeholder = "ej: García López",
                    error       = apellidosError,
                    imeAction   = ImeAction.Next,
                    onValueChange = { apellidos = it; apellidosError = null; errorMsg = null },
                    onNext        = { focusManager.moveFocus(FocusDirection.Down) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Fecha de nacimiento ──
                RegisterField(
                    label       = "Fecha de nacimiento",
                    value       = fechaNacimiento,
                    placeholder = "AAAA-MM-DD",
                    error       = fechaError,
                    imeAction   = ImeAction.Next,
                    onValueChange = { fechaNacimiento = it; fechaError = null; errorMsg = null },
                    onNext        = { focusManager.moveFocus(FocusDirection.Down) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Contraseña ──
                Text(
                    text       = "Contraseña",
                    color      = TextDark,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value           = pass,
                    onValueChange   = { pass = it; passError = null; errorMsg = null },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    placeholder     = { Text("••••••••••••••••", color = TextSecond) },
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
                    supportingText  = passError?.let { msg -> { Text(msg, color = ErrorText, fontSize = 11.sp) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = registerFieldColors(),
                    shape  = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text     = "Mínimo 15 caracteres, una mayúscula, un número y un símbolo.",
                    color    = TextSecond,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // ── Confirmar contraseña ──
                Text(
                    text       = "Confirmar contraseña",
                    color      = TextDark,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value           = pass2,
                    onValueChange   = { pass2 = it; pass2Error = null; errorMsg = null },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    placeholder     = { Text("••••••••••••••••", color = TextSecond) },
                    leadingIcon     = { Icon(Icons.Filled.Lock, null, tint = GreenLight) },
                    trailingIcon    = {
                        TextButton(onClick = { pass2Visible = !pass2Visible }) {
                            Text(
                                text     = if (pass2Visible) "Ocultar" else "Mostrar",
                                color    = GreenLight,
                                fontSize = 12.sp
                            )
                        }
                    },
                    visualTransformation = if (pass2Visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    isError         = pass2Error != null,
                    supportingText  = pass2Error?.let { msg -> { Text(msg, color = ErrorText) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = registerFieldColors(),
                    shape  = RoundedCornerShape(6.dp)
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

                // Mensaje de éxito
                AnimatedVisibility(visible = successMsg != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color    = SuccessBg,
                        shape    = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text     = successMsg ?: "",
                            color    = SuccessText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Botón registrarse
                Button(
                    onClick = {
                        var valid = true
                        if (usuario.isBlank())         { usuarioError   = "Introduce tu usuario"; valid = false }
                        if (nombre.isBlank())           { nombreError    = "Introduce tu nombre"; valid = false }
                        if (apellidos.isBlank())        { apellidosError = "Introduce tus apellidos"; valid = false }
                        if (fechaNacimiento.isBlank())  { fechaError     = "Introduce tu fecha de nacimiento"; valid = false }
                        if (pass.isBlank())             { passError      = "Introduce una contraseña"; valid = false }
                        if (pass2.isBlank())            { pass2Error     = "Confirma tu contraseña"; valid = false }
                        if (pass.isNotBlank() && pass2.isNotBlank() && pass != pass2) {
                            pass2Error = "Las contraseñas no coinciden"; valid = false
                        }
                        if (!valid) return@Button

                        isLoading  = true
                        errorMsg   = null
                        successMsg = null

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                callRegisterApi(
                                    usuario         = usuario.trim(),
                                    nombre          = nombre.trim(),
                                    apellidos       = apellidos.trim(),
                                    fechaNacimiento = fechaNacimiento.trim(),
                                    pass            = pass,
                                    pass2           = pass2
                                )
                            }
                            isLoading = false
                            result.fold(
                                onSuccess = { body ->
                                    try {
                                        val json = JSONObject(body)
                                        when {
                                            json.has("error") ->
                                                errorMsg = mapRegisterError(json.getString("error"))
                                            else -> {
                                                successMsg = "¡Cuenta creada! Ya puedes iniciar sesión."
                                                // Volver al login tras un breve delay
                                                kotlinx.coroutines.delay(1500)
                                                onRegisterSuccess()
                                            }
                                        }
                                    } catch (ex: Exception) {
                                        errorMsg = "Error al procesar la respuesta."
                                    }
                                },
                                onFailure = { errorMsg = "No se pudo conectar. Revisa tu conexión." }
                            )
                        }
                    },
                    enabled  = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape  = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GreenLight,
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
                            text       = "Crear cuenta",
                            color      = White,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Volver al login
                TextButton(onClick = onGoToLogin) {
                    Text(
                        text     = "¿Ya tienes cuenta? Inicia sesión",
                        color    = GreenDark,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Componente auxiliar: campo de texto genérico
// ─────────────────────────────────────────────
@Composable
private fun RegisterField(
    label:         String,
    value:         String,
    placeholder:   String,
    error:         String?,
    imeAction:     ImeAction,
    onValueChange: (String) -> Unit,
    onNext:        () -> Unit
) {
    Text(
        text       = label,
        color      = TextDark,
        fontSize   = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        modifier        = Modifier.fillMaxWidth(),
        singleLine      = true,
        placeholder     = { Text(placeholder, color = TextSecond) },
        isError         = error != null,
        supportingText  = error?.let { msg -> { Text(msg, color = ErrorText) } },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        colors          = registerFieldColors(),
        shape           = RoundedCornerShape(6.dp)
    )
}

@Composable
private fun registerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor        = TextDark,
    unfocusedTextColor      = TextDark,
    focusedContainerColor   = White,
    unfocusedContainerColor = White,
    focusedBorderColor      = GreenLight,
    unfocusedBorderColor    = BorderColor,
    cursorColor             = GreenLight
)

// ─────────────────────────────────────────────
// Llamadas a las APIs
// ─────────────────────────────────────────────
private fun callLoginApi(username: String, password: String): Result<String> = try {
    val connection = (URL(LOGIN_API_URL).openConnection() as HttpURLConnection).apply {
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

private fun callRegisterApi(
    usuario:         String,
    nombre:          String,
    apellidos:       String,
    fechaNacimiento: String,
    pass:            String,
    pass2:           String
): Result<String> = try {
    val connection = (URL(REGISTER_API_URL).openConnection() as HttpURLConnection).apply {
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
                put("usuario", usuario)
                put("nombre", nombre)
                put("apellidos", apellidos)
                put("fechaNacimiento", fechaNacimiento)
                put("pass", pass)
                put("pass2", pass2)
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

// ─────────────────────────────────────────────
// Mapeado de errores
// ─────────────────────────────────────────────
private fun mapLoginError(apiMessage: String) = when (apiMessage) {
    "Invalid Credentials"  -> "Usuario o contraseña incorrectos."
    "Something whet wrong" -> "Error interno del servidor."
    else                   -> "Error: $apiMessage"
}

private fun mapRegisterError(apiMessage: String) = when (apiMessage) {
    "Username invalido"    -> "El usuario solo puede contener letras y números (2–32 caracteres)."
    "Nombre inválido"      -> "El nombre solo puede contener letras (2–32 caracteres)."
    "Apellido inválido"    -> "Los apellidos solo pueden contener letras y espacios (2–32 caracteres)."
    "Fecha inválida"       -> "La fecha debe tener el formato AAAA-MM-DD."
    "Contraseña inválida"  -> "La contraseña debe tener al menos 15 caracteres, una mayúscula, un número y un símbolo."
    "Something whet wrong" -> "Error interno del servidor. Inténtalo de nuevo."
    else                   -> "Error: $apiMessage"
}