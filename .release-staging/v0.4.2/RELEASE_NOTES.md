# Jarnsen Mrs Plugin 0.4.2

Certified release for **ATAK-CIV 5.6.0**.

## Changes since 0.4.0
- Multi-drawing editing is directly accessible after saving a drawing
- Direct actions for **Bearbeiten**, **Neue Zeichnung** and **Übersicht**
- Manual coordinate entry is **MGRS-only**
- Own-position following is now an explicit opt-in when **Eigenposition** is selected
- Each drawing persists its own linked ATAK marker UID
- Linked marker listeners are restored after ATAK/plugin restart
- Cancelled edits no longer create unchanged undo snapshots
- Regression/device acceptance coverage was expanded for the 0.4.2 interaction flow
- Documentation and realistic usage mock-ups were refreshed

## Compatibility
- Package: `com.jarnsen.atak.mrs.plugin`
- ATAK requirement: `com.atakmap.app@5.6.0.CIV`
- Plugin version: `0.4.2`
- Release source target: `3e83fc8d893980d2c33710a607034716e672045c`
- Maximum sector range remains fixed at **8 km**

## Certification
The published APK is the TAK Product Center-signed artifact supplied from the TAK.gov processing result.

Signer:
`CN=TAK Product Center ATAK Untrusted Plugin Release, OU=Product Center, O=TAK, L=Fort Belvoir, ST=Virginia, C=US`

SHA-256:
`247835e440248dbd21665eff4e177e98c2de230ce8389bf70bf964f1474c90d8`

## Installation
If an older **TAK.gov-signed** Jarnsen Mrs Plugin is installed, install this APK as the update. If a differently signed development/test build is installed, uninstall that build first.
