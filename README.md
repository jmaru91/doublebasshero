# Double Bass Hero

An Android app that trains your **double bass drum speed** with a structured, level-based routine. It combines a metronome, a session guide and a training calendar. You open it, it tells you what to play today and how fast, and it guides you set by set.

The training method follows the Art of Double Bass routine: three pillars (form, endurance, firing rate), Speed / Hybrid / Endurance days, and ten levels tied to a 4-minute non-stop 16th-note benchmark. The full rules are in [docs/DESIGN.md](docs/DESIGN.md).

## Features

- **Levels and quests**: 10 levels from 60 to 220 BPM. Your level comes from your True BPM: the speed at which you hold 4 minutes of non-stop 16ths with clean form.
- **Weekly plan**: 1 Speed, 1 Hybrid and 3 Endurance days, built around your two usual days off. You can change a single week, and missed days are re-planned with a suggested make-up day.
- **Guided sessions**:
  - a warm-up shown as R/L step grids;
  - each level's exercises, played with the day's pattern (bursts, 8ths/16ths intervals, or non-stop);
  - a BPM ramp from set to set.
- **Form decides**: you rate every set (Clean / Shaky / Broken). Each week the app recommends new start BPMs, and you confirm or adjust them.
- **Benchmark and level up**: the benchmark unlocks only when every exercise reaches the level's top BPM on Endurance days, and it is scheduled on a fresh day.
- **No-pedals mode**: for holidays and travel. Switched on in Settings, with an optional return date. Floor exercises replace Pedal Normal and keep the same session length; the benchmark waits until you're back on pedals.
- **3 + 1 cycle**: three hard weeks, then a deload week. A week with fewer than 3 sessions counts as a deload.
- **Works with the screen off**:
  - quarter-note metronome, sample-accurate, with the beat-1 accent telling you the bar type;
  - voice prompts and end-of-rest cues;
  - lock-screen controls and headphone-button support.
- **Crash-safe**: the session is saved after every step. If the phone dies mid-set, you can resume.
- **Reminders**: a reminder time per weekday, an evening missed-session check, week review, benchmark and deload notices.

## Requirements

- JDK 17
- Android SDK with platform 34 (`local.properties` → `sdk.dir=…`)
- A device or emulator running Android 8.0 (API 26) or later

## Build and run

```bash
./gradlew :engine:test          # training-rule unit tests (plain JVM, fast)
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:assembleRelease  # release APK (signed with the debug key)
adb install -r app/build/outputs/apk/release/app-release.apk
```

APKs are written to `app/build/outputs/apk/<debug|release>/`.

## Project structure

```
engine/   Pure Kotlin module with every training rule, no Android dependencies.
          Levels, week planning, session builder and runner, weekly review,
          cap and benchmark rules, cycle, resume policy, notification planner.
app/      Android app (Kotlin, Jetpack Compose, Room).
  data/     Room database and repository.
  audio/    AudioTrack metronome and text-to-speech.
  session/  Session controller and foreground service (lock screen, headset).
  notify/   Reminder scheduling and notification channels.
  ui/       Compose screens.
docs/     DESIGN.md (training rules and screens) and PLAN.md (implementation status).
```

Rules go first into `docs/DESIGN.md`, then into `engine` with a unit test, then into the app.

## Development notes

Debug builds can post the next planned reminder right away:

```bash
adb shell am broadcast -n com.doublebasshero/.notify.ReminderReceiver -a dbh.reminder.TEST
```

On phones with aggressive battery management, set the app to "Unrestricted" so the metronome keeps running with the screen off.

## Status

Version 0.2.0. Flows tested on an Android 15 emulator; installed and launched on a Fairphone 4. Still to verify: audio by ear, headphone buttons, and reminders and the week rollover at their real times. See [docs/PLAN.md](docs/PLAN.md).
