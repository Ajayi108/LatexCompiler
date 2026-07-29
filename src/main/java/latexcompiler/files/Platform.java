package latexcompiler.files;

import java.util.Locale;

public enum Platform {
    WINDOWS("windows", ".exe"),
    MAC("macos", ""),
    LINUX("linux", ""),
    UNKNOWN("unknown", "");

    private final String folderName;
    private final String executableSuffix;

    Platform(String folderName, String executableSuffix) {
        this.folderName = folderName;
        this.executableSuffix = executableSuffix;
    }

    public static Platform current() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WINDOWS;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return MAC;
        }
        if (os.contains("nux") || os.contains("nix")) {
            return LINUX;
        }
        return UNKNOWN;
    }

    public String folderName() {
        return folderName;
    }

    public String executableSuffix() {
        return executableSuffix;
    }
}

