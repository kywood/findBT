# Nearo 2.0: Google Play Data safety answers (#36)

Based on the designer's draft (`design/store-v3/data-safety.md`), updated for what 2.0 actually ships.
Rule #19: no scan data leaves the phone. Play counts data as "collected" only when it leaves the device,
so everything Nearo keeps on the phone (scan results, saved devices, last seen place, tracker records)
is **not** declared as collected.

SDKs in the 2.0 build: Google Mobile Ads (AdMob) with the UMP consent form, Google Play Billing.
No Firebase in 2.0 (PM decision 2026-11-26): no analytics and no crash-reporting service.

> Before submitting: re-check Google's current Data safety disclosure page for the AdMob SDK version we
> ship and update the table if Google's list changed.

## Section 1: Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (from the ads SDK) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | No account. Users can reset or delete the advertising ID in Android settings. Data kept on the phone is removed in the app (Settings) or by uninstalling. |
| Account creation | None |
| Independent security review | No |
| Target audience | Not designed for children. 13+ (choose 16+ if the ads setup needs it). |

## Section 2: Data types

| Category › Type | Collected | Shared | Required / optional | Purposes | Source |
|---|---|---|---|---|---|
| Location › Approximate location | Yes | Yes | Required | Advertising, fraud prevention | AdMob (derived from the IP address) |
| App activity › App interactions | Yes | Yes | Required | Advertising | AdMob |
| App info and performance › Diagnostics | Yes | Yes | Required | Advertising, fraud prevention | AdMob |
| Device or other IDs | Yes | Yes | Required | Advertising, fraud prevention | Advertising ID, app set ID |

"Shared" is Yes for AdMob data because ads are served by Google and its partners. This is the
conservative answer.

### Not collected (answer No)

- **Location › Precise location.** Nearo never sends a location. The last seen place of a saved device is
  an approximate place, kept on the phone only, and can be switched off and erased in Settings. On
  Android 11 and lower the system requires the location permission for Bluetooth scanning.
- **Personal info, Financial info, Messages, Photos and videos, Audio, Files, Calendar, Contacts,
  Health and fitness, Web browsing.** Google Play handles the purchase; the app only keeps the premium
  entitlement on the device.
- **Nearby Bluetooth scan results** (device names, addresses, signal strength, maker, service UUIDs,
  paired devices), **protection mode state** and **tracker records** (kept 48 hours). All stay on the
  phone. The form has no category for them; the privacy policy and the listing say so.

## Other declarations in Play Console

| Form | Answer |
|---|---|
| Ads | Yes, contains ads |
| Advertising ID | Yes, used for advertising (added by the ads SDK) |
| Foreground service | Types `connectedDevice` and `location` (both are in the manifest): protection mode, started by the user, watches the user's own Bluetooth devices, notes the approximate place where one was last detected, and shows a permanent notification. Play asks for a description and a short video for each type. |
| Location permissions | No background location. Foreground only: Android 11 and lower for Bluetooth scanning, approximate location for last seen place. |
| Privacy policy | https://kywood.github.io/findBT/privacy/ |

## Permissions to explain

| Permission | Why |
|---|---|
| Nearby devices (BLUETOOTH_SCAN, BLUETOOTH_CONNECT) | Find nearby Bluetooth devices and show the ones paired with this phone |
| Location | Android 11 and lower: required by Android for Bluetooth scanning. Approximate location: last seen place, on the phone only |
| Notifications | Protection mode heads-up |
| Foreground service | Protection mode while the user has it on |
| Vibration | Haptic feedback while finding |
| Internet, AD_ID (added by the ads SDK) | Ads |
