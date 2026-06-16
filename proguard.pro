# ═══════════════════════════════════════════════════════════════════
#  FRewards — ProGuard Configuration
#  Target: Paper 1.21.4 / Java 17
# ═══════════════════════════════════════════════════════════════════

# ── Diccionario de ofuscación ────────────────────────────────────────
-obfuscationdictionary      proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# ── Optimización ─────────────────────────────────────────────────────
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'a'

# ── Librerías externas (no ofuscar) ──────────────────────────────────
-dontwarn org.bukkit.**
-dontwarn io.papermc.**
-dontwarn me.clip.**
-dontwarn net.kyori.**
-dontwarn net.md_5.**
-dontwarn com.google.**
-dontwarn org.jetbrains.**

# ── Atributos a conservar ─────────────────────────────────────────────
# Necesarios para que Bukkit resuelva anotaciones y firmas genéricas
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Clase principal ───────────────────────────────────────────────────
# plugin.yml referencia "me.fRewards.main.Main" literalmente — no renombrar
-keep class me.fRewards.main.Main {
    public void onEnable();
    public void onDisable();
    public static me.fRewards.main.Main getInstance();
}

# ── Sistema de licencias ──────────────────────────────────────────────
# El paquete se ofusca pero las clases internas de la respuesta deben
# conservar sus métodos para que Gson pueda deserializar correctamente
-keep class me.fRewards.license.LicenseManager$LicenseInfo    { *; }
-keep class me.fRewards.license.LicenseManager$LicenseResponse { *; }
-dontwarn javax.crypto.**

# ── Listeners ─────────────────────────────────────────────────────────
# Bukkit invoca los métodos @EventHandler por reflexión → conservar firma
-keep class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}

# ── Ejecutores de comandos ────────────────────────────────────────────
-keep class * implements org.bukkit.command.CommandExecutor {
    public boolean onCommand(org.bukkit.command.CommandSender,
                             org.bukkit.command.Command,
                             java.lang.String,
                             java.lang.String[]);
}

# ── Tab completers ────────────────────────────────────────────────────
-keep class * implements org.bukkit.command.TabCompleter {
    public java.util.List onTabComplete(org.bukkit.command.CommandSender,
                                        org.bukkit.command.Command,
                                        java.lang.String,
                                        java.lang.String[]);
}

# ── PlaceholderAPI ────────────────────────────────────────────────────
# PlaceholderAPI registra la expansión por reflexión y llama sus métodos
-keep class * extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
    public java.lang.String getIdentifier();
    public java.lang.String getAuthor();
    public java.lang.String getVersion();
    public java.lang.String onPlaceholderRequest(org.bukkit.entity.Player, java.lang.String);
}

# ── Enums ─────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
    public final int ordinal();
}

# ── Records (Java 16+) ────────────────────────────────────────────────
# Los métodos de acceso de records se usan internamente — ProGuard los
# rastrea solo si los renombra de forma consistente (lo hace), pero
# conservamos el descriptor para seguridad
-keepclassmembers class * {
    !static final ** $assertionsDisabled;
}

# ── Clases anónimas e internas ────────────────────────────────────────
-keepclassmembernames class * {
    java.lang.Class class$(java.lang.String);
    java.lang.Class class$(java.lang.String, boolean);
}

# ── Serialización Bukkit ──────────────────────────────────────────────
# ItemStack.serializeAsBytes() / deserializeBytes() no necesitan keeps
# adicionales — son métodos de la API pública de Paper (provided)

# ── Suprimir advertencias de módulos Java 17 ─────────────────────────
-ignorewarnings
