# Control Mi Stick

App Android que convierte el teléfono en un **control remoto Bluetooth** para el Xiaomi Mi TV Stick (y en general cualquier Android TV / Google TV).

El teléfono se presenta ante el Stick como un dispositivo **HID Bluetooth** (teclado + teclas multimedia), igual que un control físico. No necesita WiFi, ni modo desarrollador, ni instalar nada en el Stick.

## Funciones

- **D-pad con OK**: mantener apretado repite, como el control original.
- **Zona táctil**: deslizar para moverse, tocar para OK, mantener en el borde para repetir.
- Encendido, Buscar, Atrás, Inicio, Menú.
- Volumen +/−, Silenciar, Play/Pausa.
- Multimedia: anterior, retroceder, adelantar, siguiente.
- **Escribir texto** en el Stick desde el teclado del teléfono, con velocidad ajustable.
- **Pegar** lo copiado en el teléfono (útil para contraseñas largas).
- Se mantiene conectado en segundo plano y se reconecta solo al último Stick.

## Requisitos

- Teléfono con **Android 9 o superior** y Bluetooth.
- Algunos fabricantes desactivan el perfil Bluetooth *HID Device*; en ese caso la app lo avisa.

## Instalación

Descargá el APK desde [Releases](../../releases) e instalalo en el teléfono.

## Emparejar con el Stick (una sola vez)

1. En la app, tocá el ícono de Bluetooth → **Hacer visible (2 min)**.
2. En el Stick: **Ajustes → Controles y accesorios → Agregar accesorio**.
3. Elegí el teléfono y aceptá el código en ambos equipos.

Si el teléfono no aparece, en la app usá **Buscar dispositivos** mientras el Stick está en esa pantalla y tocá el Stick.

## Compilar

Requiere JDK 17 y Android SDK (API 34).

```bash
./gradlew assembleDebug     # APK de prueba
./gradlew assembleRelease   # APK release (minificado)
```

Para firmar el release, crear `keystore.properties` en la raíz (no se versiona):

```properties
storeFile=/ruta/a/la/clave.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Sin ese archivo, `assembleRelease` genera un APK sin firmar.

## Cómo funciona

- `hid/HidDescriptor.kt`: descriptor HID con dos reportes: teclado (ID 1) y Consumer Control (ID 2).
- `hid/HidRemoteService.kt`: servicio en primer plano que registra la app con `BluetoothHidDevice`, gestiona la conexión y envía los reportes.
- `hid/KeyMapper.kt`: convierte caracteres a teclas (distribución US).
- `ui/`: interfaz en Jetpack Compose.

## Limitaciones

- El botón de encendido apaga o suspende el Stick, pero es posible que no lo encienda desde suspensión profunda.
- El texto se tipea con distribución de teclado US: las tildes se envían sin acento.
- Algunas apps del Stick descartan letras si llegan muy rápido; por eso la velocidad de tipeo es ajustable (por defecto 300 ms por letra).
