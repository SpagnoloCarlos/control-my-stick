# Reglas R8 para Control Mi Stick.
# La app sólo usa APIs del framework (Bluetooth HID) y Jetpack Compose,
# que ya traen sus propias reglas; no hace falta conservar nada extra.

# Quitar los Log.d / Log.v en release.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
