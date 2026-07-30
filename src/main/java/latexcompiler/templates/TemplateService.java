package latexcompiler.templates;

import latexcompiler.files.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TemplateService {
    private static final String TEMPLATE_EXTENSION = ".tex";

    public List<LatexTemplate> loadTemplates() throws IOException {
        List<LatexTemplate> templates = new ArrayList<>(builtInTemplates());
        templates.addAll(loadCustomTemplates());
        templates.sort(Comparator
            .comparing(LatexTemplate::category, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(LatexTemplate::name, String.CASE_INSENSITIVE_ORDER));
        return templates;
    }

    public Path templatesDirectory() {
        return AppPaths.templatesDirectory();
    }

    public Path saveCustomTemplate(String name, String body) throws IOException {
        Files.createDirectories(templatesDirectory());
        String fileName = safeFileName(name) + TEMPLATE_EXTENSION;
        Path target = uniqueTemplatePath(fileName);
        Files.writeString(target, body.strip() + System.lineSeparator(), StandardCharsets.UTF_8);
        return target;
    }

    public void updateCustomTemplate(LatexTemplate template, String body) throws IOException {
        if (template == null || !template.custom() || template.sourcePath() == null) {
            throw new IOException("Only custom templates can be edited.");
        }

        Files.writeString(template.sourcePath(), body.strip() + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public void deleteCustomTemplate(LatexTemplate template) throws IOException {
        if (template == null || !template.custom() || template.sourcePath() == null) {
            throw new IOException("Only custom templates can be deleted.");
        }

        Files.deleteIfExists(template.sourcePath());
    }

    private List<LatexTemplate> builtInTemplates() {
        return List.of(
            new LatexTemplate(
                "Professional Table",
                "Tables",
                "A captioned table using booktabs.",
                "\\usepackage{booktabs}",
                """
                \\begin{table}[ht]
                    \\centering
                    \\caption{Model comparison}
                    \\label{tab:model-comparison}
                    \\begin{tabular}{lrrr}
                        \\toprule
                        Model & Accuracy & Precision & Recall \\\\
                        \\midrule
                        Baseline & 0.82 & 0.80 & 0.78 \\\\
                        Improved & 0.91 & 0.90 & 0.88 \\\\
                        Final & 0.94 & 0.93 & 0.92 \\\\
                        \\bottomrule
                    \\end{tabular}
                \\end{table}
                """,
                false
            ),
            new LatexTemplate(
                "Simple Table",
                "Tables",
                "A plain table that does not require extra packages.",
                "",
                """
                \\begin{center}
                    \\begin{tabular}{|l|c|c|}
                        \\hline
                        Item & Quantity & Status \\\\
                        \\hline
                        Draft & 1 & Done \\\\
                        Review & 2 & Pending \\\\
                        Publish & 1 & Planned \\\\
                        \\hline
                    \\end{tabular}
                \\end{center}
                """,
                false
            ),
            new LatexTemplate(
                "Bar Graph",
                "Graphs",
                "A simple in-document bar chart using colored rules.",
                "\\usepackage{xcolor}",
                """
                \\begin{figure}[ht]
                    \\centering
                    \\caption{Export quality by target}
                    \\label{fig:export-quality}
                    \\begin{tabular}{ll}
                        PDF & \\textcolor{blue}{\\rule{7.2cm}{0.8em}} 96\\% \\\\
                        Word & \\textcolor{green}{\\rule{5.6cm}{0.8em}} 74\\% \\\\
                        Slides & \\textcolor{orange}{\\rule{4.3cm}{0.8em}} 58\\% \\\\
                    \\end{tabular}
                \\end{figure}
                """,
                false
            ),
            new LatexTemplate(
                "PGFPlots Line Graph",
                "Graphs",
                "A line chart for users who want editable plot axes.",
                """
                \\usepackage{pgfplots}
                \\pgfplotsset{compat=1.18}
                """,
                """
                \\begin{figure}[ht]
                    \\centering
                    \\begin{tikzpicture}
                        \\begin{axis}[
                            width=0.82\\linewidth,
                            xlabel={Iteration},
                            ylabel={Score},
                            grid=major
                        ]
                            \\addplot coordinates {(1,62) (2,71) (3,78) (4,86) (5,91)};
                        \\end{axis}
                    \\end{tikzpicture}
                    \\caption{Score over time}
                    \\label{fig:score-over-time}
                \\end{figure}
                """,
                false
            ),
            new LatexTemplate(
                "Resume Header",
                "Resume",
                "A compact CV/resume contact header.",
                "\\usepackage{hyperref}",
                """
                \\begin{center}
                    {\\LARGE \\textbf{Your Name}}\\\\
                    City, Country \\quad | \\quad +49 000 000000 \\\\
                    \\href{mailto:you@example.com}{you@example.com} \\quad | \\quad
                    \\href{https://github.com/yourname}{github.com/yourname}
                \\end{center}
                """,
                false
            ),
            new LatexTemplate(
                "Resume Experience",
                "Resume",
                "A CV/resume experience block with bullet points.",
                "",
                """
                \\section{Professional Experience}

                \\textbf{Software Developer} \\hfill January 2026 -- Present\\\\
                Company Name \\hfill Remote
                \\begin{itemize}
                    \\item Built and maintained reliable application features for real users.
                    \\item Improved internal workflows through automation and clear documentation.
                    \\item Collaborated with stakeholders to translate requirements into working software.
                \\end{itemize}
                """,
                false
            ),
            new LatexTemplate(
                "Resume",
                "Resume",
                "A small CV/resume scaffold with sections.",
                "\\usepackage{hyperref}",
                """
                \\begin{center}
                    {\\LARGE \\textbf{Your Name}}\\\\
                    City, Country \\quad | \\quad \\href{mailto:you@example.com}{you@example.com}
                \\end{center}

                \\section*{Professional Summary}
                Concise summary of your experience, strengths, and target role.

                \\section*{Skills}
                Python, Java, SQL, Git, LaTeX, technical writing

                \\section*{Experience}
                \\textbf{Software Developer} \\hfill 2026 -- Present\\\\
                Company Name
                \\begin{itemize}
                    \\item Built reliable software features and improved internal workflows.
                    \\item Documented technical decisions so future maintenance is easier.
                \\end{itemize}

                \\section*{Education}
                Degree Name \\hfill Expected 2027
                """,
                false
            ),
            new LatexTemplate(
                "Equation Block",
                "Math",
                "Aligned equations with labels for references.",
                "\\usepackage{amsmath}",
                """
                \\begin{align}
                    y &= Xw + \\epsilon \\label{eq:linear-model} \\\\
                    \\hat{w} &= (X^T X)^{-1}X^T y \\label{eq:least-squares}
                \\end{align}

                Equation~\\ref{eq:least-squares} gives the ordinary least-squares estimate.
                """,
                false
            ),
            new LatexTemplate(
                "Figure",
                "Figures",
                "A figure block for an image in the project folder.",
                "\\usepackage{graphicx}",
                """
                \\begin{figure}[ht]
                    \\centering
                    \\includegraphics[width=0.8\\linewidth]{image-file-name}
                    \\caption{Describe the image here.}
                    \\label{fig:example-image}
                \\end{figure}
                """,
                false
            )
        );
    }

    private List<LatexTemplate> loadCustomTemplates() throws IOException {
        Path directory = templatesDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        List<LatexTemplate> templates = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + TEMPLATE_EXTENSION)) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    String name = displayName(file.getFileName().toString());
                    String body = Files.readString(file, StandardCharsets.UTF_8);
                    templates.add(new LatexTemplate(name, "Custom", "Custom template from " + file.getFileName(), "", body, true, file));
                }
            }
        }
        return templates;
    }

    private Path uniqueTemplatePath(String fileName) {
        Path directory = templatesDirectory();
        Path target = directory.resolve(fileName);
        if (!Files.exists(target)) {
            return target;
        }

        String baseName = fileName.substring(0, fileName.length() - TEMPLATE_EXTENSION.length());
        for (int index = 2; index < 1000; index++) {
            Path candidate = directory.resolve(baseName + "-" + index + TEMPLATE_EXTENSION);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return directory.resolve(baseName + "-" + System.currentTimeMillis() + TEMPLATE_EXTENSION);
    }

    private String safeFileName(String name) {
        String safe = name.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._ -]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-");
        return safe.isBlank() ? "template" : safe;
    }

    private String displayName(String fileName) {
        String name = fileName.endsWith(TEMPLATE_EXTENSION)
            ? fileName.substring(0, fileName.length() - TEMPLATE_EXTENSION.length())
            : fileName;
        String withSpaces = name.replace('-', ' ').replace('_', ' ').trim();
        if (withSpaces.isBlank()) {
            return "Custom Template";
        }
        return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
    }
}
