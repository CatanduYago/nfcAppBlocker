# Compatibilidad con Google Play

Resumen previo: **es publicable**, pero el `AccessibilityService` obliga a un proceso de
revisión manual que rechaza a bastantes apps por presentarlo mal. La mayoría de los
rechazos se deben a la declaración, no a la funcionalidad.

Las políticas de Play cambian con frecuencia; conviene contrastar lo de abajo con la
Play Console antes de enviar.

## AccessibilityService — el punto crítico

Google exige que el uso del API de accesibilidad esté justificado y que la app describa en
la ficha cómo lo usa. Puntos clave:

1. **`android:isAccessibilityTool="false"`.** Esta app es de bienestar digital, no una
   herramienta de asistencia para personas con discapacidad. Declararlo como `true` sin
   serlo es motivo directo de rechazo, y la revisión lo comprueba. Ya está puesto a
   `false` en `accessibility_service_config.xml`.

2. **Descripción precisa en `android:description`.** Debe decir qué datos se consumen y
   para qué. La actual: solo se usa el nombre del paquete en primer plano, no se lee ni se
   almacena el contenido de pantalla.

3. **Formulario de declaración de permisos** en la Play Console, explicando la
   funcionalidad principal y por qué no hay alternativa. Las apps de control parental y
   bienestar digital son un caso de uso aceptado, y hay varias publicadas con este mismo
   mecanismo.

4. **Divulgación destacada en la app** antes de pedir el permiso, no solo en la política
   de privacidad.

5. **Vídeo de demostración**: la revisión suele pedirlo. Conviene grabar el flujo completo
   (activar permiso → seleccionar apps → acercar tarjeta → app bloqueada) y tenerlo listo.

## Otras políticas aplicables

| Área | Situación | Acción |
|---|---|---|
| `QUERY_ALL_PACKAGES` | **No se usa.** Se listan apps con `<queries>` por intent de launcher | Ninguna. Evita el permiso sensible por completo |
| `PACKAGE_USAGE_STATS` | Declarado, opcional para el usuario | Justificar en el formulario o eliminarlo si no se implementa el respaldo |
| `SYSTEM_ALERT_WINDOW` | Declarado pero no usado | **Recomendación: quitarlo del manifest** hasta que se implemente el overlay. Declarar permisos sin usar es un riesgo innecesario en la revisión |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ exige tipo y justificación | Ya declarado con `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. Hay que rellenar la justificación en la Console |
| `RECEIVE_BOOT_COMPLETED` | Normal, sin fricción | Ninguna |
| NFC | Sin restricciones de política | Ninguna |
| Device Admin | **No se usa** | Play restringe mucho las apps de device admin; mantenerlo fuera simplifica la publicación |
| Datos y privacidad | No sale nada del dispositivo; la app no tiene permiso de internet | Declarar "no se recopilan datos" en la sección de seguridad de datos |
| Apps de bienestar digital | Categoría aceptada | Encajarla como "Productividad" o "Herramientas" |

## Antes de enviar

- Quitar `SYSTEM_ALERT_WINDOW` si no se implementa el overlay.
- Decidir si se mantiene `PACKAGE_USAGE_STATS`; si el respaldo no se implementa, quitarlo.
- Subir `targetSdk` a 36 (requisito para envíos nuevos desde agosto de 2026).
- Preparar el vídeo de demostración y la divulgación destacada dentro de la app.
- Rellenar la sección de seguridad de datos declarando cero recopilación.

## Si rechazan la app

Arquitectura alternativa, por orden de coste:

1. **Reposicionarla como app de control parental**, con el flujo pensado para que un adulto
   la configure en el dispositivo de un menor. Es una categoría con criterios de revisión
   más claros para el uso de accesibilidad. Requiere cambios de producto, no de código.

2. **Sustituir el motor por `UsageStatsManager`** con sondeo desde el foreground service.
   Elimina por completo el `AccessibilityService` y con él la revisión manual. Coste real:
   la detección pasa de instantánea a entre uno y varios segundos, el consumo de batería
   sube, y la protección de la pantalla de Ajustes desaparece. El bloqueo se degrada de
   "eficaz" a "recordatorio insistente". La arquitectura lo permite: bastaría otra
   implementación detrás de la misma decisión de `BlockingPolicy`.

3. **Distribución fuera de Play** (APK directo, F-Droid, Obtainium) manteniendo el
   `AccessibilityService`. Sin restricciones de política, a cambio de renunciar a la
   distribución masiva y a las actualizaciones automáticas.
