# LaTeX Compiler

A local desktop LaTeX editor and exporter for Windows first, with a path to macOS and Linux later.

The app is designed so end users install only the app. It can use bundled tools or prompt the user to download required tools into the app-managed data folder.

## Preview

![LaTeX Compiler editor with project files, source code, outline, logs, and PDF preview](docs/images/app-preview.png)

## Current Features

- Create, open, edit, and save `.tex` files
- Start new documents from a richer LaTeX example with sections, math, tables, and charts
- Edit LaTeX with syntax colors and line numbers
- Insert, copy, and PDF-preview built-in templates for tables, graphs, equations, figures, and resume sections
- Save selected LaTeX as reusable custom templates stored in the app data folder
- Export to PDF with Tectonic
- Preview the compiled PDF inside the app
- Browse project files in a VS Code-style expandable tree
- Drag and drop files or folders in the tree to move them into different folders
- Keep the file panel scoped to the opened project folder, like a local workspace
- Navigate headings from a file outline below the project tree
- Create, rename, move, copy, and delete project files from the file panel
- Select a main `.tex` file for multi-file projects
- Find and replace source text with `Ctrl+F` and `Ctrl+H`
- Click LaTeX log line references to jump back to the editor
- Auto-refresh the file panel when project files change outside the app
- Switch between system, light, and dark mode from the View menu
- Show or hide export logs
- Export to Word `.docx` with Pandoc
- Shows export logs inside the app
- Prompts before downloading missing command-line tools
- Shows Java, Tectonic, Pandoc, project, and compile settings inside the app
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

- PowerPoint export for slide-friendly LaTeX documents
- Google Drive sync with user-approved OAuth
- macOS and Linux packaging
