# NFC KeyBlock

Bloqueo de aplicaciones en Android accionado por una tarjeta NFC física. Acercas la
tarjeta, entras en modo concentración; la acercas otra vez, sales.

Proyecto Android nativo (Kotlin + Compose + Material 3), sin servidores, sin cuentas
y sin root.

---

## Estado del proyecto

Lo que está implementado y es funcional:

- Lectura de tarjetas NFC en primer plano (Reader Mode) y con la app cerrada (dispatch NDEF/TECH).
- Registro de tarjetas con huella HMAC respaldada por Android Keystore.
- Escritura opcional de token NDEF + AAR para que el sistema abra la app sola.
- Bloqueo real de apps mediante `AccessibilityService` y pantalla de bloqueo propia.
- Perfiles con listas de apps independientes, duración máxima y bloqueo de dominios web.
- Persistencia en Room: el estado sobrevive a cerrar la app, matar el proceso y reiniciar.
- Foreground service con notificación, watchdog de accesibilidad y restauración en el arranque.
- Desbloqueo de emergencia con retardo configurable.
- Estadísticas: tiempo bloqueado, sesiones, racha, intentos por app.
- Tests unitarios de la política de bloqueo y de los casos de uso, más tests de Room.

Lo que **no** está hecho todavía y conviene saberlo antes de empezar:

- No hay asistente de onboarding paso a paso; en su lugar, la pantalla de inicio muestra
  tarjetas de aviso que llevan a cada permiso pendiente.
- No hay exportación/importación de configuración

---

## Compilación rápida

```bash
# Requisitos: JDK 17, Android SDK 35, un teléfono con NFC y Android 8.0+
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest          # tests JVM
./gradlew :app:connectedDebugAndroidTest  # tests de Room en dispositivo
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Después de instalar hay que conceder el servicio de accesibilidad a mano:

```bash
adb shell settings put secure enabled_accessibility_services \
  com.nfckeyblock.debug/com.nfckeyblock.service.BlockingAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

(en un teléfono real es más limpio hacerlo desde Ajustes → Accesibilidad → NFC KeyBlock)

Detalle completo en `docs/PRUEBAS.md`.

