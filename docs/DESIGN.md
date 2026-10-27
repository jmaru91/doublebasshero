# Double Bass Hero — Design Document

> Status: **v0.9** (implemented; see [PLAN.md](PLAN.md)). This is a living document: refine it until the rules are settled, then implement.
> Open questions, when there are any, are listed in §9.
> Clickable prototype: https://claude.ai/artifact/9gFGxDwm85pVo62A7tDXSv

---

## 1. Vision

An Android app for drummers who want to get **faster on double bass**.
It combines in one flow:

- A **metronome** that knows which exercise you are doing (bursts, intervals, non-stop).
- A **session guide** that walks you through warm-up, sets and rests.
- A **calendar** that plans the week and records every session.
- **Video-game style levels**, anchored to an objective benchmark.

The user should not have to think about *what* to do today or *how fast*. They open the app, see today's session, press **Start**, and the app guides them exercise by exercise. At the end they rate their form and the app proposes next week's BPM.

### Guiding principle

**Form is king and decides always.**
Every progression rule goes through the user's own form rating. If form does not hold, the BPM does not go up.

---

## 2. Glossary

| Term | Meaning |
|---|---|
| **True BPM** | The highest BPM at which the user can play **4 minutes of non-stop 16th notes with clean form**. Determines the level. |
| **Benchmark** | The 4-minute non-stop 16ths test. Used to find the starting level and to level up. |
| **Level** | A True BPM range (1–10). Determines which exercises to do and how many sets. |
| **Quest** | A group of levels with a technical goal (Perfect Form, Floor Trio, 4-Stage Ankle Path, Endgame). |
| **Day type** | Speed, Hybrid, Endurance, Rest. Changes *how* the exercises are played, not *which* ones. |
| **Exercise** | A technical movement (Floor Hip, Floor Heel-Up, …, Pedal Normal). |
| **Set** | One timed block of an exercise (e.g. 2 min). |
| **Start BPM** | The BPM of the first set of an exercise, per exercise **and** per day type. Updated weekly. |
| **Ramp** | The BPM climbs **from one set to the next** of the same exercise. It stays fixed within a set. |
| **Cap** | The top BPM of the current level. No set ever goes above it. |
| **Cycle** | 3 hard weeks + 1 deload week, managed by the app. |
| **Deload week** | The easy 4th week of a cycle: recovery from muscular and nervous-system fatigue. |

---

## 3. Training rules

### 3.1 The three pillars

```
        03 FIRING RATE   ← how fast the CNS fires the muscles from a cold start (phosphagen system, 0–10 s)
        02 ENDURANCE     ← aerobic base; refills the phosphagen system faster
        01 FORM          ← the roots: without form nothing else grows
```

### 3.2 Day types

| Type | Metronome pattern | Intent | Rest |
|---|---|---|---|
| **Speed** ⚡ | 1 bar of 16ths, then 3 bars of rest, repeat | Fresh, zero fatigue, full recovery | **Always long**, between sets too |
| **Hybrid** | 1 bar of 8ths, then 1 bar of 16ths, repeat | Intervals | Short between sets, long between exercises |
| **Endurance** ∞ | Non-stop 16ths | Little rest, fatigue on purpose | Short between sets, long between exercises |
| **Rest** | — | No leg work at all | — |

Reference split (Olympic pursuit rider): **70% endurance · 22% hybrid · 8% speed**.

### 3.3 The week

Every week has **1 Speed, 1 Hybrid, 3 Endurance, 2 Rest**, in the order **Speed → Hybrid → Endurance → Rest**.

The user does **not** pick from templates. The app **recommends** a week based on the days the user can rest, following these guidelines:

- Speed always comes right after a rest day, so it starts fresh.
- Speed, Hybrid and the first Endurance fall on consecutive days.
- The remaining Endurance days fill the other training days.

Examples of what the app produces:

```
Rest Thu + Sun   →   S  H  E  R  E  E  R
Rest Sat + Sun   →   S  H  E  E  E  R  R
Rest Mon + Fri   →   R  S  H  E  R  E  E
```

### 3.4 Levels

