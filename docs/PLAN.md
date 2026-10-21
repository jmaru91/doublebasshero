# Double Bass Hero — Implementation Plan

Source of truth for the rules: [DESIGN.md](DESIGN.md). This file tracks how they become code.

## Stack

| Piece | Choice |
|---|---|
| Build | Gradle 8.7 wrapper, AGP 8.3.2, Kotlin 2.0.0, JDK 17 |
| UI | Jetpack Compose (BOM 2024.06), Material 3, Navigation Compose |
| Storage | Room 2.6 (KSP), kotlinx.serialization for the in-progress session snapshot |
| Audio | `AudioTrack` stream rendered sample by sample in a foreground service; Android TTS for voice prompts |
| Lock screen / headset | Session notification with actions, `MediaSessionCompat` for headset buttons |
| Notifications | `AlarmManager` (inexact) + boot receiver; one alarm per day event |
| Android | minSdk 26, target/compile 34 |

## Modules

- **`:engine`** (pure Kotlin/JVM, no Android): every training rule. Unit-tested on the JVM.
- **`:app`** (Android): data layer, metronome service, notifications, Compose screens.

## Phases

1. **Engine** — levels, exercises, patterns, warm-up grids; week recommendation and re-planning (skips, unavailable days, make-up, busy-week priority); session builder with weakness balancing and deload; session runner (steps, per-set rating, ramp, crash-safe snapshot, resume after interruption); rating averages, weekly review, cap/day conversion, benchmark unlock and scheduling; 3+1 cycle with short-week rule; retest rule; notification planner; calendar model that combines all of it.
2. **Data** — Room entities/DAOs, repository exposing the calendar model as flows.
3. **Metronome service** — sample-accurate quarter-note clicks with bar-type accent, chimes and rest cue in the same stream, TTS prompts, session notification with lock-screen actions, headset buttons, crash-safe snapshot after every step.
4. **Screens** — onboarding, Today, player (+ rest, rating, ramp, close dialog), summary, benchmark result, level up, week review, edit week, calendar, progress, levels, settings.
5. **Notifications** — reminder/missed/review/benchmark/cycle/rest-day scheduling, actions (Start, Not today), boot re-scheduling, permission flow.
6. **Polish** — low-battery warning, interrupted-session card, retest confirmation, accessibility pass.

Each phase ends with `./gradlew :engine:test :app:assembleDebug` green.

## Status

All six phases are implemented (v0.1.0). Verified on an Android 15 emulator: onboarding, Today, player (warm-up grids, Speed pattern, rest rating and ramp), close dialog with partial save, crash recovery after the app was killed mid-session, calendar, progress, levels, settings, edit week, lock-screen session notification, and a posted training reminder.

Not verified yet: audio and voice by ear, headset buttons, reminders firing at their real time, the week rollover on an actual Monday.

### Debug helper

Debug builds export the reminder receiver so a reminder can be posted on demand:

```
adb shell am broadcast -n com.doublebasshero/.notify.ReminderReceiver -a dbh.reminder.TEST
```
