# LatexCompiler

A local desktop LaTeX editor and exporter for Windows first, with a path to macOS and Linux later.

The app is designed so end users install only the app. It can use bundled tools or prompt the user to download required tools into the app-managed data folder.

## Current Features

- Create, open, edit, and save `.tex` files
- Edit LaTeX with syntax colors and line numbers
- Export to PDF with Tectonic
- Preview the compiled PDF inside the app
- Show or hide export logs
- Export to Word `.docx` with Pandoc
- Export to PowerPoint `.pptx` with Pandoc
- Shows export logs inside the app
- Prompts before downloading missing command-line tools
- Opens generated exports with the system viewer

## Tool Strategy

The app resolves tools in this order:

1. Bundled tools under `tools/<platform>/`
2. App-managed tools under the user data directory
3. Developer tools already available on `PATH`

For Windows, the bundled layout is:

```text
tools/
  windows/
    tectonic.exe
    pandoc.exe
```

If a required tool is missing, the app can prompt to download the latest matching GitHub release asset into its own app data folder.

## Requirements for Developers

- JDK 21 or newer
- Gradle 8 or newer is optional
- WiX Toolset is needed only when creating a Windows `.exe` installer locally

End users do not need to install Java when the app is packaged with `jpackage`.

## Run Locally

```powershell
gradle run
```

If you do not have Gradle, run with:

```powershell
.\scripts\run-dev.ps1
```

If PowerShell blocks scripts, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-dev.ps1
```

Or compile a jar with:

```powershell
.\scripts\build-jar.ps1
```

## Build

```powershell
gradle clean build
```

## Create a Windows Installer

Install JDK 21, then run:

```powershell
.\scripts\package-windows.ps1
```

The installer output is written to `installer/`.

## GitHub Releases

The workflow in `.github/workflows/release.yml` builds a Windows installer when you push a tag like:

```text
v0.1.0
```

The generated `.exe` installer is uploaded to the GitHub Release.

## Roadmap

- Google Drive sync with user-approved OAuth
- Project folder support for images, `.bib`, style files, and templates
- Better LaTeX diagnostics with clickable error lines
- Built-in PDF preview
- macOS and Linux packaging
