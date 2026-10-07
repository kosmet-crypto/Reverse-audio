# Reverse Voice

Single-file web app (`index.html`): record your voice, play it backwards, and a pass-the-phone party game.
Languages: English, Norsk, Српски. Works on GitHub Pages and in an Android WebView.

Scoring constants are at the top of the `<script>` in `index.html`.

## Android app
`android/` is a WebView wrapper (microphone permission, native Share for the WAV file, back button).
GitHub Actions builds `reverse-voice.apk` on every PR (artifact) and publishes a Release on every push to `main`:
https://github.com/kosmet-crypto/Reverse-audio/releases/latest/download/reverse-voice.apk
