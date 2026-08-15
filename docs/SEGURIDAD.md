# Seguridad

## Modelo de amenazas: contra quién se defiende esto

El adversario aquí **es el propio usuario**, pero en un momento distinto. La app existe
para que el "yo de las 11 de la noche" no pueda deshacer lo que decidió el "yo de las 9 de
la mañana", y no para resistir a un atacante con herramientas y tiempo.

Con eso claro, el objetivo de seguridad es concreto: que evadir el bloqueo cueste más
esfuerzo que cumplirlo, y que nadie salvo el dueño del teléfono pueda tocar la
configuración.

## La tarjeta NFC no es un mecanismo criptográfico

Conviene decirlo sin rodeos: **una tarjeta NFC corriente no autentica nada**.

### Clonado

- El UID se lee con cualquier móvil con NFC y una app gratuita. Copiarlo a una "magic
  card" cuesta unos 3 € y treinta segundos.
- El contenido NDEF también se lee y se copia igual de fácil. El token de 32 bytes que
  escribe esta app **no es un secreto**: aporta entropía e identificación fiable, no
  seguridad.
- Los ataques de repetición son triviales: no hay reto-respuesta, así que lo que se leyó
  una vez sirve siempre.

### Consecuencia de diseño

La tarjeta se trata como **un factor físico de activación**, no como una llave
criptográfica. Toda la configuración real (perfiles, apps bloqueadas, sesiones, retardo de
emergencia) vive en el dispositivo. En la tarjeta no hay nada sensible: perderla no
expone nada, y clonarla solo sirve para desbloquear tu propio teléfono, que es algo que ya
podías hacer con la original.

Que la tarjeta sea clonable importa poco porque el modelo de amenazas es la fricción, no
la exclusión. Que sea *física* es justo lo valioso: puedes dejarla en otra habitación.

### La opción que sí resuelve el clonado

**NTAG 424 DNA** (y MIFARE DESFire con AES) implementan autenticación por reto-respuesta
con AES-128: el teléfono envía un desafío aleatorio, la tarjeta responde con un criptograma
que solo puede generar quien tenga la clave, y la clave nunca sale del chip. Un clon no
puede responder.

Sería implementable desde esta app con `IsoDep.transceive()`, guardando la clave AES en el
Keystore. Añade complejidad real (gestión de claves, aprovisionamiento de la tarjeta,
recuperación si se pierde la clave) y encarece la tarjeta. **No está implementado**; el
diseño lo permite porque `HandleCardTapUseCase` depende de una identidad abstracta, no del
UID directamente.

## Qué protege el Android Keystore

Se genera una clave HMAC-SHA256 con alias `nfckeyblock_card_hmac_v1`, marcada solo para
firmar y **no exportable**. En la base de datos únicamente se guarda
`HMAC(clave, UID)` y `HMAC(clave, token)`.

Por qué HMAC y no un SHA-256 pelado: un UID tiene 4 o 7 bytes. Un hash simple de un
espacio tan pequeño se invierte por fuerza bruta en nada, y en la práctica los UID reales
están muy sesgados por fabricante. Con HMAC y una clave que no se puede extraer, quien
consiga leer la base de datos no puede reconstruir el UID ni fabricar una entrada válida.

La clave **no** exige autenticación de usuario (`setUserAuthenticationRequired`), a
propósito: el servicio tiene que poder verificar una tarjeta sin pedir huella cada vez.

Si el dispositivo tiene StrongBox o TEE, la clave vive ahí. Al desinstalar la app, la
clave se destruye y las huellas guardadas dejan de ser verificables, que es exactamente lo
que se quiere.

## Almacenamiento local

- La base de datos Room **no está cifrada con SQLCipher**. Es una decisión razonada: desde
  Android 10 el almacenamiento interno está cifrado en reposo (FBE) y el directorio de la
  app no es accesible para otras apps sin root. Dentro no hay secretos, solo HMACs cuya
  clave está en el Keystore. Añadir SQLCipher costaría rendimiento y una clave más que
  gestionar, a cambio de protección solo frente a un atacante con root, que en ese caso ya
  tiene el Keystore comprometido de otras formas.
- Las copias de seguridad automáticas están **desactivadas**
  (`allowBackup="false"` + reglas de extracción vacías). Restaurar la BD en otro
  dispositivo dejaría huellas de tarjeta que no se pueden verificar, porque la clave del
  Keystore no viaja.

## Autenticación del usuario

No hay cuentas ni contraseñas. El único control de acceso a la configuración es el
desbloqueo del propio teléfono, que ya es un factor de autenticación gestionado por el
sistema. Añadir una contraseña de app propia daría una falsa sensación de seguridad:
cualquiera que tenga el teléfono desbloqueado puede desinstalar la app.

## Vector conocido sin resolver: el reloj

El desbloqueo de emergencia se guarda como timestamp absoluto
(`System.currentTimeMillis()`). Adelantar la hora del sistema acelera el desbloqueo.

La solución sería usar `SystemClock.elapsedRealtime()`, que no se puede manipular, pero se
reinicia con el teléfono, así que reiniciar cancelaría la cuenta atrás. Un enfoque robusto
combina ambos y toma el más restrictivo. Está en la lista de mejoras, no implementado.
