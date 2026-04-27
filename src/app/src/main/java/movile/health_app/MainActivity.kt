package movile.health_app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import movile.health_app.ui.theme.Health_AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow
import kotlin.system.exitProcess

// ── Comprobaciones de seguridad ───────────────────────────────────────────────
object SecurityCheck {

    // ── 1. Detección de modo debug ────────────────────────────────────────────
    /**
     * Devuelve true si la app está firmada o ejecutándose en modo debug.
     *
     * Combina tres métodos:
     *  a) Flag ApplicationInfo.FLAG_DEBUGGABLE del manifiesto.
     *  b) BuildConfig.DEBUG (valor de compilación).
     *  c) Debug.isDebuggerConnected(): detecta un depurador activo en tiempo real.
     */
    fun isDebugEnvironment(context: android.content.Context): Boolean {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val hasDebugger  = Debug.isDebuggerConnected()
        return isDebuggable || hasDebugger
    }

    // ── 2. Detección de root ──────────────────────────────────────────────────
    /**
     * Devuelve true si se detectan indicios de root en el dispositivo.
     *
     * Combina cuatro métodos:
     *  a) Presencia del binario `su` en rutas del sistema.
     *  b) Paquetes de gestión de root conocidos instalados (Magisk, SuperSU, KingRoot…).
     *  c) Build tags "test-keys" (imagen de sistema no oficial).
     *  d) Escritura en /system (partición normalmente de sólo lectura).
     */
    fun isRootedDevice(context: android.content.Context): Boolean =
        hasSuBinary() || hasRootPackages(context) || hasTestKeys() || canWriteSystem()

    // a) Rutas habituales del binario su
    private fun hasSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/dev/com.koushikdutta.superuser.daemon/"
        )
        return paths.any { File(it).exists() }
    }

    // b) Paquetes asociados a root/jailbreak
    private fun hasRootPackages(context: android.content.Context): Boolean {
        val rootPackages = listOf(
            "com.topjohnwu.magisk",          // Magisk
            "eu.chainfire.supersu",           // SuperSU
            "com.noshufou.android.su",        // Superuser (ChainsDD)
            "com.koushikdutta.superuser",     // Superuser (Koush)
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",          // KingRoot
            "com.kingo.root",                 // KingoRoot
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
        val pm = context.packageManager
        return rootPackages.any { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
    }

    // c) Build tags de imagen no oficial
    private fun hasTestKeys(): Boolean =
        android.os.Build.TAGS?.contains("test-keys") == true

    // d) Intenta escribir en /system (solo root puede hacerlo)
    private fun canWriteSystem(): Boolean =
        runCatching { File("/system/test_root_write").createNewFile() }.getOrDefault(false)
}

// ── Modelo de datos ──────────────────────────────────────────────────────────
data class HealthData(
    val weight: Float = 0f,
    val height: Float = 0f,
) {
    val imc: Float
        get() {
            val heightM = height / 100f
            return if (heightM > 0f) weight / heightM.pow(2) else 0f
        }
}

// ── Actividad principal ───────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val isDebug = SecurityCheck.isDebugEnvironment(this)
            val isRooted = SecurityCheck.isRootedDevice(this)

            if (isDebug || isRooted) {
                val reason = if (isDebug) "debug" else "root"
                blockApp(reason)
                return
            }

            enableEdgeToEdge()

            val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val jwtToken = sharedPrefs.getString("jwt", "") ?: ""

            setContent {
                Health_AppTheme {
                    HealthDashboardScreen(jwtToken = jwtToken)
                }
            }
        }

        // ✅ Método de instancia dentro de la clase — this y finishAffinity() disponibles
        private fun blockApp(reason: String) {
            val (title, message) = when (reason) {
                "debug" -> "Entorno no permitido" to
                        "Esta aplicación no puede ejecutarse en modo de depuración."

                else -> "Dispositivo no compatible" to
                        "Se han detectado privilegios de superusuario (root). " +
                        "Esta aplicación no puede ejecutarse en un dispositivo rooteado."
            }

            android.app.AlertDialog.Builder(this)           // ✅ this = MainActivity
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Cerrar") { _, _ ->
                    finishAffinity()                        // ✅ método de ComponentActivity
                    exitProcess(0)
                }
                .show()
        }
    }
