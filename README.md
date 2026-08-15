# NFC KeyBlock

Bloqueo de aplicaciones en Android accionado por una tarjeta NFC física. Acercas la
tarjeta, entras en modo concentración; la acercas otra vez, sales.

Proyecto Android nativo (Kotlin + Compose + Material 3), sin servidores, sin cuentas
y sin root.

---

## Índice de la documentación

| Documento | Contenido |
|---|---|
| `docs/ARQUITECTURA.md` | Capas, flujo de datos, modelo de datos, componentes Android y por qué cada uno |
| `docs/NFC.md` | UID vs NDEF, dispatch en segundo plano, escritura de token, restricciones reales |
| `docs/BLOQUEO.md` | Cómo se bloquea, resistencia a evasiones y qué evasiones siguen siendo posibles |
| `docs/SEGURIDAD.md` | Modelo de amenazas, clonado de tarjetas, Keystore, qué garantiza y qué no |
| `docs/PLAY_STORE.md` | Políticas de Google Play aplicables y arquitectura alternativa si rechazan la app |
| `docs/PRUEBAS.md` | Compilación e instrucciones para probarlo en un teléfono real con tarjeta |
| `docs/LIMITACIONES.md` | Lista honesta de limitaciones conocidas y mejoras futuras |
| `docs/PRIVACIDAD.md` | Política de privacidad, coherente con lo que hace el código |

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
- No hay exportación/importación de configuración (está en la lista de mejoras).
- **El proyecto no ha sido compilado.** Se ha escrito con el SDK de Android en mente pero
  sin un entorno Android disponible, así que espera tener que resolver algún import o
  alguna versión de dependencia en el primer `assembleDebug`. La lógica de dominio,
  que es la parte con enjundia, sí está cubierta por tests.

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

---

## Nota sobre `targetSdk`

El proyecto va a `compileSdk`/`targetSdk` 35 porque son las versiones que puedo
garantizar como estables. Google Play exige `targetSdk` 36 para envíos nuevos desde
agosto de 2026; para subirlo basta cambiar los dos valores en `app/build.gradle.kts`
y revisar los cambios de comportamiento de Android 16 (sobre todo los relativos a
foreground services y a las restricciones de lanzamiento de Activities desde segundo
plano, que afectan directamente a la pantalla de bloqueo).
