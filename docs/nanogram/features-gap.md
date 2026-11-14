# Nanogram features: on-device transcription and translation

Spec section 10: two switches on Settings > Nanogram features, both default OFF. Implemented in this round (not built).

- Translation: ML Kit Translate 17.0.3 in `NanogramOnDeviceTranslation`, hooked in front of the server request in `TranslateController`. Models download only after the switch is on, Wi-Fi only by default. Any failure sends the batch to Telegram's server translation. Scope: chat auto-translate and voice-transcript translation; the translate sheet, stories and photo translation are not hooked.
- Transcription: `NanogramOnDeviceTranscription`. The recognizer can read a stream (`EXTRA_AUDIO_SOURCE`) only from API 33, so on API 31-32 (our minSdk is 31) the switch is hidden: below 33 the on-device recognizer listens to the microphone only. Voice files not yet downloaded fall back to the server.
- Gaps: no download percentage (ML Kit gives none without internal APIs; the screen shows downloading, ready or failed); the language of a voice message is assumed to be the app language; the Premium/trial gate on the transcribe button still applies.
