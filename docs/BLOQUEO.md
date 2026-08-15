# Sistema de bloqueo y resistencia a evasiones

## Cómo se bloquea

Android **no ofrece ninguna API para impedir que se abra una app de terceros**. Sin ser
device owner, ninguna app puede decirle al sistema "no lances este paquete". Lo que sí se
puede hacer es enterarse inmediatamente de que la app se ha abierto y taparla.

```
Usuario toca el icono de Instagram
        ↓
Android abre Instagram
        ↓
AccessibilityService recibe TYPE_WINDOW_STATE_CHANGED (packageName = com.instagram.android)
        ↓
BlockingPolicy.decide() → BlockApp
        ↓
performGlobalAction(GLOBAL_ACTION_HOME)   ← saca Instagram de primer plano
        ↓
startActivity(BlockActivity)              ← pantalla de bloqueo propia
        ↓
Se registra el intento en block_attempts
```

Instagram llega a abrirse durante unas décimas de segundo. Eso es inherente al enfoque y
lo hacen igual todas las apps del sector; no hay alternativa sin control del dispositivo.

### Por qué AccessibilityService y no UsageStatsManager

`UsageStatsManager` solo permite **sondear**. `queryEvents()` tiene un retardo de
agregación que va de medio segundo a varios, y sondear en bucle desde un foreground
service castiga la batería sin llegar a ser fiable. Sirve como respaldo y para
diagnóstico, no como mecanismo principal. En esta app es un permiso **opcional**.

`AccessibilityService` recibe el evento en el instante en que la ventana pasa a primer
plano. Es la vía soportada y la única que da una latencia aceptable.

### Por qué Activity y no overlay

Se podría dibujar un overlay con `SYSTEM_ALERT_WINDOW` en lugar de lanzar una Activity.
Se ha elegido la Activity porque:

- Un overlay no impide interactuar con la app de debajo si el usuario encuentra un hueco,
  y Android recorta cada vez más lo que un overlay puede tapar.
- Desde Android 12, los overlays no pueden cubrir ciertos diálogos del sistema.
- La Activity permite habilitar Reader Mode y desbloquear apoyando la tarjeta ahí mismo.

El permiso de overlay queda declarado como camino alternativo para fabricantes donde el
lanzamiento de Activities desde el servicio sea poco fiable, pero no se usa por defecto.

## Resistencia a evasiones: caso por caso

| Intento de evasión | Qué hace la app | ¿Resuelto? |
|---|---|---|
| **Cerrar la app desde recientes** | El estado vive en Room y el motor es el `AccessibilityService`, que no pertenece a la tarea de la app | ✅ Sí |
| **Reiniciar el teléfono** | La sesión sigue abierta en la BD; `BootReceiver` relanza la notificación. El sistema rearranca solo el servicio de accesibilidad | ✅ Sí |
| **Abrir la app bloqueada directamente** | Es el caso normal, ya cubierto | ✅ Sí |
| **Abrirla desde una notificación** | La detección es por app en primer plano, da igual quién la lanzó | ✅ Sí |
| **Abrirla desde un enlace externo o acceso directo** | Igual: se detecta la ventana resultante | ✅ Sí |
| **Usar el navegador para entrar en la web** | Bloqueo por dominio leyendo la barra de direcciones (opcional por perfil) | ⚠️ Parcial, ver abajo |
| **Pantalla dividida** | `TYPE_WINDOW_STATE_CHANGED` se dispara igual; se lanza la pantalla de bloqueo | ⚠️ Parcial: en algunos fabricantes la Activity solo cubre una mitad |
| **Ventanas flotantes / burbujas** | Se detecta el cambio de ventana | ⚠️ Parcial: las burbujas de notificación no siempre generan el evento |
| **Ir a Ajustes y desactivar el servicio** | Se desvía la navegación al detectar pantallas de accesibilidad o de info de la app; el `AccessibilityWatchdog` observa `Settings.Secure` y avisa al instante | ⚠️ Parcial: solo ralentiza |
| **Desinstalar la app** | Se desvía la pantalla de detalles de la app | ❌ No se puede impedir sin device owner |
| **Revocar permisos** | Desactivar accesibilidad detiene el bloqueo; el watchdog lanza una notificación de alta prioridad | ❌ No se puede impedir |
| **Modo seguro** | El sistema arranca sin apps de terceros | ❌ Imposible |
| **Cambiar de usuario o perfil de trabajo** | Otro espacio, otras apps | ❌ Imposible |
| **Reiniciar de fábrica** | — | ❌ Imposible, y debe seguir siéndolo |
| **Cambiar la hora del sistema para acelerar la emergencia** | El retardo se guarda como timestamp absoluto; adelantar el reloj **sí** lo acelera | ❌ Conocido, ver mejoras |

### Sobre el bloqueo web

El bloqueo por dominio lee el texto de la barra de direcciones mediante el id de vista del
navegador (`com.android.chrome:id/url_bar` y equivalentes). Esto es explícitamente mejor
esfuerzo:

- Los ids son internos y cambian entre versiones del navegador.
- Solo están mapeados Chrome, Firefox, Brave, Edge y Opera.
- No funciona en navegadores desconocidos ni en webviews dentro de otras apps.
- En algunos modos de incógnito la barra no es accesible.

Si alguien quiere evitarlo, instala otro navegador y ya está. La opción realista para un
bloqueo web serio es bloquear el navegador entero.

### Sobre la protección de Ajustes

Cuando `guardSystemSettings` está activo y hay sesión en curso, si el usuario navega a una
pantalla que coincide con `accessibility`, `appinfo`, `uninstall`, `deviceadmin` u otras
similares, la app ejecuta `GLOBAL_ACTION_BACK` y muestra un aviso.

Sus límites, con claridad:

- Depende del nombre de clase de la Activity de Ajustes, que **varía entre fabricantes**.
  Samsung, Xiaomi y Oppo usan sus propios paquetes; están contemplados los más comunes,
  no todos.
- Un usuario decidido llega igualmente: por búsqueda dentro de Ajustes, por un atajo, o
  simplemente insistiendo.
- **Esto no es una jaula.** Es fricción deliberada, para que evadir el bloqueo sea una
  decisión consciente en lugar de un impulso. Ese es todo el objetivo honesto del diseño.

## Lo que este diseño no promete

No existe bloqueo absoluto de apps en Android sin root ni sin control administrativo del
dispositivo. Cualquier app que prometa lo contrario está exagerando o usando privilegios
de device owner sin decirlo.

## La vía que sí es robusta: Device Owner

`DevicePolicyManager.setPackagesSuspended()` suspende apps a nivel de sistema. Suspendidas
de verdad: el icono se apaga, el intent no arranca nada, no hay ventana de décimas de
segundo. Y `setUninstallBlocked()` impide desinstalar.

Requiere ser **device owner**, lo que exige un dispositivo recién restablecido y un
comando ADB antes de crear la primera cuenta:

```bash
adb shell dpm set-device-owner com.nfckeyblock/.admin.KeyBlockDeviceAdminReceiver
```

No es viable para una app de consumo distribuida por Play, pero sí para quien quiera
montarse un teléfono dedicado al estudio. La arquitectura deja el hueco: bastaría añadir
un `DeviceAdminReceiver` y una implementación alternativa del bloqueo detrás de la misma
interfaz de dominio. Está en la lista de mejoras futuras, no implementado.
