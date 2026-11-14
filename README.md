# Ruleta de Desafíos — mod de Fabric para Minecraft Java 26.3

Gira una ruleta y recibe un desafío aleatorio (con efecto de poción opcional y temporizador).
Incluye pantalla de configuración, comandos, giro automático y todo configurable en un JSON.

- Minecraft **26.3** · Fabric Loader **0.19.5+** · Fabric API **0.160.6+26.3** (o más nuevo) · **Java 25**
- Funciona en mundo local y en servidor dedicado (instalar el mod en servidor y clientes).
- Mod Menu es **opcional** (añade el botón "Configurar" en la lista de mods).

## Cómo compilarlo

Necesitas **JDK 25** (el Gradle JVM también debe ser 25).

```bash
./gradlew build          # Windows: gradlew.bat build
```

El `.jar` queda en `build/libs/ruleta-desafios-1.0.0.jar` (no uses el `-sources`).
Cópialo a tu carpeta `mods` junto con Fabric API.

Para probarlo en desarrollo: `./gradlew runClient`.

## Cómo se usa

| Acción | Cómo |
| --- | --- |
| Girar la ruleta | tecla **R** (cambiable en Controles) o `/ruletagui` |
| Abrir la configuración | tecla **K**, `/ruletaconfig`, o el botón *Configuración* de la ruleta, o Mod Menu |
| Girar (elige el servidor, sin animación) | `/ruleta girar` |
| Ver el desafío activo | `/ruleta` |
| Detener el desafío | `/ruleta parar` |
| Listar desafíos | `/ruleta lista` |
| Activar uno concreto | `/ruleta aplicar <id>` |
| Recargar el JSON | `/ruleta recargar` |

La ruleta aparece como una superposición sobre el mundo, sin abrir una pantalla ni pausar la partida.
Al pulsar **R** comienza automáticamente el giro circular; al detenerse, muestra el desafío elegido
en un aviso y ejecuta `/ruleta aplicar <id>`. Mientras dura el desafío se ve el tiempo restante
en la barra de acción.

`parar` y `recargar` requieren ser operador (en un mundo local siempre puedes).

## Pantalla de configuración

- **General:** giro automático (cada X min), duración por defecto, duración de la animación,
  quién puede girar, aplicar efectos, afectar a todos o solo a quien gira, chat, título y sonidos.
- **Desafíos:** lista paginada. Clic en un desafío para editarlo (nombre, descripción, efecto,
  nivel, duración, peso, activo), activarlo/desactivarlo y *Añadir desafío* para crear uno nuevo.

Los cambios se guardan con **Guardar** en `config/ruleta-desafios.json`.

## Tipos de desafío

- **Con efecto:** pon el id de un efecto, p. ej. `minecraft:blindness`, `minecraft:slowness`,
  `minecraft:mining_fatigue`, `minecraft:weakness`, `minecraft:hunger`, `minecraft:nausea`,
  `minecraft:darkness`, `minecraft:jump_boost`, `minecraft:glowing`, `minecraft:speed`
  (también sirven efectos de otros mods). Se reaplica si mueres o entras de nuevo.
- **De palabra (modo honor):** deja el efecto vacío; solo se anuncia y cada quien lo cumple
  ("Sin correr", "Solo madera", "Silencio total"...). El mod **no** vigila estas reglas.

El **peso** controla la probabilidad: un desafío con peso 3 sale el triple de veces que uno con peso 1.

## Notas importantes

- La ruleta visual decide el ganador en el cliente y avisa al servidor con un comando; en un
  servidor dedicado los ids de los desafíos deben existir en el `ruleta-desafios.json` del servidor.
  Si quieres que decida el servidor, usa `/ruleta girar`.
- Si un desafío tiene efecto y termina o se detiene, se quita ese efecto a todos los jugadores.

## Si algo no compila (26.3 es muy reciente)

Este proyecto se escribió contra la documentación de Fabric 26.1/26.2 y las notas de 26.3, sin
poder compilarlo contra los jars de 26.3. Los puntos más propensos a cambiar son:

1. `RuletaCommands.isAdmin` → `net.minecraft.server.permissions.Permissions` / `source.permissions()`.
2. `RuletaManager.playSound` → `playNotifySound` y `SoundEvents.UI_TOAST_CHALLENGE_COMPLETE` (los sonidos son opcionales; puedes vaciar el método).
3. `RuletaScreen.finishSpin` → `minecraft.getConnection().sendCommand(...)`.
4. `RuletaDesafiosClient` → `client.gui.screen()` / `client.gui.setScreen(...)`.
5. Pantallas → `GuiGraphicsExtractor.text(...)` / `fill(...)` y `Screen#rebuildWidgets()`.
6. Versiones en `gradle.properties` (revisa https://fabricmc.net/develop): `fabric_api_version`, `loom_version`, `modmenu_version`.

Si no quieres Mod Menu, borra la línea `compileOnly ... modmenu` de `build.gradle`,
`client/ModMenuIntegration.java` y la entrada `"modmenu"` de `fabric.mod.json`.

## Obtener el .jar sin instalar nada

Sube esta carpeta a un repositorio de GitHub: el flujo `.github/workflows/build.yml` compila
solo y deja el `.jar` en la pestaña **Actions → (ejecución) → Artifacts → ruleta-desafios-jar**.

## Animaciones de la ruleta

Ruleta circular con ocho sectores de colores, aro dorado, puntero fijo, desaceleración progresiva,
sonido al terminar y aviso del desafío ganador sobre el HUD.

## Config desde el menú del juego

Hay botones **Ruleta de desafíos** y **Config. de la ruleta** arriba a la izquierda en el menú de
pausa (ESC), y el botón de configuración también en la pantalla de título. Además: tecla K,
`/ruletaconfig` y Mod Menu.

## Compilar con doble clic

Con JDK 25 instalado: en Windows abre `compilar.bat`; en Linux/Mac ejecuta `./compilar.sh`.
