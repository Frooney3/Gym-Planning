# Spotter: AI personal trainer

A single-file phone app (`index.html`) for logging workouts and planning training.

**Live app:** https://claude.ai/artifact/R9cAdf9RyBrV2BzWJ8xeLz (open it on your phone and add it to your home screen)

## What it does

- **Onboarding**: your goal (strength, muscle, fat loss, endurance, general health), experience, days per week, session length, equipment, units (kg/lb), and any injuries or preferences.
- **Built-in programme engine**: makes a weekly split (full body, upper/lower, push/pull/legs) to fit your goal, schedule, time and equipment.
- **Workout logging**: every set's weight, reps and optional RPE, a rest timer, plates per side for barbell lifts, exercise swaps, and extra sets or exercises.
- **Automatic progressive overload**: weights are set from your last sessions using double progression. The load goes up after you hit the top of the rep range on every set, holds when you miss reps, drops 10% after two failed sessions in a row, and flags lifts that have stalled.
- **AI coach** (when opened from claude.ai):
  - Writes today's session from how you feel and how much time you have.
  - Writes whole new programmes around your history.
  - Reviews each workout when you finish it.
  - Answers questions in chat. Every request includes your profile, plan and full logged history.
- **Progress**: estimated 1RM trend charts, personal records, weekly sets per muscle, streaks, and the full session history.
- **Storage**: when opened on claude.ai, data is saved privately to your account, so it follows you across devices. It's also cached on the device. Opened anywhere else, it saves in the browser only. JSON backup export and import are included.

## Running elsewhere

`index.html` is self-contained. Hosting it anywhere (e.g. GitHub Pages) gives the full logging and progression engine with browser storage. AI features need the claude.ai artifact runtime.