| Lvl | True BPM | Quest | Exercises (in order) | Sets | Session (ref.) |
|---|---|---|---|---|---|
| 1 | 60–69 | Q1 Perfect Form | Floor Hip · Floor Heel-Up · Floor Heel-Down · Pedal | 3 × 2 min ramp | 37:30 |
| 2 | 70–79 | Q1 Perfect Form | same | 3 × 2 min ramp | 37:30 |
| 3 | 80–99 | Q1 Perfect Form | same | 3 × 2 min ramp | 37:30 |
| 4 | 100–119 | Q2 Floor Trio | same | 3 × 2 min ramp | 37:30 |
| 5 | 120–139 | Q2 Floor Trio | same | 3 × 2 min ramp | 37:30 |
| 6 | 140–159 | Q3 4-Stage Ankle Path | Floor Elevated Taps · Pedal Elevated Taps · Pedal | 3 × 3 min ramp | 37:00 |
| 7 | 160–179 | Q3 4-Stage Ankle Path | same | 4 × 3 min ramp | 47:00 |
| 8 | 180–199 | Endgame | Pedal | 8 × 3 min ramp | 28:40 |
| 9 | 200–209 | Endgame | Pedal | 10 × 3 min ramp | 35:20 |
| 10 | 210–220 | Endgame | Pedal | 10 × 4 min ramp | 45:20 |

"Sets" are per exercise (Lvl 1: 4 exercises × 3 sets × 2 min = 24 min of work).

**Level up:** at level N with range `[min, max]`, passing the benchmark at `max + 1` BPM moves the user to level N+1.

### 3.5 Exercises

| # | Exercise | Trains | Levels |
|---|---|---|---|
| 01 | Floor Hip | Upper legs | 1–5 |
| 02 | Floor Heel-Up | Calf, the slow rotation | 1–5 |
| 03 | Floor Heel-Down | Calf and shin together, the fast rotation, feet planted | 1–5 |
| 04 | Floor Elevated Taps | Elevated ankle rotation, off the floor | 6–7 |
| 05 | Pedal Elevated Taps | Elevated ankle rotation, on the pedals | 6–7 |
| 06 | Pedal Normal | Full technique in normal playing position | 1–10, **always last** |

4-Stage Ankle Path (Quest 3): Heel-Down → Floor Elevated Taps → Pedal Elevated Taps → Normal Pedal Position.

### 3.6 Session structure

1. **Warm-up (~2 min):**
   - Sticking RLRLRRRR LRLRLLLL, in **8th notes** (2 bars)
   - Sticking RLRLRRRR RRRRRRRR LRLRLLLL LLLLLLLL, in **8th notes** (4 bars)
   - Solo Leg, right foot only, in **8th notes**
   - Solo Leg, left foot only, in **8th notes**

   **Solo Leg 1 and Solo Leg 2 alternate** from one training session to the next (by the number of sessions already done), so each day plays one of them: **16 bars on the right foot and 24 on the left** (Solo Leg 1: 4 / 6 passes; Solo Leg 2: 8 / 12 passes). Both days last the same.

   The whole warm-up is in 8th notes: as written in 16ths it is too fast for a warm-up. Each written bar of 16ths becomes two bars of 8ths.

   The other exercises in the PDF (Interval, Burst, Endurance) are dropped: the day types replace them.

   **How it's played:**
   - **Whole passes, never cut mid-pattern:** each Sticking repeats its pattern at least twice and for at least 8 bars (Sticking 1: 4 passes; Sticking 2: 2 passes); the day's Solo Leg plays 16 bars right, 24 left.
   - **8th notes on quarter clicks:** the patterns are written and played in 8ths; the metronome plays quarters (accent on beat 1), so two notes per click. The highlighted note moves two steps per click.
   - **Tempo (metronome, quarters):** Sticking 1 at 65% of the True BPM, Sticking 2 at 75%, Solo Leg at 85% for **both feet** (never below 40). At True BPM 117: 76 · 87 · 99 · 99.
   - **Count-in:** one bar (4 clicks) before every exercise.
   - **Count-in:** one bar of count-in before every exercise, so you switch pattern cleanly.
   - **Weaker foot** (Settings: None / Left / Right): its Solo Leg gets one extra pass.
   - Total, count-ins included: about 2:35 at True BPM 117, ~3:40–5 min at levels 1–3, ~1:40 at level 8 (180), ~1:20 at 220, the same on Solo Leg 1 and 2 days.

   Each exercise is shown as **drum notation** on a 5-line staff: kick in the first space with stems down, 8th notes beamed in pairs (one pair per beat), 8th rests where the foot is silent, **R** or **L** under each note; on Solo Leg also hi-hat (x above the staff) and snare (third space) with stems up; and the note being played highlighted, following the metronome. Two bars per row. Patterns (transcribed from the PDF, checked by the user):

   ```
   Sticking 1 (8ths) RL RL RR RR | LR LR LL LL
   Sticking 2 (8ths) RL RL RR RR | RR RR RR RR | LR LR LL LL | LL LL LL LL
   Solo Leg 1 (R)    RR R. RR R. | RR R. RR R. | RR RR R. .. | RR RR R. ..
   Solo Leg 2 (R)    RR R. RR RR | .R R. RR R.
   ```

   The left-foot version is the same with L. Solo Leg also shows the hands as written in the PDF (hi-hat in 8ths, snare on 2 and 4), stretched to the 8th-note version: hi-hat on every beat, snare on beat 3 of each bar.
