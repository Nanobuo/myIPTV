# Note per lo sviluppo

## Tecnologie

- Kotlin, Jetpack Compose e Compose for TV (`androidx.tv:tv-material`, Material 3 / Material You)
- Media3 / ExoPlayer (HLS, DASH, SmoothStreaming, RTSP, MPEG-TS) con OkHttp
- DataStore per le impostazioni, Coil per i loghi
- Nessuna dipendenza da Google Play Services

## Struttura

```
app/src/main/java/io/github/nanobuo/myiptv/
├── data/      parser M3U e XMLTV, repository playlist/EPG, impostazioni
├── player/    gestione di ExoPlayer (header HTTP per canale, DRM, tracce audio)
├── update/    aggiornamento automatico da GitHub Releases
└── ui/        schermate Compose (home, player, impostazioni, editor playlist)
```

### Playlist supportate

Oltre agli attributi standard di `#EXTINF` (`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`, `tvg-chno`),
il parser gestisce:

- `#EXTM3U url-tvg="…"` / `x-tvg-url` per la guida e `user-agent` come User-Agent predefinito;
- User-Agent e Referer per canale tramite attributi `user-agent`/`http-referrer`, `#EXTVLCOPT:http-user-agent=…`,
  `#EXTHTTP:{…}`, `#KODIPROP:inputstream.adaptive.stream_headers=…` e la sintassi `url|User-Agent=…&Referer=…`;
- DRM ClearKey (`kid:key` o JSON) e Widevine/PlayReady con URL di licenza tramite `#KODIPROP`.

L'ordine dei canali segue l'LCN (`tvg-chno`, `channel-number`, …); i canali senza numero vengono messi in coda
e numerati progressivamente.

## Compilare

Serve JDK 17 o superiore e l'Android SDK.

```bash
./gradlew assembleDebug        # APK di debug in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # test dei parser
```

## Pubblicare una nuova versione

Gli aggiornamenti in-app scaricano l'APK dall'ultima release di GitHub, quindi ogni release deve essere firmata
**sempre con la stessa chiave**.

1. Crea una chiave una sola volta (conservala con cura):
   ```bash
   keytool -genkeypair -v -keystore myiptv-release.jks -alias myiptv \
     -keyalg RSA -keysize 4096 -validity 36500
   ```
2. In *GitHub → Settings → Secrets and variables → Actions* aggiungi:
   - `KEYSTORE_BASE64`: il risultato di `base64 -w0 myiptv-release.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS` (es. `myiptv`), `KEY_PASSWORD`
3. Crea e invia un tag di versione:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   Il workflow `Release` compila l'APK firmato e crea la release; le app installate la proporranno automaticamente.

Per firmare in locale si può creare un file `keystore.properties` (escluso da git) con
`storeFile`, `storePassword`, `keyAlias` e `keyPassword`.
