# Installing the JIW watch app on a Galaxy Watch Ultra

The watch app is a **companion display**. The phone runs the session and keeps doing all the
counting and all the Health Connect syncing exactly as before. The watch shows you which
interval you're in, how long is left, and buzzes when the phase changes — so the phone stays
in your pocket.

---

## ⚠️ Read this first: signing

**The watch APK must be signed with the same release keystore as the phone app.**

The Wear Data Layer only pairs a phone app and a watch app that share **both** a package name
and a signing certificate. A debug-signed watch APK installed alongside your release-signed
phone app will launch, render, and then sit at "No active session" forever. It looks exactly
like a bug in the sync code. It isn't.

So there are exactly two valid combinations:

| Phone app | Watch app | Works? |
| --- | --- | --- |
| Release (from CI / GitHub release) | Release (from CI) | ✅ |
| Debug (`./gradlew :app:installStandardDebug`) | Debug (`./gradlew :wear:installDebug`) | ✅ |
| Release | Debug | ❌ silently does nothing |
| Debug | Release | ❌ silently does nothing |

The keystore lives only in GitHub Actions secrets, so a signed watch APK is built **in CI**,
never locally.

---

## Getting the APK

Run the **Wear APK** workflow (Actions → Wear APK → Run workflow), or take the
`JIW Tracker Wear APK` asset from any GitHub release. Both are signed with the release key.

## Installing it

1. On the watch: **Settings → About watch → Software → tap Software version 7 times** to
   enable developer options.
2. **Settings → Developer options → ADB debugging → on**, and **Debug over Wi-Fi → on**. Note
   the IP address it shows.
3. Put the watch and your computer on the same Wi-Fi network, then:

   ```bash
   adb connect <watch-ip>:5555
   # accept the prompt on the watch
   adb -s <watch-ip>:5555 install -r jiw-tracker-wear.apk
   ```

4. Open **JIW Tracker** on the watch once and allow notifications when asked. The
   notification permission is what lets the session stay on your watch face.

---

## Using it

1. Start the session **on the phone**, as you always have.
2. Open JIW Tracker on the watch once. It picks up the running session immediately.
3. Drop your arm. The screen sleeps normally — **raise your wrist or tap to wake** and the
   display is already correct, because the watch computes the countdown itself rather than
   waiting for the phone to tell it.
4. **Tap the screen** to reveal pause / skip / stop. They hide again after six seconds so a
   glance always lands on the timer, not on buttons.

### The buzzes

You mostly won't need to look at all:

| Cue | Meaning |
| --- | --- |
| Two firm buzzes | Speed up — FAST interval starting |
| One longer soft buzz | Slow down — SLOW interval starting |
| Three buzzes, last one long | Session complete |

### Why sessions start on the phone

Apps targeting Android 12+ cannot start a foreground service from the background, and a
message arriving from the watch *is* the background. Pause, skip and stop are fine — those
reach a session that's already running. Starting is a thing you do once, standing still,
before you set off.

### If the watch loses Bluetooth mid-walk

**The countdown stays correct.** The watch was given the plan, not a video feed — it knows
where the intervals are. Only steps and calories go stale, and a small "Phone not connected"
line appears. Everything catches up when the connection returns.

---

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Watch always says "No active session" while the phone is mid-session | Signing mismatch. See the table above. This is by far the most common cause. |
| No buzzes, but the display is right | Notification permission was denied — the foreground service can't run. Reinstall or grant it in the watch's app settings. |
| Watch face shows no session chip | Same as above. |
| F-Droid build of the phone app doesn't talk to the watch | Expected. The Data Layer ships in Google Play services, which the F-Droid flavor deliberately excludes. Use the standard build. |