2. **Exercises** in the level's order, Pedal Normal last, each played with the day type's pattern. Every day type, Endurance included, uses the level table's sets with a ramp (no single long set).
3. **Rests** are a hint, not a timer: the app says "short rest" or "long rest", counts up the time, and the user taps **Ready** to go on. Nothing starts on its own.

Rest lengths are derived from the reference session durations. All six durations match exactly with (the course's 2:20 warm-up is now measured in passes instead, about the same length):

| Block | Hint |
|---|---|
| Warm-up | ~2:20 |
| Between sets of the same exercise | short, ~20 s (long on Speed days) |
| Between exercises | long, ~3 min |

Session duration is therefore an estimate (~37 min).

### 3.6b Starting BPMs

At placement (and after every level-up or retest) every exercise and day type **starts at the True BPM** (never above the cap); the ramp then climbs from there during the session. From then on the weekly review moves the start BPMs.

### 3.7 Ramp within a session

- Set 1 is played at the **start BPM** for that exercise and day type.
- After **each set** the user rates its form (Clean / Shaky / Broken) on the rest screen.
- The rating alone sets the next set's BPM; the user doesn't choose the step:
  - Shaky or Broken → **+0**;
  - Clean → **+2**;
  - Clean **and** the previous set of the same exercise was Clean too → **+4** (you're strong enough to push).
  - An unrated set counts as Clean for +2, never for +4.
- No set ever goes above the **cap**.

The ramp regulates itself on high levels. At levels 8–10 there are 8–10 sets of the same exercise, so a fixed step would push the last sets far above the start (+2 per set = +14 to +18 BPM). Here the climb stops as soon as a set is rated Shaky or Broken, and the level's cap stops it in any case.

### 3.8 Weekly progression

The app **recommends** next week's start BPMs from what happened in each session; the user does not pick them session by session. Progression is tracked separately for each exercise and for each day type (Speed, Hybrid, Endurance).

What each session records, per exercise:
- the form rating of every set (Clean / Shaky / Broken);
- the BPM of every set (the ramp follows the ratings).

**Exercise rating for a session** = the average of its set ratings (Clean = 2, Shaky = 1, Broken = 0):

| Average | Session rating |
|---|---|
| ≥ 1.5 | ✅ Clean |
| ≥ 0.75 | ⚠️ Shaky |
| < 0.75 | ❌ Broken |

The ramp makes the last sets the hardest; averaging over all sets keeps one Broken top set from dragging the whole exercise down.

At the end of the week, for each exercise and day type, the app looks at all of that week's sessions (1 for Speed, 1 for Hybrid, 3 for Endurance) and applies this rule (confirmed):

| This week | Recommendation |
|---|---|
| Any session rated ❌ Broken | −2 |
| Any ⚠️ Shaky session, or any Shaky/Broken set before the last one | +0 |
| All ✅ Clean | +2 |
| All ✅ Clean **and** the ramp gave a +4 in most sessions | +4 |

The result is capped at the level's maximum BPM. The user sees the recommendations in a **week review** and can accept them or adjust any of them: form decides, and the user is the judge of form.

### 3.9 The cap and day conversion

- Every day type climbs toward the level's cap.
- When **every exercise** of the level reaches the cap on a Speed day, Speed days become **Endurance days**.
- The same applies to Hybrid days.
- After a level-up the cap rises and Speed and Hybrid days come back.

Example: the level tops out at 150 and the user can already burst at 150 on every exercise but cannot hold 150 non-stop. Speed days turn into Endurance days until the benchmark is passed.

### 3.10 Prioritize weakness

Weaker exercises get **2–3× more sets**. The extra sets are taken from the strongest exercises, so the session length stays the same.

### 3.11 Benchmark (suggested by the app)

- **Unlock:** the benchmark is **locked** until **every exercise** of the level has reached the cap on Endurance days, rated Clean, in the week's Endurance sessions. Endurance is the hardest day type, so Speed and Hybrid normally get there first (and have already turned into Endurance days).
- **Trigger:** as soon as the week review sees the unlock condition, the app suggests the benchmark.
- **When:** on the next fresh day, i.e. the first training day after a rest day.
- **Format:** warm-up, then 4:00 of non-stop 16ths at `max + 1`. That is the whole session for the day.
- **Passed:** level up immediately; the new level's sessions start the next training day.
- **Not passed:** back to the normal plan; the benchmark locks again until the unlock condition holds in a later week.
- There is no way to attempt it early.

### 3.11b Redoing the True BPM test

The placement test can be redone from the level map, e.g. after a long break or an injury. It always asks for **confirmation** first, listing what happens:

- **Resets:** the level (from the new True BPM), every start BPM (back to the level's defaults), and the cycle (back to week 1).
- **Keeps:** calendar, session history and progress charts.
- **No shortcut past the benchmark:** a True BPM above the current level's cap is accepted only after passing the 4-minute test in the app. Typing a lower number is always allowed.

### 3.12 Training cycle (managed by the app)

- **3 hard weeks, then 1 deload week**, repeating. The app tracks where the user is in the cycle and shows it ("Week 2 of 3").
- **Deload week:** same days, half the sets per exercise (rounded up), every set at the start BPM (no ramp), no week review afterwards (start BPMs carry over), no benchmark. The user can switch it to a full rest week.
- The PDF's reason: fatigue builds up in the muscles and in the central nervous system; proper recovery lets you push harder every cycle.

### 3.13 Skipped days and busy weeks

A day the user did not train **counts as a rest day**. Because a skip is an extra rest, it can often be absorbed without losing anything:

- **Skip, then re-plan.** The app re-plans the rest of the week with the same rules (Speed after a rest day, S → H → E consecutive, at least 2 rest days). If a planned day off can become a training day, the app **suggests** it, and the user accepts or not, since days off may be real unavailability.

  ```
  Planned       S  H  E  R  E  E  R
  Skip Mon      R  S  H  E  E  E  R    ← suggests training on Thu instead (Mon is now the rest)
  Skip Wed      S  H  R  E  E  E  R    ← suggests training on Thu instead
  ```

- **If the week can't hold everything**, sessions are dropped in this order of priority (keep every day type alive, drop Endurance first, then Speed, then Hybrid):

  | Training days left | Week |
  |---|---|
  | 5 | S H E E E |
  | 4 | S H E E |
  | 3 | S H E |
  | 2 | H E |
  | 1 | E |

- **First week:** if the user starts mid-week, the days before the start are plain rest and the week keeps the usual days off (no make-up offers). The remaining days get Speed first, then Hybrid, then Endurance.
- **Undo a skip:** today's skip can be undone (on Today and in "Edit this week") in case it was tapped by mistake; the week is re-planned and any make-up suggestion withdrawn. Past days can't be un-skipped: without a session they already counted as rest.
- **Busy week, known in advance:** the user opens "Edit this week", marks the days they can't train, and the app plans the remaining days with the table above.
- **Effects:**
  - Skipped sessions don't count in the week review; a day type with no completed session keeps its start BPM.
  - A week with fewer than 3 completed sessions counts as an **unplanned deload week**: it restarts the cycle (the next week is "Week 1 of 3") instead of counting as a hard week. Like a planned deload, it is followed by no week review, so start BPMs carry over.

### 3.13b No-pedals mode

For periods without pedals (holidays, travel), switched on and off in Settings, like "Prioritize weak spots".

- **What you play:**

  | Levels | Exercises without pedals |
  |---|---|
  | 1–5 | Floor Hip · Floor Heel-Up · Floor Heel-Down |
  | 6–10 | Floor Heel-Down · Floor Elevated Taps |

- **Same session length:** Pedal Normal's sets are spread over the floor exercises (level 4: 4 + 4 + 4 sets; level 6: 4 + 5; level 8: 4 + 4). Extra sets go to the more advanced exercise. Weakness balancing, ramp, rests and deload work as usual.
- **Return date (optional):** when turning the mode on you can set the day you're back with your pedals; from that day the mode is off by itself. Without a date it stays on until switched off.
- **What doesn't move:**
  - Pedal Normal's start BPMs stay frozen: no Pedal Normal session, no change.
  - The benchmark is played on pedals, so it is never scheduled on a no-pedals day.
  - Speed/Hybrid days turned into Endurance stay that way: the cap rules look at the latest session in which each exercise was actually played, so floor-only sessions don't undo them.
- **What does move:** the floor exercises progress through the week review as usual, and the sessions count for the cycle.

### 3.14 Other rules from the PDF

- Recommended 3–5 training days a week, 30–45 min per session.
- Two full rest days per week are mandatory (a skipped day counts toward them).
- Beginners should go slow enough not to accumulate too much fatigue.

---

## 4. Training engine

The core of the app is a pure module with no Android dependencies. Given the user's state, it produces the plan. It can be tested in isolation, and rules change without touching the UI.

```
User state ──►  WeekPlanner      ──► the week (from the user's rest days)
                SessionBuilder   ──► exercises, sets, start BPMs, pattern
                WeaknessBalancer ──► redistributes sets
                RampController   ──► next-set BPM from the ratings (+0 / +2 / +4 after two clean sets, capped)
                BpmProgression   ──► week review: recommended start BPMs from the week's sessions
                CapEvaluator     ──► converts maxed Speed/Hybrid days to Endurance
                LevelEvaluator   ──► benchmark passed? level up?
```

### 4.1 Building a session (pseudocode)

```
session(date):
  dayType   = weekPlan[date]                     # Speed | Hybrid | Endurance | Rest
  if dayType in (Speed, Hybrid) and allExercisesAtCap(dayType): dayType = Endurance
  exercises = level.exercises                    # in order, Pedal Normal last
  sets      = WeaknessBalancer(exercises, level.setsPerExercise, history)
  for each exercise:
      bpm = startBpm[exercise][dayType]
      for each set:
          play(bpm, dayType.pattern, level.setDuration)
          rest(dayType == Speed or lastSetOfExercise ? LONG : SHORT)
          bpm = min(cap, bpm + step(rating, previousRating))   # +0 / +2 / +4
      rateForm(exercise)
```

### 4.2 Weakness balancing (proposal)

- Each exercise has a **form score**: the moving average of its last N ratings.
- The session's total set count stays constant (Lvl 1 = 12 sets).
- The weakest exercise can reach 2–3× the base sets; sets are taken from the strongest, never below 1.
- **Pedal Normal** never drops below its base sets.
- An exercise left with **fewer sets than normal** starts higher (start + 2 × the missing sets, capped), so its last set still lands where the normal ramp would arrive. A strong exercise is still trained, and recorded, at a challenging speed: 1 set → played at the top, 2 sets → start +2. Not in deload weeks.
- The user can turn balancing off, or mark an exercise as weak by hand.

---

## 5. Data model

### 5.1 Static data (shipped with the app)

```
Level     { number, minBpm, maxBpm, quest, exercises[], setsPerExercise, setDurationSec }
Exercise  { id, name, description, minLevel, maxLevel, alwaysLast }
DayType   { SPEED, HYBRID, ENDURANCE, REST }
Pattern   { SPEED: [16th×1 bar, rest×3 bars], HYBRID: [8th×1, 16th×1], ENDURANCE: [16th continuous] }
```

### 5.2 User data (Room / SQLite)

```
UserProfile      { trueBpm, level, defaultRestDays[2], createdAt }
StartBpm         { exerciseId, dayType, bpm }
ExerciseState    { exerciseId, formScore, manualWeak }
PlannedDay       { date, dayType, convertedFrom?, status (PLANNED|DONE|PARTIAL|SKIPPED|UNAVAILABLE) }
Session          { id, date, dayType, level, startedAt, endedAt, completed }
SetLog           { sessionId, exerciseId, setIndex, bpm, durationSec, restAfterSec }
SetRating        { sessionId, exerciseId, setIndex, rating (CLEAN|SHAKY|BROKEN) }
CycleState       { cycleStart, weekIndex (1–4), isDeload }
WeekOverride     { weekStart, unavailableDays[] }
RampChoice       { sessionId, exerciseId, afterSet, delta (0|2|4) }
BpmChange        { weekStart, exerciseId, dayType, fromBpm, recommendedBpm, toBpm, reason }
BenchmarkAttempt { date, bpm, passed, notes }
LevelEvent       { date, fromLevel, toLevel }
NotificationPrefs{ reminderTimeByWeekday[7], missedTime, perTypeEnabled{}, restCueEnabled, voicePromptsEnabled }
```

---

## 6. Flows and screens

### 6.1 Onboarding

1. Short intro: the pillars and "form decides".
2. **True BPM**: the user sets a BPM and can run the 4:00 benchmark with the metronome. At the end: "Was your form clean for all 4 minutes?" Yes/No. If no, retry slower (−10 BPM suggested). The user can also type a known True BPM directly.
3. Level and quest assigned from the True BPM.
4. **Your week**: the user taps their two rest days; the app shows the recommended week live.
5. **Reminders**: one reminder time for every day (default 18:00), editable per weekday (e.g. 9:00 on weekends). Then the app asks for notification permission (Android 13+), explaining what it will send.

### 6.2 Home — "Today"

```
┌──────────────────────────────────┐
│ LV 4 · Quest 2: The Floor Trio   │
│ True BPM 112  ███████░░░  → 120  │
│                                  │
│  M  T  W  T  F  S  S             │
│ [S][H][E][R][E][E][R]            │
│        ▲ today                   │
│                                  │
│  ┌────────────────────────────┐  │
│  │  ENDURANCE DAY             │  │
│  │  4 exercises · 12 sets ~37'│  │
│  │  Floor Hip   1×2'  116     │  │
│  │  ...                       │  │
│  │      [ ▶  START ]          │  │
│  └────────────────────────────┘  │
│                                  │
│ [Today] [Calendar] [Progress] [Levels] │
└──────────────────────────────────┘
```

Tapping any day of the week previews its session. Only today's session can be started; other days are previews (to move a session, use "Edit this week").

### 6.3 Session player (metronome)

```
┌──────────────────────────────────┐
│ ✕        ENDURANCE           🔊  │
│      Exercise 2 of 4 · Set 2/3   │
│        FLOOR HEEL-UP             │
│                                  │
│            114 BPM               │
│     sets so far: 112 · [114]     │
│        ● ○ ○ ○   (beats)         │
│  [16THS][16THS][16THS][16THS]    │
│                                  │
│          1:24 / 2:00             │
│  ━━━━━━━━━━━━━━━━━━━━──────      │
│  [⏸]   Next: Floor Heel-Up 3/3  [⏭] │
└──────────────────────────────────┘
```

- The day's pattern is shown: on Speed days the rest bars read "REST", on Hybrid days you see when to switch between 8ths and 16ths.
- **The metronome plays quarter notes only**, with an accent on beat 1. The user plays the 8ths or 16ths against it.
- The accent also tells the bar type without looking: a high accent opens a bar of 16ths, a lower accent opens a bar of 8ths or a rest bar (Speed and Hybrid days).
- The metronome keeps counting quarters through the rest bars.
- **Rest screen:** "Short rest" or "Long rest", the time spent resting, and a **Ready** button.
  - After every set: rate the set's form (**Clean / Shaky / Broken**).
  - Between sets of the same exercise: the next set's BPM, set by the rating (shown, not chosen).
- **Leaving a session never loses work silently.** The close button (and Android's back gesture) pauses the session and asks:
  - **Keep going** (default);
  - **End and save N sets**: saved as a **partial** session; its rated sets count toward the week review, and the calendar shows it as "Partial";
  - **Discard session** (or "Leave session" if no set was finished yet).
  For the benchmark: "Stop the test?", since it only counts if all 4 minutes are played.
- **Crash-safe:** see §6.3b.
- Buttons are large so they can be hit without looking. Screen stays on; the metronome keeps playing with the screen locked (foreground service).

### 6.3b Interrupted sessions (battery dead, app killed, phone restart)

**What is saved, and when.** The session is written to the database at every step boundary: each finished set (with its BPM), each rating, each ramp choice. A sudden shutdown loses at most the set being played. That set is **not counted**: it was not finished and not rated.

**Before starting.** If the battery is below 20% and not charging, the app warns before the session starts ("Battery 15% · this session takes ~37 min. Plug in?"), with **Start anyway** and **Cancel**.

**On reopening the app**, depending on how much time has passed:

| Time since the interruption | What the app does |
|---|---|
| Same day, < 15 min | Card on Today: "Session interrupted at Floor Heel-Down, set 3/6." **Resume** restarts the interrupted set from the beginning, at the same BPM. Also **End and save** and **Discard** |
| Same day, ≥ 15 min | Same card, but **Resume** first runs a short warm-up (the ~2 min warm-up) because the legs have gone cold, then restarts the interrupted set |
| A later day | No resume: the day is over. The finished sets are saved automatically as a **partial** session on the original day, and Today shows a one-time notice ("Wednesday's session was interrupted: 7 of 12 sets saved") |

- On a **Speed day**, the resume always includes the short warm-up: the point of Speed is starting fresh and properly warmed up.
- A **benchmark** cannot be resumed: an interrupted test doesn't count, and it stays available on the next fresh day.
- If the interruption hit the **warm-up**, resuming simply starts the warm-up over. If it hit a **rest**, the rest comes first and the re-warm-up goes right before the next set.
- If the user ignores the card and the day ends, the third row applies.

### 6.4 Session summary

- Total time, sets completed, the BPM of every set per exercise, form ratings (editable).
- No BPM decisions here: the session feeds the week review.

### 6.4b Week review

Shown on the first day of the new week, before the Speed session.

- For each day type and exercise: last week's start BPM → recommended start BPM, with the reason (e.g. "3/3 clean", "shaky on Fri").
- **Accept all** in one tap, or adjust any single value (−2 / 0 / +2 / +4).
- Skipped sessions don't count; a day type with no completed session stays the same.

### 6.4c Edit this week

- Shows the current week with planned, done and skipped days.
- Tap a remaining day to mark it **unavailable** (or available again). The app re-plans the rest of the week (§3.13) and shows the result before saving.
- Changes apply to this week only. The default days off live in Settings.

### 6.5 Calendar

- Month view; each day colored by type (blue Speed, green Hybrid, gold Endurance, neutral Rest).
- Status: planned (outline) / done (filled) / skipped (struck through).
- Converted days are marked (e.g. a Speed day turned Endurance).
- Tap a day → session details (set BPMs, form).
- Planning changes for the current week go through "Edit this week" (§6.4c).

### 6.6 Progress

- Top-set BPM over time, per exercise.
- True BPM / benchmark history.
- Each day type's BPM against the level cap, with a MAX badge when converted.
- Form score per exercise (drives weakness balancing).
- Stats: minutes trained, sessions per type, adherence.

### 6.7 Level map / Quests

- The 10 levels as a game map, grouped by quest, current level highlighted.
- Each level: BPM range, exercises, sets, estimated session length.
- **Benchmark** card at `max + 1` BPM: locked, with progress ("3 of 4 exercises at the cap on Endurance days"), until the unlock condition holds; then it shows the suggested day.

### 6.8 Notifications

Principle: **few and useful**. At most one planning notification per day, outside a session. When two fall on the same day they are merged (e.g. Monday: "Week review ready · Speed day today").

| Notification | When | Content / actions | Default |
|---|---|---|---|
| **Training reminder** | Training days, at that weekday's reminder time | "Endurance day · ~37 min · Pedal Normal starts at 112". Actions: **Start**, **Not today** (marks it skipped) | On |
| **Missed session** | Training day not done by the evening (default 21:00) | "No session yet today. There's still time." Actions: **Start**, **Not today**. The day only counts as skipped at midnight (or on **Not today**), so a late session at 22:00 still counts. The make-up suggestion comes the next morning, merged with that day's notification | On |
| **Week review ready** | First day of the week, morning (merged with the reminder) | "3 start BPMs to change." Opens the review | On |
| **Benchmark unlocked** | When the week review unlocks it | "Benchmark unlocked: Friday, 120 BPM." | On |
| **Benchmark day** | Morning of the benchmark day | "Benchmark today. Warm up and stay fresh." | On |
| **Deload / new cycle** | First day of week 4, and of the next week 1 | "Deload week: half the sets, no ramp." / "New cycle: week 1 of 3." | On |
| **Rest day** | Rest days | "Rest day: no leg work." | Off |

**During a session** (foreground service, required by Android to keep the metronome running):
- A persistent notification shows the exercise, set and BPM, with **Pause** and **Skip** buttons on the lock screen.
- **End of suggested rest:** when the rest hint runs out (~20 s / ~3 min), one gentle sound and vibration. It does not start anything; the user still taps Ready. On by default.

**Settings:** a toggle per notification type, the reminder time per weekday, the missed-session time, and Android notification channels per type so the user can also manage them from system settings.

### 6.9 Screen off

The whole session must work with the screen off and the phone on the floor or a stand.

- **Audio engine:** one continuous audio stream from a foreground service (media playback type). Clicks, cues and silence during rests are all rendered in that stream, so timing stays sample-accurate and Android doesn't suspend the app mid-rest.
- **Audio cues**, so the user never needs to look:
  - one bar of count-in clicks before every set;
  - a distinct "set done" chime;
  - the end-of-rest cue (sound + vibration);
  - **voice prompts** (Android text-to-speech): "Floor Heel-Up. Set 2 of 3. 114." / "Long rest." / "Rest is up." On by default, can be turned off.
- **Lock-screen controls** (the session's notification, usable without unlocking):

  | Moment | Notification text | Buttons |
  |---|---|---|
  | Playing a set | Exercise · Set 2/3 · 114 BPM · 1:24 left | Pause, Skip |
  | Rest, set not rated | Short rest · rate set 2 (114 BPM) | Clean, Shaky, Broken |
  | Rest, set rated | Next set: 116 BPM | Ready |
  | Session done | Rate your last set | Clean, Shaky, Broken → Summary |

- **Headphone / Bluetooth button:** one press = Pause or Ready, two presses = Skip.

### 6.10 Settings

- Metronome sound (click type) and volume. Always quarter notes, accent on beat 1.
- Rest days (re-runs the week recommendation).
- Notifications: reminder time per weekday, missed-session time, one toggle per type (§6.8).
- Session: end-of-rest cue, voice prompts.
- Training: prioritize weak spots, no-pedals mode (with optional return date).
- Weakness balancing on/off.
- Data export/import (backup).

---

## 7. Visual style

Matches the course material: dark theme, electric blue as the primary color, slanted HUD-style cards like a video game, condensed uppercase type.

| Use | Color |
|---|---|
| Speed | Blue `#4C9BFF` |
| Hybrid | Green `#37C77F` |
| Endurance | Gold `#F0B840` |
| Rest | Warm white `#E9E4D4` |
| Background | Night blue `#0F141C` |

---

## 8. Technical architecture (proposal)

| Area | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | Native Android, declarative UI |
| Metronome | `AudioTrack` streaming pre-rendered clicks, scheduled at sample level | A timer/Handler metronome drifts; sample accuracy is mandatory at 200+ BPM |
| Background | Foreground Service (media playback type) | Metronome keeps running with the screen off; lock-screen controls |
| Scheduled notifications | WorkManager / inexact AlarmManager | Reminders don't need to-the-second precision; no exact-alarm permission needed |
| Storage | Room (SQLite) | Calendar and history offline |
| Training engine | Pure Kotlin module | Rules testable in isolation |
| Charts | Vico (or a Compose equivalent) | |

No backend: the app works fully offline.

---

## 9. Open questions

None right now.

Settled: 3 Endurance days; app-recommended week (no templates); app-recommended weekly progression (§3.8); one cap per level; day conversion only when **all** exercises hit the cap (Speed and Hybrid); ramp per set set by the ratings (+0 / +2 / +4 after two clean sets); rests as hints, always long on Speed days; warm-up includes Solo Leg; Endurance days use the normal ramp; form rated after every set and averaged per exercise; benchmark locked until every exercise hits the cap on Endurance days, suggested on the next fresh day; 3+1 cycle managed by the app, deload = half the sets with no ramp (full rest optional); a week with fewer than 3 sessions counts as an unplanned deload; skipped days count as rest and the app suggests make-up days; busy-week priority S H E E E → S H E E → S H E → H E → E; notifications per §6.8 (per-weekday reminder times, missed-session at 21:00, end-of-rest cue on); full session usable with the screen off (§6.9); interrupted sessions resume within the day, with a re-warm-up after 15 min (§6.3b).
