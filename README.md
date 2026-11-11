# FIAT Panda Launcher

[🇮🇹 Italiano](README.it.md) · 🇬🇧 English

**A custom digital dashboard for a Fiat Panda 169, built from scratch as an Android launcher for a Chinese car head unit.**

The stock head unit shows a grid of icons and a music app. This project turns it into an instrument cluster: hand-drawn speedometer
and tachometer, real data read from the engine ECU, the radio frequency, engine fault diagnosis, and a full-screen radio tuner
for when the car data is not in use. Everything is written in Java with no third-party UI libraries, and designed to be read
at a glance while driving.

![Home with car data](Extra/Immagini/Screenshots/home.png)

## Two modes

The launcher has two modes, chosen in the settings (**Use OBD**):

| OBD on: two pages | OBD off: the radio |
|---|---|
| Home (speed, revs, trip) and the car data page | A single page with the radio tuner |
| ![Home](Extra/Immagini/Screenshots/home.png) | ![Radio](Extra/Immagini/Screenshots/home-radio.png) |

A Bluetooth OBD adapter left plugged in can drain the battery of a parked car, so the OBD can be switched off entirely.
In that case the car pages disappear: only the radio remains, with the date and time bar on top as always.

### OBD on, first page: speed, revs and trip in three columns

- **Speedometer** drawn entirely in code (`GaugeView`): dark dial with a gradient, 240° arc, ticks and numbers, configurable full scale.
- **Engine revs** in thousands, with a segmented bar that fills up to the shift point (configurable).
  A grey arrow blinks upward when it is time to shift up, and downward when you are driving below the minimum revs.
- **Radio frequency** above the speedometer, read from the radio app notification. Tapping it opens the radio app.
- **Top-down car image** in the middle, with the fuel level.
- **Trip computer**: distance, time, fuel used as a percentage and in litres, and (optionally) average speed and cost in euro.
  Below, a speed chart for the last half hour, with the average.

### OBD on, second page: the car and its data

The car sits in the middle, with data above and below connected by lines to points on the bodywork. On the right, in a narrow column
because it is usually empty, the fault codes read from the ECU.

![Car data and faults](Extra/Immagini/Screenshots/auto-dati-errori.png)

- **Measured**: coolant temperature, battery voltage (with a charging icon when the alternator is working), fuel level,
  intake air temperature, engine load, throttle position.
- **Estimated**: remaining fuel in litres and driving range, computed from the average consumption of the trip.
- **Faults**: a *Read alarms* button and a list with the check-engine light, stored and pending fault codes, and warnings derived
  from live values (coolant too hot, battery not charging). Fault codes are described in Italian.

### OBD off: the radio tuner

A single full-screen page, drawn like an instrument of the dashboard (`RadioTunerView`):

- the **frequency** in large type, with the band (FM or AM) and the unit, on the same red glow used behind the speedometer;
- a **tape scale** from 87.5 to 108 MHz that slides under a fixed red needle when the station changes;
- a thin, mirrored **waveform** that moves while a station is playing (it is simulated, it does not listen to the audio).

Without a station it shows *Radio FM*, with a dimmed scale. Tapping the tuner opens the radio app chosen in the settings.

**Changing station.** Swipe the scale from right to left for the next station, left to right for the previous one: the scale follows
your finger and goes back to the centre. Chinese head units each behave differently, so the control method is chosen in the settings
(see below). With *Disabled*, swiping is off and only the tap that opens the radio app is left.

### The rest

- Column of pinned apps, with settings and volume buttons.
- Icons, text and translucent boxes designed to be legible from the driver's seat on a dark screen.
- Demo mode with fake data to try the whole interface without a car or an adapter: with the OBD on it simulates the car data,
  with the OBD off it simulates a radio station (99 MHz).

The app interface is in Italian.

## Under the hood

**OBD without garage tools.** A cheap ELM327 adapter talks to the ECU over Bluetooth (RFCOMM).
The launcher requests the standard PIDs one at a time (the Fire engine ECU answers in about 280 ms per request),
parses them and builds a snapshot of the data. Reading continues while another app is in the foreground.

