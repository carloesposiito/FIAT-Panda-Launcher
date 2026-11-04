# Panda Launcher

Launcher Android per l'autoradio della Fiat Panda 169: app a sinistra, Panda 3D che ruota al centro, dati del motore (OBD) a destra.
Progetto in **Java**, rendering 3D con [Filament](https://github.com/google/filament) di Google.

## Struttura

```
app/src/main/
  java/it/panda/launcher/
    LauncherActivity.java     schermata Home (tre colonne)
    SettingsActivity.java     scelta adattatore OBD, motore, rotazione
    Prefs.java                impostazioni salvate
    car/CarRenderer.java      auto 3D: caricamento .glb, rotazione, tocco, 30 fps
    car/StudioEnvironment.java luci da showroom generate in codice (riflessi sulla vernice)
    apps/                     elenco delle app installate
    obd/                      ELM327 via Bluetooth, PID standard, stima consumi e marcia
    ui/                       pedana sotto l'auto, grafico dei giri
  assets/models/panda.glb     modello 3D (Panda 169 nera, ridotta a ~49k triangoli)
app/src/test/                 test JUnit della parte OBD
tools/genera-modello/         script che genera il panda.glb provvisorio
prototipo/mockup.html         prototipo web della schermata
```

## Aprire e installare

1. Android Studio → **Open** → questa cartella. Al primo avvio scarica Gradle 8.13 e le librerie.
   Se segnala che manca il "Gradle wrapper", accetta di crearlo.
2. Collega l'autoradio al PC (USB con debug attivo, oppure `adb connect <ip>` via Wi-Fi) e premi **Run**.
   In alternativa: **Build → Build APK(s)** e copia l'APK su una chiavetta.
3. Sull'autoradio premi Home e scegli **Panda Launcher** → "Sempre".
   Se non compare la scelta: Impostazioni → App → App predefinite → App Home.
   Il launcher originale resta installato e si può sempre riattivare.

## Modello 3D

Il file `panda.glb` attuale deriva da un modello STL della Panda 169, ridotto a circa 49.000 triangoli
con `gltfpack -si 0.1 -sa -noq`, con normali ricalcolate, scala in metri (3,54 m di lunghezza) e un solo
materiale nero lucido. Il muso guarda verso +Z, compensato da `MODEL_YAW_OFFSET`.
Per usarne un altro (es. da Sketchfab, formato **glTF binario .glb**):

- sostituisci `app/src/main/assets/models/panda.glb`;
- tienilo leggero: meno di 100.000 triangoli e texture al massimo 2048 px;
- se il muso non guarda nella direzione giusta, modifica `MODEL_YAW_OFFSET` in `CarRenderer.java`.

Per rigenerare quello provvisorio: `cd tools/genera-modello`, poi `npm install` e `npm run genera`.

## OBD

- Associa l'ELM327 dalle impostazioni Bluetooth di Android (PIN di solito `1234` o `0000`).
- Nel launcher, icona ingranaggio in alto a destra → scegli l'adattatore e la cilindrata del motore (è sul libretto, alla voce P.1).
- Dati letti: giri, velocità, temperatura acqua e aria aspirata, pressione collettore, farfalla, carico, tensione batteria.
  Livello carburante e temperatura esterna compaiono solo se la centralina li supporta.
- Il consumo è **stimato** dalla pressione nel collettore: il Fire non ha il misuratore del flusso d'aria.
- La marcia è stimata dal rapporto velocità/giri (tabella in `GearEstimator.java`).
- Tenendo premuta l'etichetta "OBD collegato" si vede il protocollo usato o l'ultimo errore.

Porte, finestrini e climatizzatore non fanno parte dell'OBD standard: per porte e A/C servono i comandi Fiat del Body Computer (fase successiva).
