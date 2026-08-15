# Limitaciones conocidas

Sin adornos. Lo que esta app no puede hacer, y por qué.

## Impuestas por Android, sin solución sin root ni device owner

1. **No se puede impedir que una app se abra.** Se detecta y se tapa. Hay una ventana de
   décimas de segundo en la que la app bloqueada está visible.
2. **No se puede impedir la desinstalación.** Se puede desviar la navegación hacia la
   pantalla de detalles de la app, no bloquearla.
3. **No se puede impedir que se desactive el servicio de accesibilidad.** Solo detectarlo
   y avisar. Y así debe ser: lo contrario sería la puerta perfecta para el malware.
4. **No hay NFC en segundo plano puro.** Android solo entrega tags a Activities. Siempre
   habrá una Activity, aunque sea invisible y dure medio segundo.
5. **El NFC no funciona con la pantalla apagada o bloqueada** en la mayoría de
   dispositivos.
6. **El modo seguro desactiva todas las apps de terceros.**
7. **Cambiar de usuario o abrir el perfil de trabajo** deja el bloqueo fuera de juego.
8. **No se puede saber el "tiempo de uso evitado"** de forma fiable. La app no lo estima:
   muestra solo medidas reales (tiempo bloqueado, intentos registrados).

## Del enfoque elegido

9. **El bloqueo web es mejor esfuerzo.** Depende de ids de vista internos de cada
   navegador, solo cubre cinco navegadores, y falla si cambian de versión o si se usa un
   navegador desconocido.
10. **La guardia de Ajustes depende del fabricante.** Los nombres de clase de las
    Activities de Ajustes varían en Samsung, Xiaomi, Oppo y otros. Están contemplados los
    más comunes, no todos.
11. **Pantalla dividida y burbujas flotantes están cubiertas de forma parcial.** Se
    detecta el cambio de ventana, pero la pantalla de bloqueo puede acabar ocupando solo
    una mitad.
12. **Las tarjetas NFC corrientes son clonables.** Ver `SEGURIDAD.md`. La tarjeta es un
    factor físico, no una llave criptográfica.
13. **Adelantar el reloj del sistema acelera el desbloqueo de emergencia.**
14. **Los fabricantes agresivos con la batería** (Xiaomi, Huawei, Oppo, Vivo) pueden matar
    el foreground service. Hay que añadir la app a la lista blanca a mano.
15. **Solo se listan apps con icono en el launcher.** Es una consecuencia deliberada de no
    usar `QUERY_ALL_PACKAGES`.

## Del estado actual del proyecto

16. **No compilado.** Escrito sin entorno Android disponible; espera resolver algún import
    o versión en el primer build.
17. **Sin asistente de onboarding.** Los avisos de la pantalla de inicio hacen de guía.
18. **Sin exportación/importación de configuración**, aunque aparezca en los requisitos.
19. **La detección de respaldo por `UsageStatsManager` no está implementada**; el permiso
    se declara y se comprueba, pero no hay sondeo.
20. **El overlay alternativo no está implementado**; el permiso está declarado. Conviene
    quitarlo del manifest antes de publicar.

---

# Mejoras futuras

Por orden de valor frente a coste:

1. **Reloj a prueba de manipulación** para la emergencia: combinar
   `elapsedRealtime()` con el reloj de pared y tomar el más restrictivo.
2. **Asistente de onboarding** que guíe permiso a permiso hasta la primera tarjeta.
3. **Exportar/importar configuración** en JSON, excluyendo las huellas de tarjeta (que no
   son portables porque la clave del Keystore no viaja).
4. **Modo Device Owner opcional** con `setPackagesSuspended()` y `setUninstallBlocked()`,
   activable por ADB para quien quiera un teléfono dedicado. Es el salto cualitativo real.
5. **Soporte de NTAG 424 DNA** con reto-respuesta AES vía `IsoDep`, para que clonar la
   tarjeta deje de funcionar.
6. **Programación horaria**: activar perfiles por franjas sin necesidad de tarjeta.
7. **Ampliar la guardia de Ajustes** con detección por texto de nodo además de por clase,
   para cubrir más fabricantes y más idiomas.
8. **Bloqueo web decente** vía VPN local (`VpnService`) con filtrado DNS, que sí es
   independiente del navegador. Complica bastante la revisión en Play.
9. **Widget y azulejo de ajustes rápidos** para consultar el estado de un vistazo.
10. **Modo acompañado**: que una segunda persona tenga la tarjeta de desbloqueo.
