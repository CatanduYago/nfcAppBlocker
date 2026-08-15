# Compilación y pruebas en un teléfono real

## Requisitos

- JDK 17
- Android Studio (Ladybug o posterior) o solo el SDK con `cmdline-tools`
- Android SDK Platform 35 y Build Tools 35
- Un teléfono con **NFC** y Android 8.0 (API 26) o superior
- Al menos una tarjeta NFC. Recomendado: NTAG213/215 (las pegatinas o tarjetas genéricas
  de NFC que se venden en packs sirven perfectamente)

## Compilar

```bash
cd nfckeyblock
./gradlew :app:assembleDebug
```

Si es la primera vez y no hay wrapper de Gradle en el repositorio, generarlo con
`gradle wrapper --gradle-version 8.9` o abrir el proyecto en Android Studio, que lo crea
solo.

## Tests

```bash
./gradlew :app:testDebugUnitTest          # política de bloqueo, casos de uso, emergencia
./gradlew :app:connectedDebugAndroidTest  # persistencia de sesión (requiere dispositivo)
```

Los tests unitarios cubren lo que de verdad puede romper el producto: qué se bloquea y qué
no, qué hace cada tipo de tarjeta, y que el desbloqueo de emergencia no se pueda acelerar.

## Instalar y conceder permisos

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El servicio de accesibilidad **no se puede conceder mediante `pm grant`**: es un permiso
especial. Dos vías:

**Desde el teléfono (recomendado):**
Ajustes → Accesibilidad → Apps instaladas → NFC KeyBlock → Activar.

**Desde ADB (útil para iterar):**
```bash
adb shell settings put secure enabled_accessibility_services \
  com.nfckeyblock.debug/com.nfckeyblock.service.BlockingAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Ojo con el sufijo `.debug`: la build de depuración usa `applicationId`
`com.nfckeyblock.debug`.

Permisos opcionales:
```bash
# Datos de uso (opcional)
adb shell appops set com.nfckeyblock.debug GET_USAGE_STATS allow
# Sin restricciones de batería (recomendado en Xiaomi, Samsung, Oppo)
adb shell dumpsys deviceidle whitelist +com.nfckeyblock.debug
```

## Prueba manual completa

1. **Abrir la app.** La pantalla de inicio debe mostrar LIBRE. Si el servicio de
   accesibilidad no está activo, aparece una tarjeta roja de aviso.

2. **Pestaña Apps.** Debe listar las apps con lanzador. Activar el interruptor de una o
   dos apps fáciles de probar (Instagram, YouTube, o cualquiera que tengas).

3. **Pestaña Tarjetas → Registrar tarjeta.** Aparece el diálogo "Acerca la tarjeta".
   Apoyar la tarjeta en la parte trasera del teléfono, cerca de la cámara (ahí está la
   antena en casi todos los modelos).

4. **Configurar la tarjeta.** Nombre, acción "Alternar", perfil. Si la tarjeta es
   regrabable, dejar activado "Escribir token" y pulsar Guardar: pedirá un **segundo
   contacto** para escribir. Mantenerla quieta un segundo.

5. **Volver a inicio y acercar la tarjeta.** Debe vibrar y aparecer un toast
   "🔒 Perfil: N apps bloqueadas". El estado pasa a BLOQUEADO y arranca el cronómetro.

6. **Salir de la app y abrir una app bloqueada.** Debe aparecer la pantalla de bloqueo con
   el icono de la app, el cronómetro y el indicador NFC pulsante.

7. **Apoyar la tarjeta sobre la propia pantalla de bloqueo.** Debe desbloquear
   directamente, sin volver a la app principal.

8. **Prueba de persistencia — cerrar desde recientes:** con la sesión activa, cerrar la
   app desde recientes e intentar abrir la app bloqueada. Debe seguir bloqueada.

9. **Prueba de persistencia — reinicio:** con la sesión activa, reiniciar el teléfono.
   Tras desbloquear la pantalla, la app bloqueada debe seguir bloqueada y la notificación
   de sesión debe reaparecer.

10. **Prueba con la app cerrada:** forzar detención de la app
    (`adb shell am force-stop com.nfckeyblock.debug`) y acercar la tarjeta. Si escribiste
    el token, el sistema debe abrir la app sola y cambiar el estado. Si registraste solo
    por UID, puede aparecer un selector de aplicaciones.

11. **Desbloqueo de emergencia:** con sesión activa, Inicio → "Solicitar desbloqueo".
    Debe mostrar la cuenta atrás y no permitir confirmar antes de tiempo.

12. **Prueba de la guardia de Ajustes:** con sesión activa, ir a Ajustes → Accesibilidad.
    Debe volver atrás solo y mostrar un aviso. (Depende del fabricante; si tu ROM usa un
    paquete de ajustes no contemplado, no funcionará: es una limitación conocida.)

## Diagnóstico

```bash
adb logcat -s BlockingA11y:V NfcKeyBlockApp:V BootReceiver:V
```

| Síntoma | Causa probable |
|---|---|
| No se bloquea nada | Servicio de accesibilidad desactivado, o el fabricante lo mató. Comprobar en Ajustes |
| La tarjeta no se detecta | NFC apagado, o la antena está en otra zona del teléfono. Probar moviendo despacio |
| Aparece un selector de apps al acercar | Tarjeta registrada solo por UID. Registrar de nuevo escribiendo el token |
| El bloqueo se cae tras un rato | Optimización de batería del fabricante. Añadir la app a la lista blanca |
| La escritura NDEF falla | Tarjeta de solo lectura, sin espacio, o se separó demasiado pronto |
