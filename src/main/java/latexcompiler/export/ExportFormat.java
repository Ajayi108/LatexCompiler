package latexcompiler.export;

public enum ExportFormat {
    PDF("PDF", "pdf"),
    WORD("Word", "docx"),
    POWERPOINT("PowerPoint", "pptx");

    private final String label;
    private final String extension;

    ExportFormat(String label, String extension) {
        this.label = label;
        this.extension = extension;
    }

    public String label() {
        return label;
    }

    public String extension() {
        return extension;
    }
}

