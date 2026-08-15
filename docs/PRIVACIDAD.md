# Política de privacidad

Última actualización: agosto de 2026.

## Resumen

NFC KeyBlock funciona íntegramente en tu dispositivo. No recopila, transmite ni comparte
ningún dato personal. No hay cuentas, no hay servidores, no hay analítica, no hay
publicidad.

Esto no es una declaración de intenciones: **la app no solicita el permiso de internet**.
Técnicamente no puede enviar nada a ninguna parte, aunque quisiera.

## Qué datos maneja la app y dónde se quedan

Todo lo siguiente se guarda exclusivamente en el almacenamiento privado de la app, en tu
teléfono:

| Dato | Para qué | Dónde |
|---|---|---|
| Lista de apps que has elegido bloquear | Saber qué bloquear | Base de datos local |
| Perfiles de bloqueo | Configuración | Base de datos local |
| Huella criptográfica de tus tarjetas NFC | Reconocer tu tarjeta | Base de datos local |
| Historial de sesiones e intentos de apertura | Estadísticas | Base de datos local |
| Preferencias | Ajustes | DataStore local |
| Clave HMAC | Proteger las huellas de tarjeta | Android Keystore, no exportable |

## Sobre el servicio de accesibilidad

NFC KeyBlock usa el API de accesibilidad de Android, que técnicamente permite leer el
contenido de la pantalla. La app usa únicamente:

- El **nombre del paquete** de la aplicación que pasa a primer plano.
- El **nombre de clase** de la ventana, para detectar pantallas de ajustes sensibles.
- Si activas el bloqueo web, el **texto de la barra de direcciones** de navegadores
  conocidos, y solo para compararlo con tu lista de dominios bloqueados.

No se lee, guarda ni transmite ningún otro contenido de pantalla: ni mensajes, ni
contraseñas, ni nada que escribas.

## Sobre las tarjetas NFC

Cuando registras una tarjeta, la app calcula un HMAC-SHA256 de su identificador usando una
clave que vive en el Android Keystore y que no se puede extraer del dispositivo. **El
identificador de la tarjeta nunca se guarda en claro.**

Si eliges escribir un token en la tarjeta, se escriben 32 bytes aleatorios y el
identificador del paquete de la app. No se escribe ningún dato personal tuyo.

## Datos de uso

La app comprueba si le has concedido el permiso de acceso a datos de uso, pero **no lo
requiere y actualmente no lo utiliza** para recopilar nada. Puedes no concederlo sin
perder funcionalidad.

## Copias de seguridad

Las copias de seguridad automáticas de Android están desactivadas para esta app. Tus datos
no se suben a Google Drive.

## Eliminación de datos

Desinstalar la app borra todo: la base de datos, las preferencias y la clave del Keystore.
No queda ninguna copia en ningún otro sitio, porque nunca la hubo.

## Terceros

Ninguno. La app no incluye SDK de analítica, de publicidad ni de informes de fallos.

## Cambios

Si en el futuro alguna función requiriese conexión a internet, se pedirá el permiso
correspondiente de forma explícita y esta política se actualizará antes.
