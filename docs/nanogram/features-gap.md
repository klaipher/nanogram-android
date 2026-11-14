# Nanogram features: on-device transcription and translation (gap)

Spec section 10 asks for two switches on Settings > Nanogram features, both default OFF. They are not implemented on Android yet.

Why: both need new code inside upstream flows that cannot be compiled or tried in this round.

- On-device voice transcription: `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31, our minSdk) in the voice-message transcription flow, with the language pack check (`checkRecognitionSupport`, `triggerModelDownload`), download progress and errors, and a fallback to Telegram's server transcription when off or unavailable.
- On-device translation: ML Kit Translate (`com.google.mlkit:translate`, owner confirmed 2026-11-14) in `TranslateController`, models downloaded only after the switch is turned on, server translation when off or unavailable. Adds a Google dependency and about 30 MB per language pair.

Not added as inert switches, so the screen never shows a control that does nothing. Next step: implement behind `NanogramFeatures` flags (default false), add them to `NanogramFeaturesActivity` with descriptions, then check with the combined build.
