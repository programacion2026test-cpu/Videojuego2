# 🗡️ Prince of Persia 2D Cooperativo

Juego de plataformas 2D para Android, inspirado en *Prince of Persia*, hecho con **Kotlin** y **Jetpack Compose**.
El objetivo final es un juego **cooperativo asimétrico para dos jugadores**: cada uno explora por su lado un laberinto de
pantallas de superficie y alcantarillas conectadas por pozos con escalera, hasta encontrar la puerta que solo se abre entre los dos.

> **Estado actual:** Fases 0 y 1B completas (motor, nivel, combate y enemigos para un jugador).
> Sigue la **Fase 2**: pozos con escalera y alcantarillas.

---

##  Demo



https://github.com/user-attachments/assets/7a64a964-41a0-461f-893f-e379750aea56



## 📑 Índice

1. [Tecnologías](#-tecnologías)
2. [Qué tiene el juego hoy](#-qué-tiene-el-juego-hoy)
3. [Controles](#-controles)
4. [Arquitectura](#-arquitectura)
5. [Estructura del proyecto](#-estructura-del-proyecto)
6. [Assets](#-assets)
7. [Configurar el juego](#-configurar-el-juego)
8. [Cómo compilar y ejecutar](#-cómo-compilar-y-ejecutar)
9. [Concepto del juego final](#-concepto-del-juego-final-cooperativo-con-alcantarillas)
10. [Hoja de ruta](#-hoja-de-ruta)
11. [Documentación](#-documentación)
12. [Control de versiones](#-control-de-versiones)
13. [Créditos y licencias](#-créditos-y-licencias)

---

## 🧰 Tecnologías

| Tecnología | Para qué se usa |
|---|---|
| **Kotlin** | Lenguaje de todo el proyecto |
| **Jetpack Compose** (`Canvas`, `foundation`, `runtime`) | Interfaz y dibujo del juego: todo el mundo se dibuja en un `Canvas` |
| **Material 3** | `Surface` y `Text` (HUD y botones) |
| **Corrutinas de Kotlin** | Bucle de juego con `LaunchedEffect` + `withFrameNanos` (tiempo real por frame) |
| **Estado de Compose** (`mutableStateOf`, `rememberUpdatedState`) | El Canvas se redibuja solo cuando cambia el estado del jugador, los enemigos o las animaciones |
| **Android SDK** (`compileSdk 37`, `targetSdk 37`) | Plataforma, `BitmapFactory` y carga de assets desde `assets/` |
| **Gradle** (Kotlin DSL) | Sistema de compilación (`build.gradle.kts`, `settings.gradle.kts`) |
| **Sprites Universal LPC** (64×64 px) | Personaje y enemigos, recortados por fila desde sprite sheets |
| **Git** (Android Studio) | Control de versiones (rama `master`, etiqueta `fase-1b`) |
| **Firebase Realtime Database** *(planificado, Fase 4)* | Sincronización online de los dos jugadores |

---

## ✅ Qué tiene el juego hoy

**Personaje (guerrero)**
- Camina, **corre** (manteniendo la dirección), **salta** (con buffer y margen de gracia), **salto en diagonal alto** (saltando corriendo), **se agacha**, **ataca** con el arma y **se cuelga de salientes** y sube.
- Vida de **3 corazones**, animación de herido, invulnerabilidad con parpadeo y **herida roja** con salpicaduras (sistema reutilizable para enemigos).

**Nivel**
- **6 salas** (una pantalla cada una) sobre un fondo de 8000×666 px cortado en 6 tramos.
- Piso, **pozos**, **pinchos animados**, **plataformas flotantes** (colisión solo desde arriba), **salientes para colgarse** y **trampas de flechas** en la pared.
- Todo configurable con constantes: ancho, posición, copias y separación de pinchos, pozos y flechas.

**Enemigos con IA**
- **Espadachín** (Sala 2) y **zorro con hacha** (Sala 3): patrullan, persiguen y atacan cuerpo a cuerpo.
- **Minotauro arquero** (Sala 4): dispara **flechas altas** (se esquivan agachado) y **bajas** (se esquivan saltando).
- Aguantan 2 golpes: gesto de dolor, herida roja, caída, y se desvanecen de a poco.

**Controles táctiles multitáctiles**
- Un único detector de dedos: se puede mantener una dirección con un dedo y saltar o atacar con otro, o deslizar entre botones.

---

## 🎮 Controles

| Botón | Acción |
|---|---|
| ◀ ▶ | Caminar. Mantenerlo más de 0,35 s activa **correr** |
| ⤴ | **Saltar** (toque). Mantenerlo repite el salto al aterrizar. Saltando corriendo: **salto en diagonal alto**. Colgado: **subir** |
| ⬇ | **Agacharse** (queda agachado hasta tocar otro botón). Colgado: **soltarse** |
| ⚔ | **Atacar** (un toque = un golpe) |

Las combinaciones (por ejemplo, dirección + salto) funcionan a la vez; se necesita un celular real para probar el multitáctil.

---

## 🏗️ Arquitectura

Principios que ordenan el código:

- **Un solo escritor de la física.** Solo `GameLoop` escribe `x`, `y`, `velocityY` e `isOnGround` del jugador. La interfaz únicamente escribe *intenciones* (dirección, salto, agacharse, ataque) y el `Canvas` solo lee para dibujar.
- **Orden fijo por frame:** tiempos → colgado → salto y ataque → gravedad y movimiento → cambio de sala → plataformas → piso → trampas → caída → reinicio.
- **Todo escala con el tamaño del personaje.** Gravedad, velocidad y salto se multiplican por `alto del jugador ÷ 180`. Las alturas de plataformas y flechas se miden en “alturas de jugador”. Cambiar el tamaño en `Player.kt` reajusta todo.
- **El nivel son datos.** Cada sala es una lista de tramos (`FLOOR`, `PIT`, `SPIKES`) más plataformas y trampas; el tramo final (`layout`) se calcula aplicando las constantes globales, y dibujo y física usan el mismo cálculo.
- **Animaciones por estado** con `Animator`: en ciclo, de una pasada o con un frame elegido por la lógica (ataque, herido, muerte, salto según la velocidad vertical).

---

## 📁 Estructura del proyecto

```
app/src/main/java/com/example/princeofpersia/
├── MainActivity.kt              Carga de assets, enemigos, Canvas de dibujo y constantes de suelo/pantalla
├── TouchControls.kt             Controles multitáctiles (◀ ▶ ⬇ ⚔ ⤴)
└── game/
    ├── engine/
    │   └── GameLoop.kt          Bucle, física, colisiones, ataque, daño, IA (tick), flechas y HUD
    ├── entities/
    │   ├── Player.kt            Estado del jugador (posición, salto, correr, vida, colgado…)
    │   ├── Enemy.kt             Enemigos con IA (cuerpo a cuerpo y arquero)
    │   └── Arrow.kt             Flechas del arquero (proyectiles)
    ├── level/
    │   └── Level.kt             Salas, tramos, plataformas, trampas y constantes de nivel
    └── render/
        ├── Animator.kt          Animación por frames
        ├── Assetimageloader.kt  Carga de imágenes, sprite sheets y fondos por tramos
        └── WoundEffect.kt       Mancha de sangre reutilizable
```

---

## 🎨 Assets

Se guardan en `app/src/main/assets/` (no en `res/drawable`):

```
assets/
├── fondos/        fondo_castillo_largo.png (8000×666, 6 tramos), fondos de alcantarilla y desierto (pendientes)
├── sprites/       guerrero_lpc.png, enemigo1.png, enemigo2.png, enemigo3.png
├── plataformas/   plataforma_centro / bordes, escalon_medio, saliente_para_colgarse
├── obstaculos/    pozo (3 piezas), pinchos 01-04, flechas 01-04 y flecha_derecha/izquierda,
│                  pozos con escalera, puerta y palanca (pendientes)
├── decoracion/    antorchas y candelabros
└── tiles/         floor_stone_tile y otros
```

Formato de sprites: **Universal LPC**, celdas de 64×64 px. Filas usadas del guerrero: caminar 9/11, saltar 27/29, agacharse 31/33, atacar 5/7, herido 20, correr 39/41, colgarse (frame 2 de las filas 35/37).

---

## ⚙️ Configurar el juego

Casi todo se ajusta con **constantes** con un comentario que explica qué hace:

| Archivo | Qué se configura |
|---|---|
| `Player.kt` | Tamaño del personaje, corazones, invulnerabilidad |
| `GameLoop.kt` | Gravedad, velocidad, salto, correr, salto en diagonal, ataque, colgarse, texto de depuración |
| `MainActivity.kt` | Grosor del suelo, cuánto baja el mundo, botones, fondo |
| `Level.kt` | Plataformas, pinchos, pozo, flechas de pared y las salas (`testRooms`) |
| `Enemy.kt` / `Arrow.kt` | Vida, velocidad, rangos, tiempos y flechas de los enemigos |
| `WoundEffect.kt` | Tamaño y duración de la herida roja |

📘 El **Manual de configuración** (`manual_configuracion_juego.docx`) explica cada constante con un dibujo de lo que cambia al subirla o bajarla.

---

## ▶️ Cómo compilar y ejecutar

1. Abrir el proyecto en **Android Studio**.
2. Verificar que esté instalada la plataforma **API 37** (`compileSdk = 37`, `targetSdk = 37`).
3. Sincronizar Gradle (*File → Sync Project with Gradle Files*).
4. Ejecutar en un celular real (recomendado, por el multitáctil) o en un emulador.
5. Los assets tienen que estar en `app/src/main/assets/` con las rutas de arriba; si falta un sprite de enemigo se usa el del guerrero para no cerrar la app.

---

## 🌍 Concepto del juego final: cooperativo con alcantarillas

Dos jugadores exploran un **laberinto** formado por pantallas de superficie y de alcantarilla unidas por **pozos con escalera**.

1. Algunas pantallas tienen un pozo con escalera que baja a una pantalla de alcantarilla, también caminable.
2. Las alcantarillas comparten el mismo fondo y se conectan entre sí y con la superficie por más pozos: es un laberinto.
3. Un **minimapa** con puntos rojos muestra dónde está cada jugador (no muestra la puerta ni las palancas).
4. Quien encuentra la **puerta** debe esperar a su compañero: hay una palanca a cada lado y ambas son de **mantener**.
5. El compañero sostiene la primera palanca mientras el otro cruza.
6. Del otro lado, el que ya pasó sostiene la segunda palanca para que el último pueda cruzar.

En online, cada jugador ve su propia sala en su dispositivo; solo se comparte lo mínimo (posición, sala, palancas y puerta).

---

## 🗺️ Hoja de ruta

### ✅ Fase 0 — Preparación del proyecto (Sesiones 1-3)
- [x] Compilar con `compileSdk 37`, proyecto base limpio y estructura de carpetas.

### ✅ Fase 1 — Motor de juego básico + nivel (Sesiones 4-13)
- [x] Game loop con tiempo real, `Player`, controles multitáctiles, gravedad, suelo, salto confiable.
- [x] Sprite LPC, `Animator`, animación por estado.
- [x] Nivel por tramos con pozo, pinchos animados, decoración y fondo cortado por salas.

### ✅ Fase 1B — Combate y contenido del jugador en solitario (Sesiones 14-14j)
- [x] **14** Plataformas flotantes (solo por arriba) · **14b** Agacharse + trampa de flechas
- [x] **14c** Salas conectadas por tramos · **14d** Ataque con ⚔ · **14e** Vida, daño y herida roja
- [x] **14f** Enemigos con IA (cuerpo a cuerpo y arquero)
- [x] **14g** Correr · **14h** Salto en diagonal alto · **14i** Colgarse de un saliente
- [x] **14j** Repaso, limpieza y commit

### 🔜 Fase 2 — Pozos con escalera y alcantarillas, un solo jugador (Sesiones 16-21)
- [ ] **16** Modelo de mundo: salas con capa (superficie/alcantarilla) y enlaces entre pozos
- [ ] **17** Nuevo tramo: pozo con escalera
- [ ] **18** Trepar escaleras
- [ ] **19** Salas de alcantarilla con su fondo
- [ ] **20** Transiciones entre capas y laberinto de prueba (con validador del mundo)
- [ ] **21** Repaso y commit “Mundo con alcantarillas (1 jugador)”

### 🔜 Fase 3 — Cooperativo local, 2 jugadores (Sesiones 22-27)
- [ ] **22** Dos jugadores, cada uno con su sala y su panel de controles
- [ ] **23** Sprites distintos por jugador
- [ ] **24** Palanca de mantener
- [ ] **25** Puerta de dos palancas
- [ ] **26** Minimapa (GPS) con puntos rojos
- [ ] **27** Pulir y probar el puzzle completo

### 🔜 Fase 4 — Multijugador online con Firebase (Sesiones 28-37)
- [ ] Realtime Database: posición, sala, estado de palancas y puerta
- [ ] Cada jugador ve su propia sala; sala de espera con código de partida
- [ ] Manejo de desconexión y decisión de autoridad para los enemigos

### 🔜 Fase 5 — Contenido, laberinto final y pulido (Sesiones 38-42)
- [ ] **38** Diseño del laberinto final (con la puerta lejos del inicio y caminos separados)
- [ ] **39** Ambientes y paletas: castillo, desierto, azul noche y alcantarilla
- [ ] **40** Más tipos de enemigos y trampas
- [ ] **41** Enemigo que cruza salas
- [ ] **42** Sonido y menú principal

### Hitos jugables

| Fin de… | Qué se puede jugar |
|---|---|
| Fase 1B ✅ | Un jugador con combate, correr, salto diagonal, colgarse y tres tipos de enemigos |
| Fase 2 | Un jugador baja por pozos, recorre alcantarillas y vuelve a la superficie por otro lado |
| Fase 3 | Dos jugadores en el mismo dispositivo resuelven la puerta de dos palancas con minimapa |
| Fase 4 | El mismo puzzle en dos dispositivos, cada jugador viendo su propia sala |
| Fase 5 | Laberinto final con ambientes, enemigos, sonido y menú |

### Limitaciones conocidas
- Los enemigos viven dentro de su sala y no evitan trampas (por eso patrullan sobre piso seguro).
- Las flechas del arquero vuelan en línea recta a altura fija.
- No hay scroll: cada sala es una pantalla.
- Cada fondo de 8000×666 px ocupa unos 21 MB decodificado: conviene cargar solo el de la capa activa.

---

## 📚 Documentación

- **Hoja de ruta y bitácora** (`hoja_de_ruta_bitacora_v11.docx`): plan por sesiones, bitácora de código, registro de assets y código completo de cada archivo.
- **Manual de configuración** (`manual_configuracion_juego.docx`): todas las constantes, qué hacen y un dibujo de cada una.

---

## 🌿 Control de versiones

- `master`: versión estable. Commit actual: **MVP motor + nivel + salas + combate básico** (etiqueta `fase-1b`).
- Sugerido: trabajar la Fase 2 en una rama aparte, por ejemplo `fase-2-alcantarillas`.

---

## 📜 Créditos y licencias

- Los sprites siguen el formato **Universal LPC (Liberated Pixel Cup)**. Antes de publicar el juego hay que revisar y cumplir la **licencia y atribución de cada recurso** (sprites, fondos, tiles y demás imágenes).
- Juego inspirado en *Prince of Persia*; proyecto personal de aprendizaje, sin fines comerciales.
