# Nanogram branding on Android

Same identity as Nanogram iOS and macOS: the app is called Nanogram and shows the Comet, never Telegram's logo or
name for itself. Telegram's network and products keep their names (Telegram Premium, Stars, "anyone on Telegram").

## How it is applied (upstream files stay mostly untouched)

| What | Where |
|---|---|
| Launcher icon (adaptive + themed monochrome + legacy/round) | `TMessagesProj_App/src/main/res/mipmap-*` override upstream's resources of the same names |
| Alternate icons (Settings > Appearance > App icon) | Upstream aliases keep their ids; artwork = iOS designs: Vintage→Light, Aqua→Orbit, Premium→Glass, Turbo→Speed, Nox→Night |
| Notification icon, caller icon | `drawable-anydpi/notification.xml` (Comet silhouette), `drawable-*/ic_launcher_dr.png` |
| Intro sphere | `drawable-*/intro_tg_plane.png` (Comet) and the navy disc colour in `IntroActivity` |
| Title text and wordmark | `NanogramBranding.wordmark()` in the chat list and stories header; intro title is the text "Nanogram" |
| Strings naming the app | `NanogramBranding` (hooked into `LocaleController.getStringV2`) replaces "Telegram" with "Nanogram" in ~50 listed keys, in every language including cloud language packs; icon names are fixed |

Artwork is rendered from the shared Nanogram SVGs (`nanogram-ios/Telegram/Telegram-iOS/AlternateIconSources`):

```sh
swift Tools/dev/branding/render-android-icons.swift
```

To rebrand another string, add its key to `NanogramBranding.KEYS` and to the id list.
