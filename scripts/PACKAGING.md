# Desktop installer packaging

This project uses JDK 21 `jpackage` to create real desktop installers.

## Important platform rule

`jpackage` is platform-specific:

- Build macOS `.dmg` on macOS.
- Build Windows `.msi` on Windows.
- Build Linux `.deb` on Linux if needed.

A Mac cannot generate a real Windows MSI installer, and Windows cannot generate a real macOS DMG installer.

## macOS

Requirements:

- JDK 21 with `jpackage` available in `PATH`
- Maven

Run from the repository root:

```bash
bash scripts/package-desktop.sh
```

Output:

- `release/desktop/*.dmg`
- copied to `web/public/downloads/nod.dmg`

## Windows

Requirements:

- JDK 21 with `jpackage` available in `PATH`
- Maven
- WiX Toolset installed and available to `jpackage` for MSI generation

Run from PowerShell at the repository root:

```powershell
.\scripts\package-desktop.ps1
```

Output:

- `release/desktop/*.msi`
- copied to `web/public/downloads/nod-windows-latest.msi`

## Web download links

The web download page exposes both desktop installers:

- macOS: `web/public/downloads/nod.dmg`
- Windows: `web/public/downloads/nod-windows-latest.msi`

Before deploying the web app, make sure both files exist in `web/public/downloads/`. If the Windows MSI is missing, build it on Windows with `scripts/package-desktop.ps1`, then rebuild/redeploy the web app so the file is served from `/downloads/nod-windows-latest.msi`.
