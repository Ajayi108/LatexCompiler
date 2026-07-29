# Bundled Tools

Place platform-specific command-line tools here when preparing a release that should not download them on first use.

Windows layout:

```text
tools/
  windows/
    tectonic.exe
    pandoc.exe
```

The app also supports downloading missing tools into its app-managed data directory after the user approves the prompt.

