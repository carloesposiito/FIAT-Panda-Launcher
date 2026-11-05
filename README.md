# Panda Launcher

Launcher Android per l'autoradio della Fiat Panda 169, su tre colonne:

- **sinistra**: icone delle app scelte, poi (sotto un separatore) *spegni schermo* e *impostazioni*;
- **centro**: barra della radio (frequenza, stazione, volume), Panda 3D da ruotare, marcia / velocità / giri;
- **destra**: ora, giorno, temperatura esterna, dati dell'auto, consumi con viaggio e trip.

Progetto in **Java**, rendering 3D con [Filament](https://github.com/google/filament) di Google.

## Struttura

```
app/src/main/
  java/it/panda/launcher/
    LauncherActivity.java     schermata Home (tre colonne)
    SettingsActivity.java     app della colonna, radio, adattatore OBD, motore, rotazione
    AppPickerActivity.java    scelta e ordine delle app della colonna
    ScreenOffActivity.java    "spegni lo schermo" (schermata nera, un tocco la richiude)
    Prefs.java                impostazioni salvate
    car/CarRenderer.java      auto 3D: caricamento .glb, rotazione, tocco, 30 fps
    car/StudioEnvironment.java luci da showroom generate in codice (riflessi sulla vernice)
    apps/                     elenco delle app installate
    media/                    lettura della notifica della radio (frequenza e stazione)
    obd/                      ELM327 via Bluetooth, PID standard, consumi, marcia, computer di bordo
    ui/                       pedana sotto l'auto, schema del cambio, righe dati
  assets/models/panda.glb     modello 3D (per ora provvisorio)
app/src/test/                 test JUnit (OBD, trip, testi della radio)
tools/genera-modello/         script che genera il panda.glb provvisorio
prototipo/mockup.html         prototipo web della prima versione della schermata
```

## Aprire e installare

1. Android Studio → **Open** → questa cartella. Al primo avvio scarica Gradle 8.13 e le librerie.
   Se segnala che manca il "Gradle wrapper", accetta di crearlo.
2. Collega l'autoradio al PC (USB con debug attivo, oppure `adb connect <ip>` via Wi-Fi) e premi **Run**.
   In alternativa: **Build → Build APK(s)** e copia l'APK su una chiavetta.
3. Sull'autoradio premi Home e scegli **Panda Launcher** → "Sempre".
   Se non compare la scelta: Impostazioni → App → App predefinite → App Home.
   Il launcher originale resta installato e si può sempre riattivare.

## Colonna app

- Impostazioni (ingranaggio in basso a sinistra) → **Scegli e ordina le app**: si spuntano le app e si ordinano con le frecce.
- Il numero di icone dipende dall'altezza dello schermo: la Home lo calcola e l'editor lo mostra
  (le app oltre quel numero restano in elenco, sbiadite, ma non si vedono).
- Finché non si sceglie nulla compaiono le prime app in ordine alfabetico.
- Pressione lunga su un'icona: informazioni sull'app.
- **Spegni schermo**: schermata nera con luminosità al minimo, la radio continua; un tocco riaccende.

## Radio

La barra in alto legge la **notifica** dell'app radio. Android lo permette solo dopo aver dato
al launcher l'**accesso alle notifiche** (Impostazioni del launcher → Radio → *Apri accesso alle notifiche*).
Se l'autoradio non ha quella pagina, si può abilitare dal PC:

```
adb shell cmd notification allow_listener it.panda.launcher/it.panda.launcher.media.RadioNotificationListener
```

- L'app radio viene riconosciuta da sola; se sbaglia, sceglila in Impostazioni → Radio → *Scegli l'app radio*.
- Se la radio scrive solo la frequenza (es. "101.30 MHz"), **tieni premuta la barra** per dare un nome
  alla stazione: viene ricordato per quella frequenza.
- Tocco sulla barra: apre la radio. Volume: tasti − e + (volume "musica" di Android).

## Modello 3D

- Trascina per ruotare (in verticale cambia l'altezza della camera), doppio tocco per tornare alla vista 3/4.
- Il tasto in alto a destra accende/spegne la rotazione automatica.

Il file `panda.glb` viene dal modello **"Fiat Panda 169" di lowendguru** su Thingiverse
(https://www.thingiverse.com/thing:7087348, licenza **CC BY-SA**), preparato e colorato con Blender
dagli script in `tools/modello-blender/`:

1. `01_prepara.py`: importa l'STL, lo porta in metri (3,54 m) col muso verso +X e lo alleggerisce a ~110.000 triangoli;
2. `02_colora.py`: assegna i materiali per zone (carrozzeria, vetri, fari, fanali, gomme, cerchi, plastiche) ed esporta il `.glb`.
   Il colore della carrozzeria si sceglie in fondo al comando: `nero` (predefinito), `rosso`, `bianco`, `grigio`, `blu`, `giallo`, `verde`;
3. `render_viste.py`: render di controllo da più angolazioni.

```
blender --background --python 01_prepara.py -- "<file.stl>" <cartella>
blender --background <cartella>/panda_base.blend --python 02_colora.py -- <cartella> nero
```

Per ritoccare a mano i confini delle zone: apri `tools/modello-blender/panda_colorata.blend` in Blender,
in modalità Edit seleziona le facce e assegna il materiale giusto (pannello Materiali → Assign), poi
**File → Export → glTF 2.0** (formato *glTF Binary .glb*) sopra `app/src/main/assets/models/panda.glb`.
Il modello provvisorio di prima è in `tools/modello-blender/vecchio/`.

Per usare un altro modello (formato **glTF binario .glb**):

- sostituisci `app/src/main/assets/models/panda.glb`;
- tienilo leggero: meno di 100.000 triangoli e texture al massimo 2048 px;
- se il muso non guarda nella direzione giusta, modifica `MODEL_YAW_OFFSET` in `CarRenderer.java`;
- posizione dell'auto e grandezza della pedana: `TARGET_HEIGHT` e `GROUND_RADIUS` in `CarRenderer.java`.

Per rigenerare quello provvisorio: `cd tools/genera-modello`, poi `npm install` e `npm run genera`.

## OBD

- Associa l'ELM327 dalle impostazioni Bluetooth di Android (PIN di solito `1234` o `0000`).
- Impostazioni del launcher → scegli l'adattatore e la cilindrata del motore (è sul libretto, alla voce P.1).
- Dati letti: giri, velocità, temperatura acqua e aria aspirata, pressione collettore, farfalla, carico, tensione batteria.
  Temperatura olio, livello carburante, temperatura esterna e consumo misurato compaiono solo se la centralina li supporta
  (sulla 169 spesso no: resta il trattino).
- Il consumo è **stimato** dalla pressione nel collettore: il Fire non ha il misuratore del flusso d'aria.
- La marcia è stimata dal rapporto velocità/giri (tabella in `GearEstimator.java`); la retro non è riconoscibile.
- Tenendo premuta l'etichetta "OBD" si vede il protocollo usato o l'ultimo errore.

### Consumi, viaggio e trip

- **Istantaneo**: l/100 km in movimento, l/h da fermi.
- **Viaggio**: si azzera da solo a ogni nuovo collegamento all'OBD (in pratica a ogni accensione).
- **Trip**: continua tra un'accensione e l'altra finché non lo azzeri con il cestino (chiede conferma).
- Entrambi mostrano consumo medio, distanza, tempo, velocità media (soste escluse) e litri consumati.
- La lettura OBD continua anche quando si usa un'altra app (navigatore, radio), così i conteggi restano giusti.

Porte, finestrini e climatizzatore non fanno parte dell'OBD standard: per porte e A/C servono i comandi Fiat del Body Computer (fase successiva).
