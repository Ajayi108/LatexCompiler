package latexcompiler.tools;

import latexcompiler.files.Platform;

import java.util.regex.Pattern;

public enum ToolType {
    TECTONIC(
        "Tectonic",
        "tectonic",
        "tectonic-typesetting/tectonic",
        Pattern.compile(".*x86_64-pc-windows-msvc\\.zip")
    ),
    PANDOC(
        "Pandoc",
        "pandoc",
        "jgm/pandoc",
        Pattern.compile("pandoc-.*-windows-x86_64\\.zip")
    );

    private final String displayName;
    private final String commandName;
    private final String githubRepository;
    private final Pattern assetNamePattern;

    ToolType(String displayName, String commandName, String githubRepository, Pattern assetNamePattern) {
        this.displayName = displayName;
        this.commandName = commandName;
        this.githubRepository = githubRepository;
        this.assetNamePattern = assetNamePattern;
    }

    public String displayName() {
        return displayName;
    }

    public String folderName() {
        return commandName;
    }

    public String executableName() {
        return commandName + Platform.current().executableSuffix();
    }

    public String githubRepository() {
        return githubRepository;
    }

    public Pattern assetNamePattern() {
        return assetNamePattern;
    }
}

