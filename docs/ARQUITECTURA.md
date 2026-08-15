# Arquitectura

## Visión general

Tres capas, dependencias siempre hacia dentro. El dominio no conoce Android salvo en
un punto justificado (`NfcTagIdentity`, que envuelve un `Tag`).

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Compose)                                                 │
│  MainActivity ── NavHost ── Home │ Apps │ Cards │ Profiles    │
│                                  │ Stats │ Settings          │
│  BlockActivity          (pantalla que cubre la app bloqueada)│
│  NfcDispatchActivity    (entrada del sistema al leer tarjeta)│
└───────────────┬──────────────────────────────────────────────┘
                │ StateFlow
┌───────────────▼──────────────────────────────────────────────┐
│ ViewModels                                                   │
│  HomeVM · AppsVM · CardsVM · ProfilesVM · StatsVM · SettingsVM│
└───────────────┬──────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│ Domain                                                       │
│  BlockingPolicy        (decisión pura: bloquear / permitir)  │
│  HandleCardTapUseCase  (tarjeta → cambio de estado)          │
│  RegisterCardUseCase · StartSessionUseCase                   │
│  EmergencyUnlockUseCase                                      │
│  Interfaces de repositorio + modelos                         │
└───────────────┬──────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│ Data                                                         │
│  Room       perfiles, apps bloqueadas, tarjetas,             │
│             sesiones, intentos                               │
│  DataStore  preferencias                                     │
│  Keystore   clave HMAC no exportable                         │
│  PackageManager  apps instaladas                             │
└──────────────────────────────────────────────────────────────┘
                ▲
┌───────────────┴──────────────────────────────────────────────┐
│ Servicios del sistema                                        │
│  BlockingAccessibilityService  detecta app en primer plano   │
│  BlockingSessionService (FGS)  notificación + watchdog       │
│  AccessibilityWatchdog         vigila Settings.Secure        │
│  BootReceiver                  restaura tras reiniciar       │
└──────────────────────────────────────────────────────────────┘
```

## La decisión estructural que lo sostiene todo

**El estado de bloqueo vive en Room, no en memoria.**

Suena trivial y es lo que hace que funcione. Una sesión activa es simplemente una fila
en `sessions` con `endedAt IS NULL`. De ahí se derivan gratis casi todas las defensas:

- Cerrar la app desde recientes no cambia la fila.
- Que el sistema mate el proceso no cambia la fila.
- Reiniciar el teléfono no cambia la fila.
- El `AccessibilityService`, cuando el sistema lo rearranca, vuelve a leer la misma fila.

Si el estado viviera en un singleton en memoria, cada uno de esos casos sería un agujero
que habría que tapar por separado.

## Inyección de dependencias

`AppContainer`, manual, sin Hilt. El grafo tiene una docena de nodos, todos singleton de
aplicación, y los puntos de entrada críticos (`AccessibilityService`, `BroadcastReceiver`)
los instancia el sistema, no el inyector. Un contenedor explícito evita el procesador de
anotaciones y hace obvio de un vistazo qué se construye y cuándo.

## Modelo de datos

```
profiles ─┬─< blocked_apps        (perfil → paquetes, CASCADE)
          ├─< nfc_cards           (tarjeta → perfil, SET NULL)
          └─< sessions ─< block_attempts
```

| Tabla | Campos clave | Notas |
|---|---|---|
| `profiles` | `name`, `colorArgb`, `emoji`, `blockedDomainsCsv`, `guardSystemSettings`, `autoEndMinutes` | Un perfil = una configuración completa de bloqueo |
| `blocked_apps` | PK `(profileId, packageName)` | Relación N:M implícita; CASCADE al borrar perfil |
| `nfc_cards` | `uidFingerprint` (UNIQUE), `tokenFingerprint`, `action`, `profileId` | **Nunca** se guarda el UID en claro |
| `sessions` | `startedAt`, `endedAt`, `endReason`, `emergencyUnlockAt` | `endedAt IS NULL` = sesión activa |
| `block_attempts` | `sessionId`, `packageName`, `timestamp` | Base de las estadísticas |

DataStore guarda solo preferencias (retardo de emergencia, reanudar tras reinicio, tema).
El Keystore guarda una única clave HMAC-SHA256 no exportable, alias
`nfckeyblock_card_hmac_v1`.

## Flujo de navegación

```
MainActivity
 └─ NavHost
     ├─ home       (estado, cronómetro, arranque manual, emergencia)
     ├─ apps       (selector de apps por perfil)
     ├─ cards      (registrar / eliminar / configurar tarjetas)
     ├─ profiles   (CRUD de perfiles)
     ├─ stats      (tiempo, sesiones, racha, intentos)
     └─ settings   (permisos, comportamiento, privacidad)

BlockActivity        — fuera del NavHost, tarea propia, se lanza desde el servicio
NfcDispatchActivity  — fuera del NavHost, invisible, la lanza el sistema
```

## Componentes Android: por qué, qué permiso, qué límite

| Componente | Por qué se usa | Permiso | Limitación | Riesgo de privacidad |
|---|---|---|---|---|
| `NfcAdapter` (Reader Mode) | Leer tarjetas con la app abierta | `NFC` (normal) | Solo con pantalla encendida y desbloqueada | Ninguno: solo se lee el UID de la tarjeta que el usuario acerca |
| Dispatch NFC a Activity | Leer tarjetas con la app cerrada | `NFC` | Android solo entrega tags a Activities, nunca a servicios | Obliga a mostrar algo en pantalla, que es la intención del diseño |
| `AccessibilityService` | Saber qué app pasa a primer plano | Concedido en Ajustes | El usuario puede desactivarlo; el sistema puede matarlo | **Alto en potencia**: da acceso al contenido de pantalla. Aquí solo se lee `packageName`, `className` y, si se activa el bloqueo web, el id de vista de la barra de direcciones |
| `UsageStatsManager` | Detección de respaldo y diagnóstico | `PACKAGE_USAGE_STATS` (especial) | Solo sondeo, con retardo de segundos | Medio: expone historial de uso. Es **opcional** en esta app |
| Foreground Service | Notificación de sesión + watchdog | `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14 exige tipo y justificación en Play | Ninguno |
| `BroadcastReceiver` (boot) | Restaurar la sesión tras reiniciar | `RECEIVE_BOOT_COMPLETED` | No se ejecuta hasta el primer desbloqueo del usuario | Ninguno |
| `PackageManager` + `<queries>` | Listar apps con lanzador | Ninguno especial | No ve apps sin icono | Bajo: no se usa `QUERY_ALL_PACKAGES` a propósito |
| `Android Keystore` | Clave HMAC de huellas de tarjeta | Ninguno | La clave se pierde al desinstalar (deseable) | Ninguno |
| `SYSTEM_ALERT_WINDOW` | Overlay alternativo | Especial | Declarado pero **no usado** por defecto | Se puede eliminar del manifest si no se implementa el overlay |

Permisos deliberadamente **no** solicitados: `QUERY_ALL_PACKAGES`, internet, ubicación,
contactos, almacenamiento. La app no tiene permiso de red en absoluto: no puede enviar
nada aunque quisiera.
