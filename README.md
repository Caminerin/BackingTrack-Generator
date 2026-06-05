# BackingTrack Generator

App Android (Kotlin + Jetpack Compose, Material 3) que **genera backing tracks
localmente** para tocar la guitarra encima. Elige estilo, tonalidad, BPM, feel y
progresión, y la app crea una base de **batería + bajo + guitarra rítmica +
piano** con **sonidos de instrumentos reales** (samples grabados, sin MIDI ni
síntesis), la reproduce en bucle y la exporta a M4A/WAV.

Comparte identidad visual (paleta ámbar/oscuro Material 3) con la app hermana
[guitar-trainer](https://github.com/Caminerin/guitar-trainer).

## Características (v1)

- **Estilos**: Blues, Blues Rock, Classic Rock, Funk, Slow Ballad/Soul.
- **Generación procedural** por estilo con humanización (micro-timing, variación
  de velocity, round-robins de samples) para que no suene "machine gun".
- **Instrumentos reales** (CC0 / CC-BY): batería acústica, bajo eléctrico,
  guitarra eléctrica limpia y piano vertical.
- **Render** 44.1 kHz estéreo, mezcla por buses (paneo, ganancia, limiter suave
  en el master) dejando hueco para la guitarra solista.
- **Reproductor** con play/pausa, loop y mezclador por instrumento (on/off +
  volumen) en tiempo de regeneración.
- **Editor de progresiones** visual (rejilla de acordes, presets por estilo).
- **Biblioteca local** (guardar, favoritos, borrar) y **exportar a M4A** para
  compartir.

## Sonidos / Licencias

Todos los samples son grabaciones reales con licencia libre. Ver
[`app/src/main/assets/packs/core/CREDITS.md`](app/src/main/assets/packs/core/CREDITS.md):

| Instrumento | Fuente | Licencia |
|-------------|--------|----------|
| Batería | MuldjordKit (FreePats / DrumGizmo) | CC-BY-4.0 |
| Bajo | FreePats Electric Bass YR | CC0-1.0 |
| Guitarra | FreePats FSBS Clean Electric Guitar | CC0-1.0 |
| Piano | Upright Piano KW (FreePats) | CC0-1.0 |

## Compilar / Probar

El APK se construye automáticamente en **GitHub Actions** en cada push
(`assembleDebug`) y se publica como artifact descargable
(`BackingTrackGenerator-debug-apk`). Descárgalo desde la pestaña *Actions*.

Para compilar en local:

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/
```

## Arquitectura

```
model/      Teoría musical, JamRecipe, estilos, progresiones, biblioteca
audio/      SamplePack, generadores (drums/bass/guitar/keys), Conductor,
            Humanizer, Renderer (mezcla offline), exportador WAV/M4A, player
data/       Persistencia JSON de la biblioteca local
ui/         Pantallas Compose (Home, Create, Player+Mixer, Progression, Library)
tools/      Constructor del pack de samples (Python)
```

### Pack de samples

`tools/build_core_pack_local.py` procesa los repos de samples (FLAC) a WAV
44.1 kHz mono y genera `manifest.json` + `CREDITS.md` en
`app/src/main/assets/packs/core/`.

## Roadmap (v2)

- Motor C++/NDK + Oboe para baja latencia en vivo.
- Órgano/Hammond real cuando haya un sample con licencia adecuada.
- Multisamples multi-mic de batería, packs descargables, export de stems.
