# ─────────────────────────────────────────────────────────────
# ATRIBUTOS
# ─────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations

# ─────────────────────────────────────────────────────────────
# OFUSCACIÓN
# ─────────────────────────────────────────────────────────────
-dontusemixedcaseclassnames
-overloadaggressively
-repackageclasses 'a'
-allowaccessmodification

# ─────────────────────────────────────────────────────────────
# ACTIVIDADES
# ─────────────────────────────────────────────────────────────
-keep class movile.health_app.MainActivity { *; }
-keep class movile.health_app.LoginActivity { *; }

# ─────────────────────────────────────────────────────────────
# SECURITYCHECK — es un object Kotlin, no una clase estática
# ─────────────────────────────────────────────────────────────
-keep class movile.health_app.SecurityCheck { *; }

# ─────────────────────────────────────────────────────────────
# ELIMINAR LOGS EN RELEASE
# ─────────────────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# ─────────────────────────────────────────────────────────────
# OPTIMIZACIÓN
# ─────────────────────────────────────────────────────────────
-optimizationpasses 5
-verbose