// ── Pantalla principal ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDashboardScreen(jwtToken: String) {
    val scope = rememberCoroutineScope()
    var healthData by remember { mutableStateOf<HealthData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    var showInsertDialog by remember { mutableStateOf(false) }
    var insertPeso by remember { mutableStateOf("") }
    var insertAltura by remember { mutableStateOf("") }
    var insertLoading by remember { mutableStateOf(false) }
    var insertError by remember { mutableStateOf<String?>(null) }
    var insertSuccess by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun loadData() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                healthData = fetchHealthData(jwtToken)
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión", fontWeight = FontWeight.Bold) },
            text  = { Text("¿Estás seguro de que quieres cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().remove("jwt").apply()
                    context.startActivity(
                        Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }) {
                    Text("Cerrar sesión", color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = Color(0xFF1976D2))
                }
            }
        )
    }

    if (showInsertDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!insertLoading) {
                    showInsertDialog = false
                    insertPeso = ""
                    insertAltura = ""
                    insertError = null
                    insertSuccess = false
                }
            },
            title = { Text("Registrar datos", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = insertPeso,
                        onValueChange = { insertPeso = it },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !insertLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = insertAltura,
                        onValueChange = { insertAltura = it },
                        label = { Text("Altura (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !insertLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    when {
                        insertLoading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1976D2)
                                )
                                Text("Guardando...", fontSize = 13.sp, color = Color(0xFF757575))
                            }
                        }
                        insertSuccess -> {
                            Text(
                                "✓ Datos guardados correctamente",
                                color = Color(0xFF388E3C),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        insertError != null -> {
                            Text(
                                "✗ ${insertError}",
                                color = Color(0xFFE53935),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !insertLoading && insertPeso.isNotBlank() && insertAltura.isNotBlank(),
                    onClick = {
                        val peso = insertPeso.trim().toFloatOrNull()
                        val altura = insertAltura.trim().toFloatOrNull()
                        if (peso == null || altura == null) {
                            insertError = "Introduce valores numéricos válidos."
                            return@TextButton
                        }
                        insertLoading = true
                        insertError = null
                        insertSuccess = false
                        scope.launch {
                            try {
                                insertHealthData(jwtToken, peso, altura)
                                insertSuccess = true
                                insertPeso = ""
                                insertAltura = ""
                                loadData()
                            } catch (e: Exception) {
                                insertError = e.message ?: "Error desconocido"
                            } finally {
                                insertLoading = false
                            }
                        }
                    }
                ) {
                    Text("Guardar", color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !insertLoading,
                    onClick = {
                        showInsertDialog = false
                        insertPeso = ""
                        insertAltura = ""
                        insertError = null
                        insertSuccess = false
                    }
                ) {
                    Text("Cancelar", color = Color(0xFF757575))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Salud", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                actions = {
                    IconButton(onClick = {
                        insertError = null
                        insertSuccess = false
                        showInsertDialog = true
                    }) {
                        Icon(Icons.Filled.Add, "Registrar datos", tint = Color.White)
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar sesión", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF5F5F5))
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF1976D2)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No se pudieron cargar los datos.",
                            color = Color(0xFF757575),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Comprueba tu conexión e inténtalo de nuevo.",
                            color = Color(0xFFBDBDBD),
                            fontSize = 12.sp
                        )
                    }
                }
                healthData != null -> {
                    HealthContent(healthData!!)
                }
            }
        }
    }
}

// ── Contenido ─────────────────────────────────────────────────────────────────
@Composable
fun HealthContent(data: HealthData) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), label = "Peso",   value = "${"%.1f".format(data.weight)} kg", color = Color(0xFF64B5F6))
            StatCard(modifier = Modifier.weight(1f), label = "Altura", value = "${"%.1f".format(data.height)} cm", color = Color(0xFFE57373))
            StatCard(modifier = Modifier.weight(1f), label = "IMC",    value = "${"%.1f".format(data.imc)}",       color = Color(0xFFFFD54F))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Resumen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF212121))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = Color(0xFF64B5F6), label = "Peso")
                    Spacer(modifier = Modifier.width(16.dp))
                    LegendItem(color = Color(0xFFE57373), label = "Altura")
                    Spacer(modifier = Modifier.width(16.dp))
                    LegendItem(color = Color(0xFFFFD54F), label = "IMC")
                }
                Spacer(modifier = Modifier.height(12.dp))
                HealthBarChart(peso = data.weight, altura = data.height, imc = data.imc)
            }
        }
    }
}