**A consumption figure nobody gives you, estimated well.** The Fire engine does not report fuel consumption and usually not even air flow.
`FuelEstimator` rebuilds it with the *speed-density* method: from manifold pressure, revs, intake air temperature
and engine displacement it computes the air mass, and from that the fuel (stoichiometric ratio 14.7).
`FuelCalibrator` then **calibrates itself**: it compares the estimated litres with the real drop of the fuel tank level
and corrects the model with a factor that updates slowly, ignoring noise and refuelling.
Trip cost and driving range come out of it too.

**The radio, without touching the radio.** A `NotificationListenerService` reads the playback notification of the radio app
(*transport* category or media style) and extracts the frequency. To change station, `RadioControl` tries the methods the
head unit may accept: the media session published by the radio app, a media button sent to its package, or the system media key.
The settings check which ones look available and let you test them.

**Hand-drawn graphics.** Speedometer, rev bar, speed chart, radio tuner and the lines from the car are custom `View`s drawn on a `Canvas`:
the lines follow the points on the photo whatever the screen size.

**Diagnostics.** OBD-II fault code decoding (modes 03 and 07) with a catalogue in Italian, and rules that turn
live values into warnings.

**Tested.** The parts with no Android dependencies (OBD parser, consumption estimates, calibration, trip, diagnostics, speed history)
are plain classes with JUnit tests.

## Project layout

```
FPLauncher/
  app/src/main/
    java/it/panda/launcher/
      LauncherActivity.java      home, car data page and radio page, all the screen logic
      SettingsActivity.java      apps, OBD, radio, car images, engine, speedometer, consumption
      AppPickerActivity.java     choice and order of the pinned apps
      Prefs.java                 saved settings
      obd/                       ELM327, PIDs, parser, consumption estimate, calibration, trip, diagnostics
      radio/                     radio app notification, station control (RadioControl)
      apps/                      list of installed apps
      ui/                        GaugeView, RadioTunerView, ShiftLightsView, CarLinesView, MetricCellView, chart
  app/src/test/                  JUnit tests
Extra/
  APK/                           compiled APK
  Immagini/                      car photos and screenshots
```

## Build and install

You need JDK 17 or later and the Android SDK (minSdk 24, targetSdk 34).

1. Open the `FPLauncher` folder in Android Studio, or from the command line:
   `gradle :app:assembleDebug :app:testDebugUnitTest`
2. The APK is at `FPLauncher/app/build/outputs/apk/debug/app-debug.apk`; a ready-made copy is in `Extra/APK/`.
3. Install it on the head unit (USB with debugging enabled, or from a USB stick).
4. Press Home and choose **FIAT Panda Launcher** → "Always". The original launcher stays installed and can be re-enabled.

## Settings

From the home screen, gear icon at the bottom left. The launcher is made only for the Fiat Panda 169
(AXF1A 08B engine, 1242 cm³): there is no choice of car.

| Quick links, data and radio | Radio settings |
|---|---|
| ![Settings](Extra/Immagini/Screenshots/impostazioni-1.png) | ![Radio settings](Extra/Immagini/Screenshots/impostazioni-2.png) |

- **Quick links**: choice and order of the apps in the column.
- **Data**: *Use OBD* switch, connection delay, and the choice of the ELM327 adapter already paired in Android's Bluetooth
  settings (PIN usually `1234` or `0000`).
- **Radio settings**:
  - notification access, granted from the system settings (needed to read the frequency);
  - the **radio app**, which opens when you tap the radio (Chinese head units use different package names);
  - the **control method**: *Check the radio* tests which methods look available for the chosen app; a drop-down then lets you pick
    *Disabled*, *Media session of the radio app*, *Media button to the radio app* or *System media key*,
    and two buttons (previous and next station) let you try it before using it.
- **Car**: images of the car (optional import from `Pictures\FIAT_Panda`), engine displacement, fuel type, speedometer and revs
  (full scale, shift revs, minimum and maximum revs), consumption (automatic calibration, tank capacity, detailed consumption
  with average speed and cost, fuel price).
- **Development**: demo data, choice of the default launcher, shortcut to Android settings.

## Status

A personal project, in use and under development. The interface, OBD reading and estimates are tested; reading the radio and changing
station depend on the head unit hardware (a radio app that publishes a playback notification, and a control method it accepts):
the control methods have to be tried on the real head unit.
Doors, windows and air conditioning are not part of standard OBD and would need the Fiat Body Computer commands.
