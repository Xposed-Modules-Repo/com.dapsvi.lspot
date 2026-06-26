# LSpot

An LSPosed module that blocks ads in Spotify by intercepting OkHttp requests to ad endpoints.

## How it works

Hooks `OkHttpClient.Builder.build()` at runtime and injects a dynamic interceptor that returns empty 200 OK responses for ad requests. No APK modification, all in-memory.

## Requirements

- Android 8.1+
- A rooted device with [LSPosed](https://github.com/LSPosed/LSPosed) or any compatible fork

## Install

1. Download the [latest release](https://github.com/Xposed-Modules-Repo/com.dapsvi.lspot/releases/latest)
2. Install the APK
3. Enable the module in LSPosed/Vector and scope it to `com.spotify.music`
4. Force-stop Spotify and reopen it

## Build from source

In the cloned repo:

```bash
./gradlew assembleRelease
```

Sign with your own keystore:

```bash
apksigner sign --ks your.keystore --ks-key-alias your-alias \
  --out LSpot.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## License

GPL-3.0
