package movile.health_app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.security.MessageDigest

enum class BlockReason { DEBUG, ROOT, EMULATOR, SIGNATURE }

// ═══════════════════════════════════════════════════════════════════════════════
// PANTALLA DE BLOQUEO (compartida por ambas actividades)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SecurityBlockScreen(reason: BlockReason, onExit: () -> Unit) {
    val (title, message) = when (reason) {
        BlockReason.DEBUG     -> "Entorno de depuración detectado" to
                "La aplicación no puede ejecutarse con un depurador activo o en modo debug."
        BlockReason.ROOT      -> "Dispositivo rooteado detectado" to
                "La aplicación no puede ejecutarse en dispositivos con acceso root."
        BlockReason.EMULATOR  -> "Emulador detectado" to
                "La aplicación no puede ejecutarse en un emulador o dispositivo virtual."
        BlockReason.SIGNATURE -> "Firma de la aplicación no válida" to
                "La integridad de la aplicación está comprometida. No se puede continuar."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠",
                fontSize = 56.sp,
                color = Color(0xFFE53935)
            )
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onExit,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text(
                    text = "Cerrar aplicación",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CHECKS DE SEGURIDAD
// ═══════════════════════════════════════════════════════════════════════════════
object SecurityCheck {

    /**
     * Ejecuta todos los checks en orden de menor a mayor coste.
     * Devuelve el primer [BlockReason] encontrado, o null si el entorno es seguro.
     */
    fun evaluate(context: Context): BlockReason? = when {
        isRootedDevice(context)      -> BlockReason.ROOT
        isDebugEnvironment(context)  -> BlockReason.DEBUG
        isEmulator(context)          -> BlockReason.EMULATOR
        isSignatureTampered(context) -> BlockReason.SIGNATURE
        else                         -> null
    }

    fun isDebugEnvironment(context: Context): Boolean = runCatching {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isDebuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        val tracerPid = File("/proc/self/status")
            .bufferedReader()
            .useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }
                    ?.split(":")
                    ?.getOrNull(1)
                    ?.trim()
                    ?.toIntOrNull()
            } ?: 0
        isDebuggable || isDebuggerAttached || tracerPid != 0
    }.getOrDefault(false)

    fun isRootedDevice(context: Context): Boolean = runCatching {
        hasSuBinary() || hasRootPackages(context) || hasTestKeys() || canWriteSystem()
    }.getOrDefault(false)

    private fun hasSuBinary(): Boolean = runCatching {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/dev/com.koushikdutta.superuser.daemon/"
        )
        paths.any { File(it).exists() }
    }.getOrDefault(false)

    private fun hasRootPackages(context: Context): Boolean = runCatching {
        val rootPackages = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu",
            "com.noshufou.android.su", "com.koushikdutta.superuser",
            "com.thirdparty.superuser", "com.yellowes.su",
            "com.kingroot.kinguser", "com.kingo.root",
            "com.smedialink.oneclickroot", "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
        val pm = context.packageManager
        rootPackages.any { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
    }.getOrDefault(false)

    private fun hasTestKeys(): Boolean = runCatching {
        android.os.Build.TAGS?.contains("test-keys") == true
    }.getOrDefault(false)

    private fun canWriteSystem(): Boolean =
        runCatching { File("/system/test_root_write").createNewFile() }.getOrDefault(false)

    private const val EXPECTED_CERT_SHA256 = ""

    fun isSignatureTampered(context: Context): Boolean {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) return false
        if (EXPECTED_CERT_SHA256.isBlank()) return false
        return runCatching {
            getSignatureSha256(context) != EXPECTED_CERT_SHA256
        }.getOrDefault(true)
    }

    @Suppress("DEPRECATION")
    fun getSignatureSha256(context: Context): String {
        val pm = context.packageManager
        val packageName = context.packageName
        val certBytes: ByteArray =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                    ?: throw IllegalStateException("Sin información de firma (API 28+)")
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
                    ?: throw IllegalStateException("Sin información de firma (API < 28)")
            }
        return MessageDigest.getInstance("SHA-256").digest(certBytes)
            .joinToString("") { "%02X".format(it) }
    }

    fun isEmulator(context: Context): Boolean = runCatching {
        hasBuildEmulatorTraces() || hasEmulatorFiles()
    }.getOrDefault(false)

    private fun hasBuildEmulatorTraces(): Boolean = runCatching {
        val fingerprint  = android.os.Build.FINGERPRINT.lowercase()
        val model        = android.os.Build.MODEL.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val brand        = android.os.Build.BRAND.lowercase()
        val device       = android.os.Build.DEVICE.lowercase()
        val product      = android.os.Build.PRODUCT.lowercase()
        val hardware     = android.os.Build.HARDWARE.lowercase()

        fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")
                || model.contains("google_sdk") || model.contains("emulator")
                || model.contains("android sdk built for x86")
                || manufacturer.contains("genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || product == "google_sdk" || product.contains("sdk_gphone")
                || hardware.contains("goldfish") || hardware.contains("ranchu")
    }.getOrDefault(false)

    private fun hasEmulatorFiles(): Boolean = runCatching {
        val emulatorFiles = arrayOf(
            "/dev/socket/qemud", "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so", "/sys/qemu_trace",
            "/system/bin/qemu-props", "/dev/socket/genyd",
            "/dev/socket/baseband_genyd"
        )
        emulatorFiles.any { File(it).exists() }
    }.getOrDefault(false)
}