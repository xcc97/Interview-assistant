Place desktop installer artifacts here before deploying the web site.

Expected filenames:
- nod.dmg
- nod-windows-latest.msi

Build scripts:
- macOS: scripts/package-desktop.sh
- Windows: scripts/package-desktop.ps1

Notes:
- jpackage creates platform-specific installers. Build .dmg on macOS and .msi on Windows.
- Do not commit large installer binaries unless your release process requires it.
