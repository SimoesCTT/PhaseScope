# PhaseScope

A phase coherence analyzer for EEG data.

Computes the Kuramoto order parameter and pairwise phase coherence between
channels of an EEG recording. All processing is local — no network, no upload.

## Features

- Load EEG data from CSV or EDF files
- Frequency band selection (delta, theta, alpha, beta, gamma)
- 4th-order Butterworth bandpass filter
- Hilbert-transform instantaneous phase
- Pairwise phase coherence matrix (N x N)
- Kuramoto order parameter r(t) timeline
- Export results as CSV

## Status

Research and educational tool. **Not a medical device.** Does not diagnose,
treat, cure, or prevent any condition. Do not use for clinical decisions.

## Building

Requires JDK 17+, Android SDK 34.

    ./gradlew assembleDebug

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
