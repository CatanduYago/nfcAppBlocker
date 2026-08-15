# Sistema NFC

## UID vs NDEF: qué usar y por qué

Las dos formas razonables de identificar una tarjeta desde Android:

### Opción A — UID de la tarjeta

Cada tag expone un identificador en el nivel de la capa anticolisión. En Android se lee
con `tag.id`.

**A favor**
- Funciona con absolutamente cualquier tarjeta: de transporte, de hotel, de oficina, la
  que ya lleva el usuario en la cartera.
- No requiere escribir nada, así que no destruye el contenido existente.
- Está disponible incluso en tags sin sistema de ficheros NDEF.

**En contra**
- Es trivialmente clonable. Una "magic card" china de 3 € copia un UID de MIFARE Classic
  en segundos, y cualquier móvil con NFC puede leer el UID para copiarlo.
- Tiene poquísima entropía: 4 o 7 bytes, con los primeros muy sesgados por fabricante.
- Algunas tarjetas (varias DESFire configuradas así, y la emulación HCE de otros móviles)
  usan **UID aleatorio por sesión**: cambian en cada lectura y no sirven como identidad.
- **Con la app cerrada, un tag sin NDEF solo dispara `TECH_DISCOVERED`**, y si hay varias
  apps registradas para la misma tecnología, Android muestra un selector. Adiós al gesto
  de un solo toque.

### Opción B — Registro NDEF propio + AAR

Se escribe en la tarjeta un mensaje NDEF con dos registros:

1. Un registro MIME de tipo `application/vnd.com.nfckeyblock.key` con 32 bytes aleatorios.
2. Un **AAR** (Android Application Record) con el id del paquete.

**A favor**
- El AAR es el mecanismo con el que Android garantiza el destino del dispatch: con la app
  cerrada, el sistema la abre directamente, sin selector y sin ambigüedad. Esto es lo que
  convierte la experiencia en "acerco y ya está".
- 32 bytes aleatorios frente a 4-7 bytes de UID: identificación mucho más fiable.
- Funciona aunque la tarjeta tenga UID aleatorio.

**En contra**
- Requiere una tarjeta regrabable con NDEF (NTAG213/215/216, MIFARE Ultralight,
  MIFARE Classic formateada). No sirve una tarjeta de transporte bloqueada.
- Sobrescribe el contenido NDEF anterior.
- El token **no es un secreto**: cualquiera que acerque su móvil lo lee y lo copia.

### Lo que hace esta app: las dos

`CardRepositoryImpl.findByFingerprints()` busca primero por token y luego por UID, y basta
con que uno coincida. En la práctica:

- Tarjeta regrabable → se ofrece escribir el token (recomendado en la UI). Se guardan
  ambas huellas y el dispatch en segundo plano es limpio.
- Tarjeta de solo lectura o que el usuario no quiere modificar → se registra por UID. Se
  cae al filtro `TECH_DISCOVERED`, con la advertencia del selector.
- Si el UID parece aleatorio (4 bytes empezando por `08`), la app lo detecta y avisa de
  que conviene escribir el token.

Nunca se guarda el UID ni el token en claro: solo su HMAC-SHA256 con una clave del
Keystore. Ver `SEGURIDAD.md`.

## NFC con la app cerrada: qué permite Android exactamente

Esto es lo que más suele malinterpretarse, así que en concreto:

**Android no entrega tags NFC a servicios ni a broadcast receivers.** El único mecanismo
es el *tag dispatch system*, que lanza una **Activity**. No hay API para "escuchar NFC en
segundo plano". Es una decisión de diseño deliberada: si existiera, cualquier app podría
leer tarjetas de crédito a tus espaldas.

Consecuencias prácticas, todas asumidas en la implementación:

1. **Hace falta una Activity.** `NfcDispatchActivity` es transparente, `noHistory`,
   `excludeFromRecents`, procesa el tag y se cierra sola. El usuario ve un toast y una
   vibración, no una app abriéndose.

2. **La pantalla debe estar encendida y desbloqueada.** Con el teléfono bloqueado, el
   controlador NFC está apagado o el dispatch no se entrega, según fabricante y versión.
   No hay forma soportada de saltarse esto. En la práctica: el usuario enciende la
   pantalla y acerca la tarjeta; sigue siendo un gesto de dos segundos.

3. **La prioridad del dispatch está fijada por el sistema**, en este orden:
   `NDEF_DISCOVERED` con MIME concreto → `TECH_DISCOVERED` → `TAG_DISCOVERED`. Por eso
   escribir el token con MIME propio da el comportamiento más fiable.

4. **Con la app en primer plano se usa Reader Mode**, no dispatch. `enableReaderMode()`
   evita el sonido del sistema, no relanza Activities y permite saltarse la comprobación
   NDEF. Es lo correcto para registrar tarjetas y para la pantalla de bloqueo.

5. El callback de Reader Mode **llega en un hilo de binder**, no en el principal. La
   escritura NDEF, que es E/S bloqueante, se hace ahí; solo se salta al hilo principal
   para tocar la UI.

## Escritura: por qué hacen falta dos toques

La conexión con el tag solo es válida mientras la tarjeta está dentro del campo. En cuanto
se separa, cualquier operación lanza `IOException`. Como entre la detección y la
confirmación del usuario hay un diálogo de por medio, el flujo real es:

```
1er toque  →  se lee UID/NDEF  →  diálogo de configuración
                                        ↓ Guardar
2º toque   →  se escribe el token  →  se registra la tarjeta
```

Si la escritura falla (tarjeta de solo lectura, sin espacio, contacto perdido), la app
avisa y cae al registro por UID. No se pierde el trabajo del usuario.

## Tarjetas recomendadas

| Tarjeta | Sirve | Nota |
|---|---|---|
| NTAG213 / 215 / 216 | Sí, ideal | Baratas, regrabables, NDEF nativo. 144 bytes de NTAG213 sobran |
| MIFARE Ultralight | Sí | Similar a NTAG |
| MIFARE Classic 1K | Sí, formateando | Soporte NDEF depende del chip NFC del teléfono |
| MIFARE DESFire EV2/EV3 | Sí, por UID | Con UID aleatorio hay que escribir NDEF si está permitido |
| NTAG 424 DNA | Sí, y es la buena | Ver `SEGURIDAD.md`: es la única que resuelve el clonado |
| Tarjeta de transporte / hotel | Solo por UID | No se puede escribir; funciona pero con selector de app |