// ── Leyenda ───────────────────────────────────────────────────────────────────
@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp).background(color = color, shape = RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 12.sp, color = Color(0xFF757575))
    }
}

// ── Gráfica de barras ─────────────────────────────────────────────────────────
@Composable
fun HealthBarChart(peso: Float, altura: Float, imc: Float) {
    val bars = listOf(
        Triple("Peso\n(kg)",   peso,   Color(0xFF64B5F6)),
        Triple("Altura\n(cm)", altura, Color(0xFFE57373)),
        Triple("IMC",          imc,    Color(0xFFFFD54F)),
    )
    val maxValue = bars.maxOf { it.second }.takeIf { it > 0f } ?: 1f

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val canvasW       = size.width
            val canvasH       = size.height
            val paddingBottom = 8f
            val paddingTop    = 16f
            val chartH        = canvasH - paddingBottom - paddingTop
            val steps         = 6

            for (i in 0..steps) {
                val y = paddingTop + chartH - (chartH / steps) * i
                drawLine(color = Color(0xFFE0E0E0), start = Offset(0f, y), end = Offset(canvasW, y), strokeWidth = 1f)
            }

            val barWidth = canvasW / (bars.size * 2f)
            val spacing  = barWidth / 2f

            bars.forEachIndexed { index, (_, value, color) ->
                val barH = (value / maxValue) * chartH
                val x    = spacing + index * (barWidth + spacing * 2)
                drawRoundRect(
                    color        = color,
                    topLeft      = Offset(x, paddingTop + chartH - barH),
                    size         = Size(barWidth, barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bars.forEach { (label, value, _) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${"%.1f".format(value)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
                    Text(text = label, fontSize = 10.sp, color = Color(0xFF757575), lineHeight = 13.sp)
                }
            }
        }
    }
}

// ── Tarjeta de estadística ────────────────────────────────────────────────────
@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 11.sp, color = Color(0xFF757575))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        }
    }
}

// ── GET: obtener datos de salud ───────────────────────────────────────────────
suspend fun fetchHealthData(jwtToken: String): HealthData = withContext(Dispatchers.IO) {
    if (jwtToken.isBlank()) throw Exception("Token vacío")

    val connection = (URL("http://10.0.2.2/api/datos_usuario.php").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout    = 10_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cookie", "jwt=$jwtToken")
    }

    when (val code = connection.responseCode) {
        HttpURLConnection.HTTP_OK -> {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            connection.disconnect()
            val trimmed = body.trim()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) throw Exception("Respuesta inesperada del servidor")
            parseHealthData(body)
        }
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_MOVED_PERM -> { connection.disconnect(); throw Exception("Redirección inesperada") }
        else -> { connection.disconnect(); throw Exception("Error HTTP: $code") }
    }
}

// ── POST: insertar datos de salud ─────────────────────────────────────────────
suspend fun insertHealthData(jwtToken: String, peso: Float, altura: Float): Unit = withContext(Dispatchers.IO) {
    if (jwtToken.isBlank()) throw Exception("Token vacío")

    val body = JSONObject().apply {
        put("peso", peso)
        put("altura", altura)
    }.toString().toByteArray(Charsets.UTF_8)

    val connection = (URL("http://10.0.2.2/api/insertData.php").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout    = 10_000
        instanceFollowRedirects = false
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cookie", "jwt=$jwtToken")
    }

    connection.outputStream.use { it.write(body) }

    when (val code = connection.responseCode) {
        HttpURLConnection.HTTP_OK -> {
            connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            connection.disconnect()
        }
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_MOVED_PERM -> { connection.disconnect(); throw Exception("Redirección inesperada (¿token caducado?)") }
        else -> { connection.disconnect(); throw Exception("Error HTTP: $code") }
    }
}

// ── Parser JSON → HealthData ──────────────────────────────────────────────────
fun parseHealthData(json: String): HealthData {
    val array = JSONArray(json)
    if (array.length() == 0) return HealthData()
    val obj = array.getJSONObject(0)
    return HealthData(
        weight = obj.optDouble("peso",   0.0).toFloat(),
        height = obj.optDouble("altura", 0.0).toFloat(),
    )
}