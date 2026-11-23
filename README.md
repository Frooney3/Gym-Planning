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

## Installable phone app

The repo root holds an installable version of the app (a PWA). It opens full screen from a home-screen icon and works offline on iPhone and Android.

**One-time setup (GitHub Pages):** in the repo go to **Settings → Pages**. Under *Build and deployment*, choose **Deploy from a branch**, pick the branch with the app and the **/ (root)** folder, then **Save**. After a minute or so the app is live at **https://frooney3.github.io/Gym-Planning/**.

**Install on your phone:**
- **iPhone:** open the link in Safari, tap **Share**, then **Add to Home Screen**.
- **Android:** open the link in Chrome and tap **Install app** (also in the ⋮ menu).

In the installed app:
- **AI coach:** add your own Anthropic API key in Profile (from console.anthropic.com; usage is billed to your account). The key is stored only on your phone.
- **Storage:** data is saved on the phone. Use **Export backup** regularly; the file can be imported on another phone or into the claude.ai version.

## Android app with Pixel Watch (Health Connect)

`android/` wraps the same app in a native Android shell that connects to **Health Connect**, where the Pixel Watch syncs its data through the Fitbit app. It can:
- **Rate your recovery** from last night's sleep, resting heart rate and HRV, compared with your own 7-day averages. On low-recovery days it offers a lighter session (one set fewer, about 90% weight), and the AI coach sees the same data.
- **Record workout heart rate:** attaches the average and peak heart rate from your watch to each logged session.
- **Save workouts to Fitbit:** writes finished workouts back to Health Connect as strength-training sessions.

**Get it:** every push builds the app on GitHub Actions and publishes it as the [`android-latest` release](https://github.com/Frooney3/Gym-Planning/releases/tag/android-latest). On your phone, download `spotter.apk` and open it. Allow "install unknown apps" for your browser when asked.

**Signing key (one-time, so updates install over the top):** add two repository secrets under **Settings → Secrets and variables → Actions**: `SPOTTER_KEYSTORE_B64` (the base64 keystore) and `SPOTTER_KEYSTORE_PASSWORD`. The key is never committed. Without these secrets, builds use a temporary key, and you must uninstall the old build before installing a new one. Uninstalling erases the app's data, so export a backup first.

## Development

`src/spotter.html` is the single source; it is also what's published as the claude.ai artifact. After editing it, run:

```
python3 build.py   # regenerates index.html, manifest, sw.js and icons at the root (needs Pillow)
```

`vendor/anthropic-sdk.js` is the official `@anthropic-ai/sdk` (v1.5.0) bundled for the browser with esbuild.
