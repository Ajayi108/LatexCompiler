package latexcompiler.ui;

import latexcompiler.export.ExportFormat;
import latexcompiler.export.ExportResult;
import latexcompiler.export.ExportService;
import latexcompiler.files.AppPaths;
import latexcompiler.format.LatexFormatter;
import latexcompiler.process.ProcessRunner;
import latexcompiler.synctex.SourcePosition;
import latexcompiler.synctex.SyncTexService;
import latexcompiler.templates.LatexTemplate;
import latexcompiler.templates.TemplateService;
import latexcompiler.tools.GithubReleaseClient;
import latexcompiler.tools.ToolDownloader;
import latexcompiler.tools.ToolManager;
import latexcompiler.tools.ToolType;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.imageio.ImageIO;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.plaf.basic.BasicMenuBarUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainWindow extends JFrame {
    // Auto compile waits for typing to pause so the app does not compile on every keystroke.
    private static final int DEFAULT_AUTO_COMPILE_DELAY_MS = 1500;
    private static final int MIN_AUTO_COMPILE_DELAY_MS = 500;
    private static final int MAX_AUTO_COMPILE_DELAY_MS = 10000;
    private static final int PROJECT_WATCH_DEPTH = 3;
    private static final String THEME_PREF_KEY = "theme";
    private static final String AUTO_COMPILE_DELAY_PREF_KEY = "autoCompileDelayMs";
    private static final String HIDE_GENERATED_FILES_PREF_KEY = "hideGeneratedFiles";
    private static final String THEME_SYSTEM_DEFAULT_MIGRATED_KEY = "theme.systemDefaultMigrated";
    private static final String SYSTEM_THEME_ID = "system";
    private static final String LIGHT_THEME_ID = "light";
    private static final String DARK_THEME_ID = "dark";
    private static final String MENU_CHANGE_LISTENER_KEY = "latex-compiler.menuChangeListener";
    private static final Pattern LOG_TEX_PATH_WITH_LINE = Pattern.compile("(.+?\\.tex):(\\d+)(?::\\d+)?");
    private static final Pattern WINDOWS_THEME_REGISTRY_VALUE = Pattern.compile(
        "AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)"
    );
    private static final Pattern LOG_LINE_NUMBER = Pattern.compile("(?i)(?:^|\\b)(?:line|l\\.)\\s*(\\d+)\\b");
    private static final int COLLAPSED_PROJECT_FILES_WIDTH = 28;
    private static final int COLLAPSED_PROJECT_FILES_DIVIDER_SIZE = 3;
    private static final String PROJECT_FILES_EXPANDED_CARD = "expanded";
    private static final String PROJECT_FILES_COLLAPSED_CARD = "collapsed";
    private static final String STARTER_DOCUMENT = """
        \\documentclass[11pt]{article}
        \\usepackage[utf8]{inputenc}
        \\usepackage[T1]{fontenc}
        \\usepackage[margin=1in]{geometry}
        \\usepackage{amsmath}
        \\usepackage{booktabs}
        \\usepackage{xcolor}
        \\usepackage{hyperref}

        \\title{Local LaTeX Compiler Demo}
        \\author{Your Name}
        \\date{\\today}

        \\begin{document}

        \\maketitle
        \\tableofcontents

        \\begin{abstract}
        This starter document is a compact test project for writing, formatting, compiling,
        previewing, and exporting LaTeX locally. Replace any section with your own work.
        \\end{abstract}

        \\section{Project Overview}

        This example includes headings, references, math, tables, lists, and an inline chart.
        The file outline on the left should detect this section and the ones below.

        \\subsection{Goals}

        \\begin{itemize}
            \\item Compile the document locally without cloud storage.
            \\item Preview the generated PDF beside the editor.
            \\item Export to PDF first, with Word export available when Pandoc is installed.
            \\item Keep the source readable enough to maintain months later.
        \\end{itemize}

        \\section{Mathematical Typesetting}

        Inline math should appear inside a normal paragraph. For example, the model predicts
        a value $\\hat{y}$ from an input matrix $X$ and learned weights $w$.

        \\begin{align}
            \\hat{y} &= Xw + b \\\\
            \\mathcal{L}(w) &= \\frac{1}{n}\\sum_{i=1}^{n}(y_i - \\hat{y}_i)^2
        \\end{align}

        \\section{Tables}

        Table~\\ref{tab:export-targets} shows the kind of comparison a local compiler app
        might need while the export pipeline improves.

        \\begin{table}[ht]
            \\centering
            \\caption{Export target comparison}
            \\label{tab:export-targets}
            \\begin{tabular}{lrrr}
                \\toprule
                Target & Speed & Fidelity & Notes \\\\
                \\midrule
                PDF & High & 96\\% & Best match for LaTeX output \\\\
                Word & Medium & 74\\% & Good for editing text-heavy documents \\\\
                Slides & Low & 58\\% & Best when the source is already slide-shaped \\\\
                \\bottomrule
            \\end{tabular}
        \\end{table}

        \\section{Inline Chart}

        \\begin{figure}[ht]
            \\centering
            \\caption{Expected export fidelity}
            \\label{fig:fidelity-bars}
            \\begin{tabular}{ll}
                PDF & \\textcolor{blue}{\\rule{7.2cm}{0.8em}} 96\\% \\\\
                Word & \\textcolor{green}{\\rule{5.6cm}{0.8em}} 74\\% \\\\
                Slides & \\textcolor{orange}{\\rule{4.3cm}{0.8em}} 58\\% \\\\
            \\end{tabular}
        \\end{figure}

        \\section{Next Steps}

        Use Insert > Templates to add reusable blocks like tables, graphs, figures,
        equations, and resume sections at the current cursor position.

        \\subsection{Maintenance Notes}

        A good local tool should keep its most important features obvious: open a folder,
        choose the main file, compile, read errors, and keep writing.

        \\end{document}
        """;

    private final JTextPane editor;
    private final JTextArea logs;
    private final JLabel status;
    private final JLabel fileStatus;
    private final PdfPreviewPanel pdfPreview;
    private final ProjectFilesPanel projectFilesPanel;
    private final ExportService exportService;
    private final ToolManager toolManager;
    private final SyncTexService syncTexService;
    private final LatexFormatter latexFormatter;
    private final TemplateService templateService;
    private final UndoManager undoManager;
    private final Preferences preferences;
    private final Timer autoCompileTimer;
    private final Timer projectFilesRefreshTimer;
    private LatexSyntaxHighlighter syntaxHighlighter;

    private JToolBar toolbar;
    private JMenuBar menuBar;
    private JButton undoButton;
    private JButton redoButton;
    private JButton compileButton;
    private JButton toggleFilesButton;
    private JButton toggleLogsButton;
    private JMenuItem toggleFilesMenuItem;
    private JMenuItem toggleLogsMenuItem;
    private JRadioButtonMenuItem systemThemeItem;
    private JRadioButtonMenuItem lightThemeItem;
    private JRadioButtonMenuItem darkThemeItem;
    private JToggleButton autoCompileToggle;
    private JPanel logsPanel;
    private JPanel statusBar;
    private JScrollPane editorScrollPane;
    private JTabbedPane editorTabs;
    private LineNumberView lineNumberView;
    private JDialog findDialog;
    private JTextField findField;
    private JTextField replaceField;
    private JCheckBox matchCaseBox;
    private JLabel findStatusLabel;
    private JDialog templateDialog;
    private DefaultListModel<LatexTemplate> templateModel;
    private JList<LatexTemplate> templateList;
    private JTextArea templatePreview;
    private PdfPreviewPanel templatePdfPreview;
    private JLabel templateDescriptionLabel;
    private JLabel templatePreviewStatusLabel;
    private JButton saveCustomTemplateButton;
    private JButton deleteCustomTemplateButton;
    private JButton renderTemplatePreviewButton;
    private SwingWorker<ExportResult, String> templatePreviewWorker;
    private int templatePreviewRequestId;

    private Path currentProjectRoot;
    private Path currentFile;
    private Path mainFile;
    private String themeMode;
    private UiTheme theme;
    private int autoCompileDelayMs;
    private boolean hideGeneratedFiles;
    private boolean dirty;
    // Programmatic editor changes should not mark the document as modified.
    private boolean loading;
    // Export work runs in the background; these flags prevent overlapping compiler processes.
    private boolean operationRunning;
    private boolean compileQueued;
    private CompoundEdit activeCompoundEdit;
    private JPanel projectFilesSlot;
    private CardLayout projectFilesCard;
    private JSplitPane projectWorkspaceSplit;
    private WatchService projectWatchService;
    private Thread projectWatchThread;
    private Path watchedProjectRoot;
    private final Map<Path, OpenFileTab> openFileTabs = new LinkedHashMap<>();
    private final List<Path> openFileOrder = new ArrayList<>();
    private boolean projectFilesVisible = true;
    private int projectFilesDividerLocation = 230;
    private int projectFilesDividerSize = 8;
    private boolean updatingEditorTabs;

    private record ReplaceResult(String text, int count) {
    }

    private record TemplateInsertion(String text, int insertedStart, int insertedEnd) {
    }

    private record ThemeChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class OpenFileTab {
        private final Path path;
        private String text;
        private boolean dirty;
        private int caretPosition;
        private int selectionStart;
        private int selectionEnd;

        private OpenFileTab(Path path, String text) {
            this.path = path;
            this.text = text == null ? "" : text;
        }
    }

    public MainWindow() {
        super("LaTeX Compiler");
        installWindowIcon();
        this.toolManager = new ToolManager(new GithubReleaseClient(), new ToolDownloader());
        this.exportService = new ExportService(this.toolManager, new ProcessRunner());
        this.syncTexService = new SyncTexService();
        this.latexFormatter = new LatexFormatter();
        this.templateService = new TemplateService();
        this.undoManager = new UndoManager();
        this.preferences = Preferences.userNodeForPackage(MainWindow.class);
        this.themeMode = initialThemeMode();
        this.theme = resolveTheme(themeMode);
        this.autoCompileDelayMs = loadAutoCompileDelay();
        this.hideGeneratedFiles = preferences.getBoolean(HIDE_GENERATED_FILES_PREF_KEY, true);
        this.editor = createEditor();
        this.syntaxHighlighter = new LatexSyntaxHighlighter(editor, theme);
        this.logs = createLogs();
        this.status = new JLabel("Ready");
        this.fileStatus = new JLabel("Untitled");
        this.pdfPreview = new PdfPreviewPanel();
        this.pdfPreview.setSourceNavigationHandler(this::navigateFromPdfToSource);
        this.projectFilesPanel = new ProjectFilesPanel();
        this.projectFilesPanel.setFileActionHandler(new ProjectFilesPanel.FileActionHandler() {
            @Override
            public void openFile(Path file) {
                openProjectFile(file);
            }

            @Override
            public void setMainFile(Path file) {
                setMainProjectFile(file);
            }

            @Override
            public void openOutlineLine(Path sourceFile, int lineNumber) {
                navigateFromOutline(sourceFile, lineNumber);
            }

            @Override
            public void navigateBack(Path projectRoot) {
                navigateBackProjectFolder(projectRoot);
            }

            @Override
            public void hideFilesPanel() {
                setProjectFilesVisible(false);
            }

            @Override
            public void hideGeneratedFilesChanged(boolean hideGeneratedFiles) {
                setHideGeneratedFiles(hideGeneratedFiles);
            }

            @Override
            public void createTexFile(Path projectRoot) {
                createProjectTexFile(projectRoot);
            }

            @Override
            public void createFolder(Path projectRoot) {
                createProjectFolder(projectRoot);
            }

            @Override
            public void rename(Path file) {
                renameProjectItem(file);
            }

            @Override
            public void copy(Path file) {
                copyProjectItem(file);
            }

            @Override
            public void move(Path file) {
                moveProjectItem(file);
            }

            @Override
            public void moveToFolder(Path file, Path destinationFolder) {
                moveProjectItemToFolder(file, destinationFolder);
            }

            @Override
            public void delete(Path file) {
                deleteProjectItem(file);
            }
        });
        this.projectFilesPanel.setHideGeneratedFiles(hideGeneratedFiles);
        // Swing Timer fires on the UI thread after the user stops editing for a moment.
        this.autoCompileTimer = new Timer(autoCompileDelayMs, event -> compilePdf(true));
        this.autoCompileTimer.setRepeats(false);
        this.projectFilesRefreshTimer = new Timer(700, event -> refreshProjectFiles());
        this.projectFilesRefreshTimer.setRepeats(false);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setJMenuBar(createMenuBar());
        add(createToolbar(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        installGlobalShortcuts();
        applyTheme();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                closeWindow();
            }
        });

        loadStarterDocument();
        refreshProjectFiles();
        updateTitle();
    }

    private void installWindowIcon() {
        try (InputStream iconStream = MainWindow.class.getResourceAsStream("/app-icon.png")) {
            if (iconStream == null) {
                return;
            }

            Image icon = ImageIO.read(iconStream);
            if (icon != null) {
                setIconImage(icon);
            }
        } catch (IOException ignored) {
            // A missing icon should never prevent the editor from starting.
        }
    }

    private JTextPane createEditor() {
        JTextPane area = new CodeEditorPane();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        area.setOpaque(true);
        area.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        area.setMargin(new Insets(12, 12, 12, 12));
        installEditorShortcuts(area);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                markDirty();
            }
        });
        area.getDocument().addUndoableEditListener(event -> {
            if (loading || isStyleOnlyEdit(event.getEdit())) {
                return;
            }

            if (activeCompoundEdit != null) {
                activeCompoundEdit.addEdit(event.getEdit());
            } else {
                undoManager.addEdit(event.getEdit());
            }
            updateUndoRedoButtons();
        });
        area.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent event) {
                updateCaretStatus();
            }
        });
        return area;
    }

    private JTextArea createLogs() {
        JTextArea area = new JTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        area.setToolTipText("Click a log line with a line number to jump to the LaTeX source.");
        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                navigateFromLogClick(event);
            }
        });
        area.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                area.setCursor(logReferenceAt(event.getPoint()) == null
                    ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        });
        return area;
    }

    private JMenuBar createMenuBar() {
        menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(menuItem("New", this::newFile, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)));
        fileMenu.add(menuItem("Open...", this::openFile, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)));
        fileMenu.add(menuItem("Save", this::saveFile, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)));
        fileMenu.add(menuItem("Save As...", this::saveFileAs, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        fileMenu.addSeparator();
        JMenu exportMenu = new JMenu("Export");
        exportMenu.add(menuItem("PDF...", () -> export(ExportFormat.PDF), null));
        exportMenu.add(menuItem("Word...", () -> export(ExportFormat.WORD), null));
        fileMenu.add(exportMenu);
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Exit", this::closeWindow, null));

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(menuItem("Undo", this::undo, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)));
        editMenu.add(menuItem("Redo", this::redo, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK)));
        editMenu.addSeparator();
        editMenu.add(menuItem("Find...", this::showFindDialog, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
        editMenu.add(menuItem("Find and Replace...", this::showReplaceDialog, KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK)));
        editMenu.addSeparator();
        editMenu.add(menuItem("Format LaTeX", this::formatLatex, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));

        JMenu insertMenu = new JMenu("Insert");
        insertMenu.add(menuItem("Templates...", this::showTemplatesDialog, KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));

        JMenu viewMenu = new JMenu("View");
        toggleFilesMenuItem = menuItem("Hide Files", this::toggleProjectFiles, null);
        toggleLogsMenuItem = menuItem("Hide Logs", this::toggleLogs, null);
        viewMenu.add(toggleFilesMenuItem);
        viewMenu.add(toggleLogsMenuItem);
        viewMenu.addSeparator();
        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        systemThemeItem = themeItem(SYSTEM_THEME_ID, "System");
        lightThemeItem = themeItem(LIGHT_THEME_ID, "Light");
        darkThemeItem = themeItem(DARK_THEME_ID, "Dark");
        themeGroup.add(systemThemeItem);
        themeGroup.add(lightThemeItem);
        themeGroup.add(darkThemeItem);
        themeMenu.add(systemThemeItem);
        themeMenu.add(lightThemeItem);
        themeMenu.add(darkThemeItem);
        viewMenu.add(themeMenu);

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.add(menuItem("Settings...", this::showSettingsDialog, null));

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(insertMenu);
        menuBar.add(viewMenu);
        menuBar.add(toolsMenu);
        return menuBar;
    }

    private JRadioButtonMenuItem themeItem(String mode, String label) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
        item.setSelected(themeMode.equals(mode));
        item.addActionListener(event -> setThemeMode(mode));
        return item;
    }

    private JMenuItem menuItem(String text, Runnable action, KeyStroke accelerator) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.addActionListener(event -> action.run());
        return item;
    }

    private JToolBar createToolbar() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        toolbar.add(button("New", this::newFile));
        toolbar.add(button("Open", this::openFile));
        toolbar.add(button("Save", this::saveFile));
        toolbar.addSeparator();

        // Compile is the main action: it saves the .tex file and builds the matching PDF.
        compileButton = button("Compile", () -> compilePdf(false));
        compileButton.setFont(compileButton.getFont().deriveFont(Font.BOLD));
        compileButton.setMargin(new Insets(6, 16, 6, 16));
        toolbar.add(compileButton);

        autoCompileToggle = new JToggleButton("Auto Compile Off");
        autoCompileToggle.setFocusable(false);
        autoCompileToggle.setMargin(new Insets(6, 12, 6, 12));
        autoCompileToggle.addActionListener(event -> toggleAutoCompile());
        toolbar.add(autoCompileToggle);

        toolbar.addSeparator();
        toolbar.add(button("Find", this::showFindDialog));
        toolbar.add(button("Templates", this::showTemplatesDialog));
        return toolbar;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        button.setFocusable(false);
        button.setMargin(new Insets(6, 10, 6, 10));
        return button;
    }

    private int loadAutoCompileDelay() {
        int stored = preferences.getInt(AUTO_COMPILE_DELAY_PREF_KEY, DEFAULT_AUTO_COMPILE_DELAY_MS);
        return Math.max(MIN_AUTO_COMPILE_DELAY_MS, Math.min(MAX_AUTO_COMPILE_DELAY_MS, stored));
    }

    private void setAutoCompileDelay(int delayMs) {
        autoCompileDelayMs = Math.max(MIN_AUTO_COMPILE_DELAY_MS, Math.min(MAX_AUTO_COMPILE_DELAY_MS, delayMs));
        preferences.putInt(AUTO_COMPILE_DELAY_PREF_KEY, autoCompileDelayMs);
        autoCompileTimer.setInitialDelay(autoCompileDelayMs);
        autoCompileTimer.setDelay(autoCompileDelayMs);
    }

    private void setHideGeneratedFiles(boolean hideGeneratedFiles) {
        this.hideGeneratedFiles = hideGeneratedFiles;
        preferences.putBoolean(HIDE_GENERATED_FILES_PREF_KEY, hideGeneratedFiles);
        projectFilesPanel.setHideGeneratedFiles(hideGeneratedFiles);
        refreshProjectFiles();
    }

    private String initialThemeMode() {
        if (!preferences.getBoolean(THEME_SYSTEM_DEFAULT_MIGRATED_KEY, false)) {
            preferences.put(THEME_PREF_KEY, SYSTEM_THEME_ID);
            preferences.putBoolean(THEME_SYSTEM_DEFAULT_MIGRATED_KEY, true);
            return SYSTEM_THEME_ID;
        }
        return preferences.get(THEME_PREF_KEY, SYSTEM_THEME_ID);
    }

    private void setThemeMode(String mode) {
        themeMode = mode;
        theme = resolveTheme(mode);
        preferences.put(THEME_PREF_KEY, mode);
        applyTheme();
        setStatus(themeModeLabel() + " mode");
    }

    private UiTheme resolveTheme(String mode) {
        return switch (mode) {
            case LIGHT_THEME_ID -> UiTheme.light();
            case DARK_THEME_ID -> UiTheme.dark();
            default -> detectSystemTheme();
        };
    }

    private UiTheme detectSystemTheme() {
        if (isWindows()) {
            Optional<Boolean> windowsLightMode = readWindowsLightMode();
            if (windowsLightMode.isPresent()) {
                return windowsLightMode.get() ? UiTheme.light() : UiTheme.dark();
            }
        }

        Color panelBackground = UIManager.getColor("Panel.background");
        if (panelBackground != null && colorBrightness(panelBackground) < 128) {
            return UiTheme.dark();
        }
        return UiTheme.light();
    }

    private Optional<Boolean> readWindowsLightMode() {
        try {
            Process process = new ProcessBuilder(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme"
            ).redirectErrorStream(true).start();
            boolean finished = process.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = WINDOWS_THEME_REGISTRY_VALUE.matcher(output);
            if (!matcher.find()) {
                return Optional.empty();
            }

            int value = Integer.parseUnsignedInt(matcher.group(1), 16);
            return Optional.of(value != 0);
        } catch (IOException | InterruptedException | NumberFormatException ignored) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private int colorBrightness(Color color) {
        return (int) Math.round(color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114);
    }

    private String themeModeLabel() {
        if (SYSTEM_THEME_ID.equals(themeMode)) {
            return "System (" + theme.label() + ")";
        }
        return theme.label();
    }

    private void applyTheme() {
        applyThemeDefaults();
        applyComponentTheme(getContentPane());
        getContentPane().setBackground(theme.windowBackground());
        if (menuBar != null) {
            applyComponentTheme(menuBar);
        }
        if (toolbar != null) {
            toolbar.setBackground(theme.raisedBackground());
            toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
        }
        if (statusBar != null) {
            statusBar.setBackground(theme.raisedBackground());
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.border()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
            ));
        }
        if (status != null) {
            status.setForeground(theme.text());
        }
        if (fileStatus != null) {
            fileStatus.setForeground(theme.mutedText());
        }
        if (editorScrollPane != null) {
            editorScrollPane.setBorder(titledBorder("LaTeX Source"));
            editorScrollPane.getViewport().setBackground(theme.editorBackground());
        }
        if (logsPanel != null) {
            logsPanel.setBorder(titledBorder("Logs"));
            logsPanel.setBackground(theme.panelBackground());
        }
        logs.setBackground(theme.panelBackground());
        logs.setForeground(theme.text());
        logs.setCaretColor(theme.editorCaret());
        logs.setSelectionColor(theme.editorSelection());
        logs.setSelectedTextColor(theme.editorSelectedText());
        if (syntaxHighlighter != null) {
            syntaxHighlighter.applyTheme(theme);
        }
        if (lineNumberView != null) {
            lineNumberView.applyTheme(theme);
        }
        styleEditorTabs();
        projectFilesPanel.applyTheme(theme);
        pdfPreview.applyTheme(theme);
        if (findDialog != null) {
            applyComponentTheme(findDialog.getContentPane());
        }
        if (templateDialog != null) {
            applyComponentTheme(templateDialog.getContentPane());
            styleTemplateControls();
        }
        updateAutoCompileToggleAppearance();
        if (systemThemeItem != null) {
            systemThemeItem.setSelected(SYSTEM_THEME_ID.equals(themeMode));
        }
        if (lightThemeItem != null) {
            lightThemeItem.setSelected(LIGHT_THEME_ID.equals(themeMode));
        }
        if (darkThemeItem != null) {
            darkThemeItem.setSelected(DARK_THEME_ID.equals(themeMode));
        }
        repaint();
    }

    private void applyThemeDefaults() {
        // JOptionPane buttons are painted by the Windows look and feel, which keeps a light
        // button face even in our dark app theme. Keep those dialog defaults readable.
        UIManager.put("Button.background", dialogButtonBackground());
        UIManager.put("Button.foreground", dialogButtonForeground());
        UIManager.put("Button.disabledText", theme.disabledText());
        UIManager.put("Button.disabledForeground", theme.disabledText());
        UIManager.put("ToggleButton.background", theme.raisedBackground());
        UIManager.put("ToggleButton.foreground", theme.text());
        UIManager.put("ToggleButton.disabledText", theme.disabledText());
        UIManager.put("ToggleButton.disabledForeground", theme.disabledText());
        UIManager.put("Label.foreground", theme.text());
        UIManager.put("Label.disabledForeground", theme.disabledText());
        UIManager.put("MenuBar.background", theme.raisedBackground());
        UIManager.put("MenuBar.foreground", theme.text());
        UIManager.put("Menu.background", theme.raisedBackground());
        UIManager.put("Menu.foreground", theme.text());
        UIManager.put("Menu.disabledForeground", theme.disabledText());
        UIManager.put("Menu.selectionBackground", theme.listSelectionBackground());
        UIManager.put("Menu.selectionForeground", theme.listSelectionForeground());
        UIManager.put("MenuItem.background", theme.panelBackground());
        UIManager.put("MenuItem.foreground", theme.text());
        UIManager.put("MenuItem.disabledForeground", theme.disabledText());
        UIManager.put("MenuItem.selectionBackground", theme.listSelectionBackground());
        UIManager.put("MenuItem.selectionForeground", theme.listSelectionForeground());
        UIManager.put("RadioButtonMenuItem.background", theme.panelBackground());
        UIManager.put("RadioButtonMenuItem.foreground", theme.text());
        UIManager.put("RadioButtonMenuItem.selectionBackground", theme.listSelectionBackground());
        UIManager.put("RadioButtonMenuItem.selectionForeground", theme.listSelectionForeground());
        UIManager.put("PopupMenu.background", theme.panelBackground());
        UIManager.put("PopupMenu.foreground", theme.text());
        UIManager.put("ToolBar.background", theme.raisedBackground());
    }

    private Color dialogButtonBackground() {
        return theme.darkMode() ? UiTheme.light().raisedBackground() : theme.raisedBackground();
    }

    private Color dialogButtonForeground() {
        return theme.darkMode() ? UiTheme.light().text() : theme.text();
    }

    private void applyComponentTheme(Component component) {
        if (component == null
            || component == editor
            || component == logs
            || component instanceof ProjectFilesPanel
            || component instanceof PdfPreviewPanel
            || component instanceof LineNumberView) {
            return;
        }

        component.setBackground(theme.panelBackground());
        component.setForeground(theme.text());
        if (component instanceof JToolBar) {
            component.setBackground(theme.raisedBackground());
        } else if (component instanceof JMenuBar || component instanceof JMenu || component instanceof JMenuItem) {
            styleMenuComponent(component);
        } else if (component instanceof AbstractButton button) {
            styleButton(button);
        } else if (component instanceof JTextComponent textComponent) {
            textComponent.setBackground(theme.panelBackground());
            textComponent.setForeground(theme.text());
            textComponent.setCaretColor(theme.editorCaret());
            textComponent.setSelectionColor(theme.editorSelection());
            textComponent.setSelectedTextColor(theme.editorSelectedText());
        } else if (component instanceof JList<?> list) {
            list.setBackground(theme.panelBackground());
            list.setForeground(theme.text());
            list.setSelectionBackground(theme.listSelectionBackground());
            list.setSelectionForeground(theme.listSelectionForeground());
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(BorderFactory.createLineBorder(theme.border()));
            scrollPane.getViewport().setBackground(theme.panelBackground());
        } else if (component instanceof JSplitPane splitPane) {
            splitPane.setBackground(theme.border());
            splitPane.setBorder(BorderFactory.createEmptyBorder());
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyComponentTheme(child);
            }
        }
    }

    private void styleButton(AbstractButton button) {
        UiButtons.style(button, theme, button.getMargin());
    }

    private void updateAutoCompileToggleAppearance() {
        if (autoCompileToggle == null) {
            return;
        }

        boolean selected = autoCompileToggle.isSelected();
        autoCompileToggle.setText(selected ? "Auto Compile On" : "Auto Compile Off");
        UiButtons.style(autoCompileToggle, theme, autoCompileToggle.getMargin());
    }

    private void styleMenuComponent(Component component) {
        if (component instanceof JMenuBar menuBar) {
            menuBar.setUI(new BasicMenuBarUI());
            menuBar.setOpaque(true);
            menuBar.setBackground(theme.raisedBackground());
            menuBar.setForeground(theme.text());
        } else if (component instanceof JRadioButtonMenuItem radioButtonMenuItem) {
            radioButtonMenuItem.setUI(new BasicRadioButtonMenuItemUI());
            styleReactiveMenuItem(radioButtonMenuItem, false);
        } else if (component instanceof JMenu menu) {
            menu.setUI(new BasicMenuUI());
            styleReactiveMenuItem(menu, menu.getParent() instanceof JMenuBar);
        } else if (component instanceof JMenuItem menuItem) {
            menuItem.setUI(new BasicMenuItemUI());
            styleReactiveMenuItem(menuItem, false);
        }
    }

    private void styleReactiveMenuItem(JMenuItem item, boolean topLevel) {
        item.setOpaque(true);
        item.setFocusPainted(false);
        item.setRolloverEnabled(true);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.setBorder(BorderFactory.createEmptyBorder(
            topLevel ? 6 : 5,
            topLevel ? 10 : 12,
            topLevel ? 6 : 5,
            topLevel ? 10 : 12
        ));

        Object oldListener = item.getClientProperty(MENU_CHANGE_LISTENER_KEY);
        if (oldListener instanceof ChangeListener listener) {
            item.getModel().removeChangeListener(listener);
        }

        ChangeListener listener = event -> applyMenuItemState(item, topLevel);
        item.getModel().addChangeListener(listener);
        item.putClientProperty(MENU_CHANGE_LISTENER_KEY, listener);
        applyMenuItemState(item, topLevel);
    }

    private void applyMenuItemState(JMenuItem item, boolean topLevel) {
        var model = item.getModel();
        boolean active = model.isArmed() || model.isPressed() || model.isSelected();
        boolean rollover = model.isRollover();
        Color normalBackground = topLevel ? theme.raisedBackground() : theme.panelBackground();
        Color background = normalBackground;
        Color foreground = theme.text();

        if (!item.isEnabled()) {
            foreground = theme.disabledText();
        } else if (active) {
            background = theme.listSelectionBackground();
            foreground = theme.listSelectionForeground();
        } else if (rollover) {
            background = blend(normalBackground, theme.text(), theme.darkMode() ? 0.14 : 0.08);
        }

        item.setBackground(background);
        item.setForeground(foreground);
        item.repaint();
    }

    private Color blend(Color base, Color overlay, double amount) {
        double keep = 1.0 - amount;
        return new Color(
            clampColor(base.getRed() * keep + overlay.getRed() * amount),
            clampColor(base.getGreen() * keep + overlay.getGreen() * amount),
            clampColor(base.getBlue() * keep + overlay.getBlue() * amount)
        );
    }

    private int clampColor(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private javax.swing.border.TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(theme.border()),
            title,
            javax.swing.border.TitledBorder.LEADING,
            javax.swing.border.TitledBorder.TOP,
            getFont(),
            theme.text()
        );
    }

    private void showFindDialog() {
        openFindReplaceDialog(false);
    }

    private void showReplaceDialog() {
        openFindReplaceDialog(true);
    }

    private void openFindReplaceDialog(boolean focusReplace) {
        if (findDialog == null) {
            createFindReplaceDialog();
        }

        String selectedText = editor.getSelectedText();
        if (selectedText != null && !selectedText.isBlank() && !selectedText.contains(System.lineSeparator())) {
            findField.setText(selectedText);
        }

        findDialog.setLocationRelativeTo(this);
        findDialog.setVisible(true);
        if (focusReplace) {
            replaceField.requestFocusInWindow();
            replaceField.selectAll();
        } else {
            findField.requestFocusInWindow();
            findField.selectAll();
        }
    }

    private void createFindReplaceDialog() {
        findDialog = new JDialog(this, "Find / Replace", false);
        findDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        findField = new JTextField(26);
        replaceField = new JTextField(26);
        matchCaseBox = new JCheckBox("Match case");
        findStatusLabel = new JLabel(" ");

        findField.addActionListener(event -> findNext());
        replaceField.addActionListener(event -> replaceCurrent());

        JPanel content = new JPanel();
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(fieldRow("Find", findField));
        content.add(fieldRow("Replace", replaceField));

        JPanel optionsRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        optionsRow.add(matchCaseBox);
        content.add(optionsRow);

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        buttonRow.add(button("Find Next", this::findNext));
        buttonRow.add(button("Replace", this::replaceCurrent));
        buttonRow.add(button("Replace All", this::replaceAll));
        buttonRow.add(button("Close", () -> findDialog.setVisible(false)));
        content.add(buttonRow);
        content.add(findStatusLabel);

        findDialog.setContentPane(content);
        applyComponentTheme(content);
        findDialog.pack();
    }

    private JPanel fieldRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(58, 24));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void findNext() {
        String query = findQuery();
        if (query == null) {
            return;
        }

        String text = editor.getText();
        int start = Math.min(editor.getSelectionEnd(), text.length());
        int index = findIndex(text, query, start, matchCaseBox.isSelected());
        if (index < 0 && start > 0) {
            index = findIndex(text, query, 0, matchCaseBox.isSelected());
        }

        if (index < 0) {
            findStatusLabel.setText("No match");
            setStatus("No match for " + query);
            return;
        }

        selectEditorRange(index, index + query.length());
        int lineNumber = editor.getDocument().getDefaultRootElement().getElementIndex(index) + 1;
        findStatusLabel.setText("Line " + lineNumber);
    }

    private void replaceCurrent() {
        String query = findQuery();
        if (query == null) {
            return;
        }

        if (selectionMatches(query)) {
            editor.replaceSelection(replaceField.getText());
            dirty = true;
            rememberCurrentOpenTab();
            updateEditorTabTitles();
            updateTitle();
            scheduleAutoCompile();
            scheduleProjectFilesRefresh();
        }
        findNext();
    }

    private void replaceAll() {
        String query = findQuery();
        if (query == null) {
            return;
        }

        ReplaceResult result = replaceAll(editor.getText(), query, replaceField.getText(), matchCaseBox.isSelected());
        if (result.count() == 0) {
            findStatusLabel.setText("No match");
            setStatus("No match for " + query);
            return;
        }

        // Replace all is grouped as one undo action, so Ctrl+Z restores the full previous source.
        activeCompoundEdit = new CompoundEdit();
        try {
            editor.setText(result.text());
            editor.setCaretPosition(0);
        } finally {
            activeCompoundEdit.end();
            undoManager.addEdit(activeCompoundEdit);
            activeCompoundEdit = null;
            updateUndoRedoButtons();
        }
        refreshSyntaxHighlighting();
        dirty = true;
        rememberCurrentOpenTab();
        updateEditorTabTitles();
        updateTitle();
        scheduleAutoCompile();
        scheduleProjectFilesRefresh();
        findStatusLabel.setText("Replaced " + result.count());
        setStatus("Replaced " + result.count() + " matches");
    }

    private String findQuery() {
        String query = findField == null ? "" : findField.getText();
        if (query.isEmpty()) {
            findStatusLabel.setText("Enter text");
            return null;
        }
        return query;
    }

    private int findIndex(String text, String query, int fromIndex, boolean matchCase) {
        if (matchCase) {
            return text.indexOf(query, fromIndex);
        }
        return text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT), fromIndex);
    }

    private boolean selectionMatches(String query) {
        String selected = editor.getSelectedText();
        if (selected == null) {
            return false;
        }
        return matchCaseBox.isSelected() ? selected.equals(query) : selected.equalsIgnoreCase(query);
    }

    private ReplaceResult replaceAll(String source, String query, String replacement, boolean matchCase) {
        String searchableSource = matchCase ? source : source.toLowerCase(Locale.ROOT);
        String searchableQuery = matchCase ? query : query.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(source.length());
        int count = 0;
        int cursor = 0;
        int index = searchableSource.indexOf(searchableQuery, cursor);
        while (index >= 0) {
            result.append(source, cursor, index);
            result.append(replacement);
            cursor = index + query.length();
            count++;
            index = searchableSource.indexOf(searchableQuery, cursor);
        }
        result.append(source, cursor, source.length());
        return new ReplaceResult(result.toString(), count);
    }

    private void selectEditorRange(int start, int end) {
        editor.requestFocusInWindow();
        editor.setCaretPosition(start);
        editor.moveCaretPosition(Math.min(end, editor.getDocument().getLength()));
        try {
            Rectangle selectionRect = editor.modelToView2D(start).getBounds();
            selectionRect.grow(0, 80);
            editor.scrollRectToVisible(selectionRect);
        } catch (BadLocationException ignored) {
            // Selection still works even if Swing cannot calculate a scroll rectangle.
        }
    }

    private void showTemplatesDialog() {
        if (templateDialog == null) {
            createTemplatesDialog();
        }

        refreshTemplateList();
        templateDialog.setLocationRelativeTo(this);
        templateDialog.setVisible(true);
    }

    private void createTemplatesDialog() {
        templateDialog = new JDialog(this, "Templates", false);
        templateDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        templateModel = new DefaultListModel<>();
        templateList = new JList<>(templateModel);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setFixedCellHeight(30);
        templateList.setCellRenderer(new TemplateListRenderer());
        templateList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateTemplatePreview();
            }
        });

        templatePreview = new JTextArea(20, 52);
        templatePreview.setEditable(false);
        templatePreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        templatePreview.setLineWrap(false);
        templatePreview.setTabSize(4);

        templateDescriptionLabel = new JLabel(" ");

        JScrollPane templateListScroll = new JScrollPane(templateList);
        templateListScroll.setPreferredSize(new Dimension(260, 360));
        JScrollPane previewScroll = new JScrollPane(templatePreview);

        JSplitPane templateSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, templateListScroll, previewScroll);
        templateSplit.setResizeWeight(0.32);
        templateSplit.setDividerLocation(260);

        templatePdfPreview = new PdfPreviewPanel();
        templatePdfPreview.setPreferredSize(new Dimension(420, 220));
        templatePdfPreview.clear("Click Preview PDF to render this template.");

        JSplitPane previewSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewScroll, templatePdfPreview);
        previewSplit.setResizeWeight(0.46);
        previewSplit.setDividerLocation(190);

        templatePreviewStatusLabel = new JLabel(" ");

        JButton addButton = button("Add", this::addSelectedTemplate);
        JButton copyButton = button("Copy", this::copySelectedTemplate);
        renderTemplatePreviewButton = button("Preview PDF", this::renderSelectedTemplatePreview);
        JButton newCustomTemplateButton = button("New Custom", this::createCustomTemplate);
        saveCustomTemplateButton = button("Save Custom", this::saveEditedCustomTemplate);
        deleteCustomTemplateButton = button("Delete Custom", this::deleteSelectedCustomTemplate);
        JButton saveSelectionButton = button("Save Selection", this::saveSelectionAsTemplate);
        JButton openFolderButton = button("Open Folder", this::openTemplatesFolder);
        JButton refreshButton = button("Refresh", this::refreshTemplateList);
        JButton closeButton = button("Close", () -> templateDialog.setVisible(false));

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        buttonRow.add(addButton);
        buttonRow.add(copyButton);
        buttonRow.add(renderTemplatePreviewButton);
        buttonRow.add(newCustomTemplateButton);
        buttonRow.add(saveCustomTemplateButton);
        buttonRow.add(deleteCustomTemplateButton);
        buttonRow.add(saveSelectionButton);
        buttonRow.add(openFolderButton);
        buttonRow.add(refreshButton);
        buttonRow.add(closeButton);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        JLabel title = new JLabel("Templates");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.WEST);
        header.add(templateDescriptionLabel, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(header, BorderLayout.NORTH);
        templateSplit.setRightComponent(previewSplit);
        content.add(templateSplit, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(templatePreviewStatusLabel, BorderLayout.CENTER);
        footer.add(buttonRow, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);

        templateDialog.setContentPane(content);
        applyComponentTheme(content);
        styleTemplateControls();
        templateDialog.setSize(840, 520);
    }

    private void refreshTemplateList() {
        if (templateModel == null || templateList == null) {
            return;
        }

        LatexTemplate previousSelection = templateList.getSelectedValue();
        templateModel.clear();

        try {
            List<LatexTemplate> templates = templateService.loadTemplates();
            int selectedIndex = -1;
            for (LatexTemplate template : templates) {
                templateModel.addElement(template);
                if (sameTemplate(previousSelection, template)) {
                    selectedIndex = templateModel.getSize() - 1;
                }
            }

            if (templateModel.getSize() > 0) {
                templateList.setSelectedIndex(selectedIndex >= 0 ? selectedIndex : 0);
            } else {
                updateTemplatePreview();
            }
        } catch (IOException error) {
            showError("Could not load templates", error);
        }
    }

    private boolean sameTemplate(LatexTemplate first, LatexTemplate second) {
        if (first != null
            && second != null
            && first.sourcePath() != null
            && second.sourcePath() != null) {
            return samePath(first.sourcePath(), second.sourcePath());
        }

        return first != null
            && second != null
            && first.custom() == second.custom()
            && first.name().equals(second.name())
            && first.category().equals(second.category());
    }

    private void updateTemplatePreview() {
        if (templatePreview == null || templateDescriptionLabel == null || templateList == null) {
            return;
        }

        LatexTemplate template = templateList.getSelectedValue();
        if (template == null) {
            templateDescriptionLabel.setText("No templates found");
            templatePreview.setText("");
            templatePreview.setEditable(false);
            if (templatePdfPreview != null) {
                templatePdfPreview.clear("Choose a template to preview.");
            }
            updateTemplateManagementButtons(null);
            return;
        }

        StringBuilder preview = new StringBuilder();
        if (!template.preamble().isBlank()) {
            preview.append("% Added to the preamble if missing\n");
            preview.append(template.preamble().strip()).append("\n\n");
        }
        preview.append(template.body().strip()).append('\n');

        templateDescriptionLabel.setText(template.category() + " - " + template.description());
        templatePreview.setText(preview.toString());
        templatePreview.setEditable(template.custom());
        templatePreview.setCaretPosition(0);
        if (templatePdfPreview != null) {
            templatePdfPreview.clear("Click Preview PDF to render this template.");
        }
        if (templatePreviewStatusLabel != null) {
            templatePreviewStatusLabel.setText(" ");
        }
        updateTemplateManagementButtons(template);
        styleTemplateControls();
    }

    private void updateTemplateManagementButtons(LatexTemplate template) {
        boolean custom = template != null && template.custom();
        if (saveCustomTemplateButton != null) {
            saveCustomTemplateButton.setEnabled(custom);
        }
        if (deleteCustomTemplateButton != null) {
            deleteCustomTemplateButton.setEnabled(custom);
        }
    }

    private void addSelectedTemplate() {
        if (templateList == null) {
            return;
        }

        LatexTemplate template = templateList.getSelectedValue();
        if (template == null) {
            setStatus("Choose a template to add.");
            return;
        }

        insertTemplate(templateForCurrentPreview(template));
    }

    private void copySelectedTemplate() {
        if (templateList == null) {
            return;
        }

        LatexTemplate template = templateList.getSelectedValue();
        if (template == null) {
            setStatus("Choose a template to copy.");
            return;
        }

        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(new StringSelection(templateClipboardText(templateForCurrentPreview(template))), null);
        setStatus("Copied template: " + template.name());
    }

    private String templateClipboardText(LatexTemplate template) {
        StringBuilder text = new StringBuilder();
        if (!template.preamble().isBlank()) {
            text.append("% Preamble lines this template may need\n");
            text.append(template.preamble().strip()).append("\n\n");
        }
        text.append(template.body().strip()).append('\n');
        return text.toString();
    }

    private LatexTemplate templateForCurrentPreview(LatexTemplate template) {
        if (template == null || !template.custom() || templatePreview == null) {
            return template;
        }

        return new LatexTemplate(
            template.name(),
            template.category(),
            template.description(),
            template.preamble(),
            templatePreview.getText(),
            true,
            template.sourcePath()
        );
    }

    private void renderSelectedTemplatePreview() {
        if (templateList == null || templatePdfPreview == null) {
            return;
        }

        LatexTemplate template = templateForCurrentPreview(templateList.getSelectedValue());
        if (template == null) {
            setStatus("Choose a template to preview.");
            return;
        }

        if (operationRunning) {
            setStatus("Wait for the current compile/export before previewing a template.");
            return;
        }

        int requestId = ++templatePreviewRequestId;
        if (templatePreviewWorker != null && !templatePreviewWorker.isDone()) {
            templatePreviewWorker.cancel(true);
        }

        renderTemplatePreviewButton.setEnabled(false);
        templatePreviewStatusLabel.setText("Rendering template preview...");
        templatePdfPreview.clear("Rendering template preview...");

        templatePreviewWorker = new SwingWorker<>() {
            private Path outputPdf;

            @Override
            protected ExportResult doInBackground() throws Exception {
                Path previewDirectory = Files.createTempDirectory("latex-compiler-template-preview-");
                Path source = previewDirectory.resolve("template-preview.tex");
                outputPdf = previewDirectory.resolve("template-preview.pdf");
                Files.writeString(source, templatePreviewDocument(template), StandardCharsets.UTF_8);
                return exportService.export(MainWindow.this, source, outputPdf, ExportFormat.PDF, message -> publish(message));
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    templatePreviewStatusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                if (requestId != templatePreviewRequestId) {
                    return;
                }

                renderTemplatePreviewButton.setEnabled(true);
                try {
                    ExportResult result = get();
                    if (result.success() && outputPdf != null && Files.isRegularFile(outputPdf)) {
                        templatePreviewStatusLabel.setText("Preview rendered");
                        templatePdfPreview.loadPdf(outputPdf);
                    } else {
                        templatePreviewStatusLabel.setText("Preview failed");
                        templatePdfPreview.clear("Preview failed. Check the logs below.");
                        appendLog(result.log());
                        setLogsVisible(true);
                    }
                } catch (Exception error) {
                    templatePreviewStatusLabel.setText("Preview failed");
                    templatePdfPreview.clear("Preview failed. Check the logs below.");
                    appendLog("Template preview failed: " + error.getMessage());
                    setLogsVisible(true);
                }
            }
        };
        templatePreviewWorker.execute();
    }

    private String templatePreviewDocument(LatexTemplate template) {
        String basePreamble = """
            \\documentclass[11pt]{article}
            \\usepackage[utf8]{inputenc}
            \\usepackage[T1]{fontenc}
            \\usepackage[margin=1in]{geometry}
            \\usepackage{amsmath}
            \\usepackage{booktabs}
            \\usepackage{xcolor}
            \\usepackage{graphicx}
            \\usepackage{hyperref}
            """;
        String extraPreamble = missingPreambleLines(template.preamble(), basePreamble);
        String body = previewBodyForTemplate(template);
        return basePreamble
            + (extraPreamble.isBlank() ? "" : extraPreamble + "\n")
            + "\n\\begin{document}\n\n"
            + body.strip()
            + "\n\n\\end{document}\n";
    }

    private String previewBodyForTemplate(LatexTemplate template) {
        String body = template.body();
        // Template preview must compile without requiring a real project image file.
        return body.replace(
            "\\includegraphics[width=0.8\\linewidth]{image-file-name}",
            "\\fbox{\\parbox[c][4cm][c]{0.72\\linewidth}{\\centering Image preview placeholder}}"
        );
    }

    private void saveSelectionAsTemplate() {
        String selectedText = editor.getSelectedText();
        if (selectedText == null || selectedText.isBlank()) {
            JOptionPane.showMessageDialog(
                this,
                "Select LaTeX in the editor first, then save it as a template.",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        String name = promptProjectChildName("Save Template", "Template name:", "my-template");
        if (name == null) {
            return;
        }

        try {
            Path saved = templateService.saveCustomTemplate(name, selectedText);
            refreshTemplateList();
            setStatus("Saved template " + saved.getFileName());
        } catch (IOException error) {
            showError("Could not save template", error);
        }
    }

    private void createCustomTemplate() {
        String name = promptProjectChildName("New Template", "Template name:", "my-template");
        if (name == null) {
            return;
        }

        String body = editor.getSelectedText();
        if (body == null || body.isBlank()) {
            body = """
                % Write reusable LaTeX here.
                \\section{New Section}

                Start writing from this template.
                """;
        }

        try {
            Path saved = templateService.saveCustomTemplate(name, body);
            refreshTemplateList();
            selectTemplateByPath(saved);
            setStatus("Created custom template " + saved.getFileName());
        } catch (IOException error) {
            showError("Could not create template", error);
        }
    }

    private void saveEditedCustomTemplate() {
        LatexTemplate template = templateList == null ? null : templateList.getSelectedValue();
        if (template == null || !template.custom()) {
            setStatus("Choose a custom template to save.");
            return;
        }

        try {
            templateService.updateCustomTemplate(template, templatePreview.getText());
            refreshTemplateList();
            selectTemplateByPath(template.sourcePath());
            setStatus("Saved custom template " + template.name());
        } catch (IOException error) {
            showError("Could not save custom template", error);
        }
    }

    private void deleteSelectedCustomTemplate() {
        LatexTemplate template = templateList == null ? null : templateList.getSelectedValue();
        if (template == null || !template.custom()) {
            setStatus("Choose a custom template to delete.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            "Delete custom template " + template.name() + "?",
            "Delete Template",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            templateService.deleteCustomTemplate(template);
            refreshTemplateList();
            setStatus("Deleted custom template " + template.name());
        } catch (IOException error) {
            showError("Could not delete custom template", error);
        }
    }

    private void selectTemplateByPath(Path path) {
        if (path == null || templateList == null || templateModel == null) {
            return;
        }

        for (int index = 0; index < templateModel.getSize(); index++) {
            LatexTemplate template = templateModel.getElementAt(index);
            if (template.sourcePath() != null && samePath(template.sourcePath(), path)) {
                templateList.setSelectedIndex(index);
                templateList.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    private void openTemplatesFolder() {
        try {
            Path directory = templateService.templatesDirectory();
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory.toFile());
            }
            setStatus("Templates folder: " + directory);
        } catch (IOException | UnsupportedOperationException error) {
            showError("Could not open templates folder", error);
        }
    }

    private void insertTemplate(LatexTemplate template) {
        if (operationRunning) {
            setStatus("Wait for the current compile/export before editing templates.");
            return;
        }

        int selectionStart = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int selectionEnd = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        String beforeText = editor.getText();
        TemplateInsertion insertion = createTemplateInsertion(beforeText, selectionStart, selectionEnd, template);

        replaceEditorTextFromUndoableAction(insertion.text(), insertion.insertedEnd(), insertion.insertedEnd());
        undoManager.addEdit(new EditorTextUndoEdit(
            "template insertion",
            beforeText,
            selectionStart,
            selectionEnd,
            insertion.text(),
            insertion.insertedEnd(),
            insertion.insertedEnd()
        ));
        updateUndoRedoButtons();

        dirty = true;
        rememberCurrentOpenTab();
        updateEditorTabTitles();
        updateTitle();
        scheduleAutoCompile();
        scheduleProjectFilesRefresh();
        editor.requestFocusInWindow();
        setStatus("Added template: " + template.name());
    }

    private TemplateInsertion createTemplateInsertion(
        String source,
        int selectionStart,
        int selectionEnd,
        LatexTemplate template
    ) {
        String text = source == null ? "" : source;
        int start = Math.max(0, Math.min(selectionStart, text.length()));
        int end = Math.max(start, Math.min(selectionEnd, text.length()));
        String missingPreamble = missingPreambleLines(template.preamble(), text);

        if (!missingPreamble.isBlank()) {
            int documentStart = text.indexOf("\\begin{document}");
            int preambleInsertionPoint = Math.max(0, documentStart);
            String preambleBlock = missingPreamble + "\n";
            String spacer = documentStart >= 0 ? "\n" : "\n\n";

            text = text.substring(0, preambleInsertionPoint)
                + preambleBlock
                + spacer
                + text.substring(preambleInsertionPoint);

            int shift = preambleBlock.length() + spacer.length();
            if (preambleInsertionPoint <= start) {
                start += shift;
                end += shift;
            } else if (preambleInsertionPoint < end) {
                end += shift;
            }
        }

        int documentStart = text.indexOf("\\begin{document}");
        if (documentStart >= 0 && start <= documentStart && end <= documentStart) {
            int afterDocumentStart = endOfLine(text, documentStart);
            start = afterDocumentStart;
            end = afterDocumentStart;
        }

        String snippet = "\n\n" + template.body().strip() + "\n";
        String result = text.substring(0, start) + snippet + text.substring(end);
        return new TemplateInsertion(result, start + 2, start + snippet.length());
    }

    private String missingPreambleLines(String preamble, String source) {
        if (preamble == null || preamble.isBlank()) {
            return "";
        }

        StringBuilder missing = new StringBuilder();
        for (String line : preamble.strip().split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.isBlank() && !source.contains(trimmed)) {
                missing.append(trimmed).append('\n');
            }
        }
        return missing.toString().stripTrailing();
    }

    private int endOfLine(String text, int offset) {
        int lineEnd = text.indexOf('\n', offset);
        return lineEnd < 0 ? text.length() : lineEnd + 1;
    }

    private void replaceEditorTextFromUndoableAction(String text, int selectionStart, int selectionEnd) {
        loading = true;
        try {
            editor.setText(text);
            setEditorSelection(selectionStart, selectionEnd);
        } finally {
            loading = false;
        }
        refreshSyntaxHighlighting();
        scheduleProjectFilesRefresh();
        updateCaretStatus();
    }

    private void setEditorSelection(int selectionStart, int selectionEnd) {
        int length = editor.getDocument().getLength();
        int start = Math.max(0, Math.min(selectionStart, length));
        int end = Math.max(start, Math.min(selectionEnd, length));
        editor.setCaretPosition(start);
        editor.moveCaretPosition(end);
    }

    private void styleTemplateControls() {
        if (templateList != null) {
            templateList.setBackground(theme.panelBackground());
            templateList.setForeground(theme.text());
            templateList.setSelectionBackground(theme.listSelectionBackground());
            templateList.setSelectionForeground(theme.listSelectionForeground());
            templateList.repaint();
        }
        if (templatePreview != null) {
            templatePreview.setBackground(theme.editorBackground());
            templatePreview.setForeground(theme.editorForeground());
            templatePreview.setCaretColor(theme.editorCaret());
            templatePreview.setSelectionColor(theme.editorSelection());
            templatePreview.setSelectedTextColor(theme.editorSelectedText());
        }
        if (templateDescriptionLabel != null) {
            templateDescriptionLabel.setForeground(theme.mutedText());
        }
        if (templatePreviewStatusLabel != null) {
            templatePreviewStatusLabel.setForeground(theme.mutedText());
        }
        if (templatePdfPreview != null) {
            templatePdfPreview.applyTheme(theme);
        }
    }

    private JPanel createMainContent() {
        editorScrollPane = new JScrollPane(editor);
        editorScrollPane.setBorder(BorderFactory.createTitledBorder("LaTeX Source"));
        lineNumberView = new LineNumberView(editor);
        editorScrollPane.setRowHeaderView(lineNumberView);
        editorScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        editorScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        JPanel editorWorkspace = new JPanel(new BorderLayout());
        editorTabs = new JTabbedPane();
        editorTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        editorTabs.setFocusable(false);
        editorTabs.setPreferredSize(new Dimension(100, 34));
        editorTabs.addChangeListener(event -> handleEditorTabSelection());
        editorWorkspace.add(editorTabs, BorderLayout.NORTH);
        editorWorkspace.add(editorScrollPane, BorderLayout.CENTER);
        refreshEditorTabs();

        JSplitPane editorPreviewWorkspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorWorkspace, pdfPreview);
        editorPreviewWorkspace.setResizeWeight(0.54);
        editorPreviewWorkspace.setDividerLocation(560);

        projectFilesCard = new CardLayout();
        projectFilesSlot = new JPanel(projectFilesCard);
        projectFilesSlot.add(projectFilesPanel, PROJECT_FILES_EXPANDED_CARD);
        projectFilesSlot.add(createCollapsedProjectFilesPanel(), PROJECT_FILES_COLLAPSED_CARD);

        projectWorkspaceSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectFilesSlot, editorPreviewWorkspace);
        projectWorkspaceSplit.setResizeWeight(0);
        projectWorkspaceSplit.setDividerLocation(projectFilesDividerLocation);
        projectWorkspaceSplit.setDividerSize(projectFilesDividerSize);

        logsPanel = new JPanel(new BorderLayout());
        logsPanel.setBorder(BorderFactory.createTitledBorder("Logs"));
        logsPanel.setPreferredSize(new Dimension(100, 190));
        logsPanel.add(new JScrollPane(logs), BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout());
        content.add(projectWorkspaceSplit, BorderLayout.CENTER);
        content.add(logsPanel, BorderLayout.SOUTH);
        return content;
    }

    private JPanel createCollapsedProjectFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setMinimumSize(new Dimension(COLLAPSED_PROJECT_FILES_WIDTH, 120));
        panel.setPreferredSize(new Dimension(COLLAPSED_PROJECT_FILES_WIDTH, 100));

        JButton showButton = new JButton(">");
        showButton.setFocusable(false);
        showButton.setMargin(new Insets(4, 2, 4, 2));
        showButton.setMinimumSize(new Dimension(COLLAPSED_PROJECT_FILES_WIDTH - 4, 30));
        showButton.setPreferredSize(new Dimension(COLLAPSED_PROJECT_FILES_WIDTH - 4, 30));
        showButton.setToolTipText("Show project files");
        showButton.addActionListener(event -> setProjectFilesVisible(true));
        panel.add(showButton, BorderLayout.NORTH);
        return panel;
    }

    private void handleEditorTabSelection() {
        if (updatingEditorTabs || editorTabs == null) {
            return;
        }

        int index = editorTabs.getSelectedIndex();
        if (index < 0 || index >= openFileOrder.size()) {
            return;
        }

        Path target = openFileOrder.get(index);
        if (samePath(target, currentFile)) {
            return;
        }

        rememberCurrentOpenTab();
        loadOpenTab(target);
    }

    private void rememberCurrentOpenTab() {
        if (currentFile == null) {
            return;
        }

        Path normalized = currentFile.toAbsolutePath().normalize();
        OpenFileTab tab = openFileTabs.computeIfAbsent(normalized, path -> new OpenFileTab(path, ""));
        if (!openFileOrder.stream().anyMatch(path -> samePath(path, normalized))) {
            openFileOrder.add(normalized);
        }
        tab.text = editor.getText();
        tab.dirty = dirty;
        tab.caretPosition = editor.getCaretPosition();
        tab.selectionStart = editor.getSelectionStart();
        tab.selectionEnd = editor.getSelectionEnd();
    }

    private void loadOpenTab(Path path) {
        OpenFileTab tab = openFileTabs.get(path.toAbsolutePath().normalize());
        if (tab == null) {
            return;
        }

        loading = true;
        try {
            currentFile = tab.path;
            editor.setText(tab.text);
            setEditorSelection(tab.selectionStart, tab.selectionEnd);
            dirty = tab.dirty;
            mainFile = currentFile;
            resetUndoHistory();
            refreshSyntaxHighlighting();
            refreshProjectFiles();
            loadExistingPdfPreview();
            updateTitle();
            setStatus("Opened tab " + pathDisplayName(currentFile, currentFile.toString()));
        } finally {
            loading = false;
        }
        refreshEditorTabs();
    }

    private void addOrUpdateOpenTab(Path path, String text, boolean dirty) {
        Path normalized = path.toAbsolutePath().normalize();
        OpenFileTab tab = openFileTabs.computeIfAbsent(normalized, key -> new OpenFileTab(key, text));
        tab.text = text == null ? "" : text;
        tab.dirty = dirty;
        tab.caretPosition = Math.max(0, Math.min(tab.caretPosition, tab.text.length()));
        tab.selectionStart = Math.max(0, Math.min(tab.selectionStart, tab.text.length()));
        tab.selectionEnd = Math.max(tab.selectionStart, Math.min(tab.selectionEnd, tab.text.length()));
        if (!openFileOrder.stream().anyMatch(item -> samePath(item, normalized))) {
            openFileOrder.add(normalized);
        }
    }

    private void removeOpenTabsOutsideProject(Path projectRoot) {
        if (projectRoot == null) {
            openFileTabs.clear();
            openFileOrder.clear();
            return;
        }

        openFileOrder.removeIf(path -> {
            boolean remove = !isSameOrInside(projectRoot, path);
            if (remove) {
                openFileTabs.remove(path);
            }
            return remove;
        });
    }

    private void removeOpenTabsAtOrInside(Path target) {
        if (target == null) {
            return;
        }

        openFileOrder.removeIf(path -> {
            boolean remove = isSameOrInside(target, path);
            if (remove) {
                openFileTabs.remove(path);
            }
            return remove;
        });
    }

    private void refreshEditorTabs() {
        if (editorTabs == null) {
            return;
        }

        updatingEditorTabs = true;
        try {
            editorTabs.removeAll();
            if (openFileOrder.isEmpty()) {
                editorTabs.addTab(dirty ? "*Untitled" : "Untitled", new JPanel());
            } else {
                int selectedIndex = -1;
                for (int index = 0; index < openFileOrder.size(); index++) {
                    Path path = openFileOrder.get(index);
                    OpenFileTab tab = openFileTabs.get(path);
                    editorTabs.addTab(tabTitle(tab), new JPanel());
                    editorTabs.setToolTipTextAt(index, path.toString());
                    if (samePath(path, currentFile)) {
                        selectedIndex = index;
                    }
                }

                if (selectedIndex >= 0) {
                    editorTabs.setSelectedIndex(selectedIndex);
                }
            }
        } finally {
            updatingEditorTabs = false;
        }
        styleEditorTabs();
    }

    private void updateEditorTabTitles() {
        if (editorTabs == null) {
            return;
        }

        if (openFileOrder.isEmpty()) {
            if (editorTabs.getTabCount() > 0) {
                editorTabs.setTitleAt(0, dirty ? "*Untitled" : "Untitled");
            }
            return;
        }

        for (int index = 0; index < openFileOrder.size() && index < editorTabs.getTabCount(); index++) {
            editorTabs.setTitleAt(index, tabTitle(openFileTabs.get(openFileOrder.get(index))));
        }
        styleEditorTabs();
    }

    private String tabTitle(OpenFileTab tab) {
        if (tab == null) {
            return "Untitled";
        }
        return (tab.dirty ? "*" : "") + pathDisplayName(tab.path, "Untitled");
    }

    private void styleEditorTabs() {
        if (editorTabs == null) {
            return;
        }

        editorTabs.setBackground(theme.raisedBackground());
        editorTabs.setForeground(theme.text());
        editorTabs.setOpaque(true);
        editorTabs.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border()));
        for (int index = 0; index < editorTabs.getTabCount(); index++) {
            boolean selected = index == editorTabs.getSelectedIndex();
            editorTabs.setBackgroundAt(index, selected ? theme.editorBackground() : theme.raisedBackground());
            editorTabs.setForegroundAt(index, selected ? theme.text() : theme.mutedText());
            setEditorTabComponent(index, editorTabs.getTitleAt(index), selected);
        }
    }

    private void setEditorTabComponent(int index, String title, boolean selected) {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(true);
        tab.setBackground(selected ? theme.editorBackground() : theme.raisedBackground());
        tab.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, selected ? 2 : 0, 0, selected ? theme.listSelectionBackground() : theme.border()),
            BorderFactory.createEmptyBorder(5, 10, selected ? 3 : 5, 10)
        ));

        JLabel label = new JLabel(title);
        label.setOpaque(false);
        label.setForeground(selected ? theme.text() : theme.mutedText());
        label.setFont(label.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN, 12f));
        tab.add(label, BorderLayout.CENTER);

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (index >= 0 && index < editorTabs.getTabCount()) {
                    editorTabs.setSelectedIndex(index);
                }
            }
        });
        editorTabs.setTabComponentAt(index, tab);
    }

    private JPanel createStatusBar() {
        statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        statusBar.add(status, BorderLayout.WEST);
        statusBar.add(fileStatus, BorderLayout.EAST);
        return statusBar;
    }

    private void loadStarterDocument() {
        loading = true;
        editor.setText(STARTER_DOCUMENT);
        editor.setCaretPosition(0);
        loading = false;
        dirty = false;
        resetUndoHistory();
        refreshSyntaxHighlighting();
        refreshEditorTabs();
    }

    private void newFile() {
        if (!confirmDiscardUnsavedChanges()) {
            return;
        }
        openFileTabs.clear();
        openFileOrder.clear();
        currentProjectRoot = null;
        currentFile = null;
        mainFile = null;
        stopProjectWatcher();
        loadStarterDocument();
        logs.setText("");
        pdfPreview.clear("Compile a document to preview the PDF here.");
        projectFilesPanel.clear();
        setStatus("New document");
        autoCompileTimer.stop();
        projectFilesRefreshTimer.stop();
        refreshEditorTabs();
        updateTitle();
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".tex");
            }

            @Override
            public String getDescription() {
                return "LaTeX files or folders";
            }
        });
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath();
        if (Files.isDirectory(selected)) {
            openProjectFolder(selected, true);
        } else {
            openLatexFile(selected, false);
        }
    }

    private void openProjectFolder(Path folder, boolean confirmUnsavedChanges) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }

        if (confirmUnsavedChanges && !confirmDiscardUnsavedChanges()) {
            return;
        }

        Path normalizedFolder = folder.toAbsolutePath().normalize();
        openFileTabs.clear();
        openFileOrder.clear();
        currentProjectRoot = normalizedFolder;
        currentFile = null;
        mainFile = detectMainFile(normalizedFolder);
        restartProjectWatcher();
        loading = true;
        try {
            editor.setText("");
            editor.setCaretPosition(0);
        } finally {
            loading = false;
        }
        dirty = false;
        resetUndoHistory();
        refreshSyntaxHighlighting();
        logs.setText("");
        pdfPreview.clear("Create or open a .tex file to preview the PDF here.");
        refreshProjectFiles();
        setProjectFilesVisible(true);
        setStatus("Opened folder " + currentProjectRoot);
        autoCompileTimer.stop();
        refreshEditorTabs();
        updateTitle();
    }

    private void openLatexFile(Path selected, boolean confirmUnsavedChanges) {
        if (selected == null) {
            return;
        }

        Path normalizedSelected = selected.toAbsolutePath().normalize();
        if (currentFile != null && samePath(normalizedSelected, currentFile)) {
            if (isTexFile(currentFile)) {
                mainFile = currentFile;
                rememberCurrentOpenTab();
                refreshProjectFiles();
                refreshEditorTabs();
                loadExistingPdfPreview();
            }
            setStatus("Already open " + selected);
            return;
        }

        boolean insideExistingProject = currentProjectRoot != null && isSameOrInside(currentProjectRoot, normalizedSelected);
        boolean changesProjectRoot = !insideExistingProject;
        if (confirmUnsavedChanges && changesProjectRoot && !confirmDiscardUnsavedChanges()) {
            return;
        }

        try {
            rememberCurrentOpenTab();
            OpenFileTab existingTab = openFileTabs.get(normalizedSelected);
            if (existingTab != null) {
                loadOpenTab(normalizedSelected);
                return;
            }

            String fileText = Files.readString(normalizedSelected, StandardCharsets.UTF_8);
            loading = true;
            editor.setText(fileText);
            editor.setCaretPosition(0);
            Path previousProjectRoot = currentProjectRoot;
            currentFile = normalizedSelected;
            Path selectedRoot = normalizedSelected.getParent();
            if (!insideExistingProject) {
                currentProjectRoot = selectedRoot;
                removeOpenTabsOutsideProject(currentProjectRoot);
                restartProjectWatcher();
            }
            if (isTexFile(currentFile)) {
                // Opening a .tex from the explorer means "work on and compile this file".
                mainFile = currentFile;
            } else if (mainFile == null || previousProjectRoot == null || !samePath(previousProjectRoot, currentProjectRoot)) {
                mainFile = currentFile;
            }
            dirty = false;
            addOrUpdateOpenTab(currentFile, fileText, false);
            resetUndoHistory();
            refreshSyntaxHighlighting();
            refreshProjectFiles();
            refreshEditorTabs();
            logs.setText("");
            loadExistingPdfPreview();
            setStatus("Opened " + selected);
            autoCompileTimer.stop();
            updateTitle();
        } catch (IOException error) {
            showError("Could not open file", error);
        } finally {
            loading = false;
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveFileAs();
            return;
        }

        try {
            Files.writeString(currentFile, editor.getText(), StandardCharsets.UTF_8);
            dirty = false;
            addOrUpdateOpenTab(currentFile, editor.getText(), false);
            refreshProjectFiles();
            refreshEditorTabs();
            setStatus("Saved " + currentFile);
            updateTitle();
        } catch (IOException error) {
            showError("Could not save file", error);
        }
    }

    private void saveFileAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("LaTeX files (*.tex)", "tex"));
        if (currentFile != null) {
            chooser.setSelectedFile(currentFile.toFile());
        } else if (currentProjectRoot != null) {
            chooser.setCurrentDirectory(currentProjectRoot.toFile());
            chooser.setSelectedFile(currentProjectRoot.resolve("document.tex").toFile());
        } else {
            chooser.setSelectedFile(Path.of("document.tex").toFile());
        }

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = ensureExtension(chooser.getSelectedFile().toPath(), "tex");
        Path previousFile = currentFile;
        currentFile = selected.toAbsolutePath().normalize();
        Path selectedParent = currentFile.getParent();
        if (currentProjectRoot == null || !isSameOrInside(currentProjectRoot, currentFile)) {
            currentProjectRoot = selectedParent;
        }
        if (mainFile == null || !isSameOrInside(currentProjectRoot, mainFile)) {
            mainFile = currentFile;
        }
        if (previousFile != null && !samePath(previousFile, currentFile)) {
            openFileTabs.remove(previousFile.toAbsolutePath().normalize());
            openFileOrder.removeIf(path -> samePath(path, previousFile));
        }
        addOrUpdateOpenTab(currentFile, editor.getText(), dirty);
        restartProjectWatcher();
        saveFile();
    }

    private void formatLatex() {
        String original = editor.getText();
        String formatted = latexFormatter.format(original);
        if (original.equals(formatted)) {
            setStatus("LaTeX is already formatted");
            return;
        }

        // Formatting is grouped as one undo step so Ctrl+Z restores the previous source.
        activeCompoundEdit = new CompoundEdit();
        try {
            editor.setText(formatted);
            editor.setCaretPosition(0);
        } finally {
            activeCompoundEdit.end();
            undoManager.addEdit(activeCompoundEdit);
            activeCompoundEdit = null;
            updateUndoRedoButtons();
        }
        refreshSyntaxHighlighting();

        dirty = true;
        rememberCurrentOpenTab();
        updateEditorTabTitles();
        updateTitle();
        setStatus("Formatted LaTeX source");
        scheduleAutoCompile();
    }

    private void export(ExportFormat format) {
        if (!saveBeforeExport()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(format.label() + " files (*." + format.extension() + ")", format.extension()));
        chooser.setSelectedFile(defaultExportFile(format).toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path target = ensureExtension(chooser.getSelectedFile().toPath(), format.extension());
        runExport(format, target, "Exporting " + format.label() + "...", format != ExportFormat.PDF, false);
    }

    // Manual and auto compile both update the right-side PDF preview.
    private void compilePdf(boolean automatic) {
        if (operationRunning) {
            if (automatic) {
                compileQueued = true;
            }
            return;
        }

        if (!saveBeforeCompile(!automatic)) {
            return;
        }

        Path target = defaultExportFile(ExportFormat.PDF);
        runExport(ExportFormat.PDF, target, automatic ? "Auto compiling PDF..." : "Compiling PDF...", false, automatic);
    }

    // SwingWorker keeps the editor responsive while Tectonic or Pandoc runs.
    private void runExport(ExportFormat format, Path target, String startMessage, boolean openAfterSuccess, boolean automatic) {
        Path source = compileSourceFile();
        if (source == null) {
            setStatus("Choose a main .tex file before compiling.");
            return;
        }

        logs.setText("");
        appendLog("Source: " + source);
        appendLog((automatic ? "Auto compile" : format.label()) + " target: " + target);
        setStatus(startMessage);
        operationRunning = true;
        setToolbarEnabled(false);

        SwingWorker<ExportResult, String> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                return exportService.export(MainWindow.this, source, target, format, message -> publish(message));
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(MainWindow.this::appendLog);
            }

            @Override
            protected void done() {
                operationRunning = false;
                setToolbarEnabled(true);
                try {
                    ExportResult result = get();
                    appendLog(result.log());
                    if (result.success()) {
                        setStatus((automatic ? "Auto compiled " : "Created ") + result.outputFile());
                        if (format == ExportFormat.PDF) {
                            pdfPreview.loadPdf(result.outputFile());
                        }
                        refreshProjectFiles();
                        if (openAfterSuccess) {
                            openGeneratedFile(result.outputFile());
                        }
                    } else {
                        setStatus((automatic ? "Auto compile" : format.label() + " export") + " failed");
                        if (!automatic) {
                            setLogsVisible(true);
                        }
                    }
                } catch (Exception error) {
                    setStatus((automatic ? "Auto compile" : format.label() + " export") + " failed");
                    if (!automatic) {
                        setLogsVisible(true);
                    }
                    showError("Export failed", error);
                } finally {
                    if (compileQueued && autoCompileToggle != null && autoCompileToggle.isSelected()) {
                        compileQueued = false;
                        scheduleAutoCompile();
                    }
                }
            }
        };
        worker.execute();
    }

    // Auto compile cannot show a Save As dialog, so unsaved documents wait for manual save.
    private boolean saveBeforeCompile(boolean allowDialogs) {
        if (dirty && currentFile != null) {
            saveFile();
        }

        if (compileSourceFile() != null) {
            return !dirty;
        }

        if (!allowDialogs) {
            setStatus("Choose or save a main .tex file before auto compile");
            return false;
        }

        if (currentFile == null) {
            if (!allowDialogs) {
                setStatus("Save the file before auto compile");
                return false;
            }

            int choice = JOptionPane.showConfirmDialog(
                this,
                "Save this LaTeX file before compiling?",
                "Save Required",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }
            saveFileAs();
            return compileSourceFile() != null && !dirty;
        }

        setStatus("Choose a main .tex file before compiling.");
        return false;
    }

    private boolean saveBeforeExport() {
        if (dirty && currentFile != null) {
            saveFile();
        }

        if (compileSourceFile() != null) {
            return !dirty;
        }

        if (currentFile == null) {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Save this LaTeX file before exporting?",
                "Save Required",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }
            saveFileAs();
            return compileSourceFile() != null && !dirty;
        }

        setStatus("Choose a main .tex file before exporting.");
        return false;
    }

    private Path defaultExportFile(ExportFormat format) {
        Path source = compileSourceFile();
        if (source == null) {
            source = currentFile == null ? Path.of("document.tex") : currentFile;
        }
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path parent = source.toAbsolutePath().getParent();
        return parent.resolve(base + "." + format.extension());
    }

    private void loadExistingPdfPreview() {
        if (compileSourceFile() == null) {
            pdfPreview.clear("Compile a document to preview the PDF here.");
            return;
        }

        Path pdf = defaultExportFile(ExportFormat.PDF);
        if (Files.isRegularFile(pdf)) {
            pdfPreview.loadPdf(pdf);
        } else {
            pdfPreview.clear("No compiled PDF yet.");
        }
    }

    private Path compileSourceFile() {
        if (mainFile != null && Files.isRegularFile(mainFile)) {
            return mainFile.toAbsolutePath().normalize();
        }

        if (currentFile != null && Files.isRegularFile(currentFile)) {
            return currentFile.toAbsolutePath().normalize();
        }

        return null;
    }

    private void navigateFromPdfToSource(Path pdfFile, int pageNumber, double normalizedX, double normalizedY) {
        Path source = compileSourceFile();
        if (source == null) {
            setStatus("Choose a main LaTeX source before using PDF click navigation.");
            return;
        }

        try {
            var position = syncTexService.findSourcePosition(pdfFile, source, pageNumber, normalizedX, normalizedY);
            if (position.isEmpty()) {
                setStatus("No SyncTeX match for that PDF position. Recompile the PDF and try again.");
                return;
            }

            SourcePosition sourcePosition = position.get();
            if (!samePath(sourcePosition.sourceFile(), currentFile)) {
                openLatexFile(sourcePosition.sourceFile(), true);
                if (!samePath(sourcePosition.sourceFile(), currentFile)) {
                    return;
                }
            }

            selectEditorLine(sourcePosition.line());
            setStatus("PDF page " + pageNumber + " -> line " + sourcePosition.line());
        } catch (IOException | BadLocationException error) {
            setStatus("Could not jump from PDF to source: " + error.getMessage());
        }
    }

    private void navigateFromOutline(Path sourceFile, int lineNumber) {
        if (sourceFile != null && Files.isRegularFile(sourceFile) && !samePath(sourceFile, currentFile)) {
            openLatexFile(sourceFile, true);
            if (!samePath(sourceFile, currentFile)) {
                return;
            }
        }

        try {
            selectEditorLine(lineNumber);
            setStatus("Outline -> line " + lineNumber);
        } catch (BadLocationException error) {
            setStatus("Could not jump to outline line: " + error.getMessage());
        }
    }

    private void selectEditorLine(int lineNumber) throws BadLocationException {
        Element root = editor.getDocument().getDefaultRootElement();
        int lineIndex = Math.max(0, Math.min(lineNumber - 1, root.getElementCount() - 1));
        Element line = root.getElement(lineIndex);
        int start = line.getStartOffset();
        int end = Math.min(line.getEndOffset(), editor.getDocument().getLength());
        int selectionEnd = Math.max(start, end - 1);

        // Selecting the whole line makes the jump visible even when the caret lands in dense source.
        editor.requestFocusInWindow();
        editor.setCaretPosition(start);
        editor.moveCaretPosition(selectionEnd);

        Rectangle lineRect = editor.modelToView2D(start).getBounds();
        lineRect.grow(0, 80);
        editor.scrollRectToVisible(lineRect);
        updateCaretStatus();
    }

    private boolean samePath(Path first, Path second) {
        if (first == null || second == null) {
            return false;
        }
        return first.toAbsolutePath().normalize().toString()
            .equalsIgnoreCase(second.toAbsolutePath().normalize().toString());
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JComboBox<ThemeChoice> themeSelector = new JComboBox<>(new ThemeChoice[] {
            new ThemeChoice(SYSTEM_THEME_ID, "System (" + resolveTheme(SYSTEM_THEME_ID).label() + ")"),
            new ThemeChoice(LIGHT_THEME_ID, "Light"),
            new ThemeChoice(DARK_THEME_ID, "Dark")
        });
        for (int index = 0; index < themeSelector.getItemCount(); index++) {
            if (themeSelector.getItemAt(index).id().equals(themeMode)) {
                themeSelector.setSelectedIndex(index);
                break;
            }
        }

        JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(
            autoCompileDelayMs,
            MIN_AUTO_COMPILE_DELAY_MS,
            MAX_AUTO_COMPILE_DELAY_MS,
            250
        ));
        JCheckBox hideBuildFiles = new JCheckBox("Hide generated LaTeX build files", hideGeneratedFiles);
        JTextArea pathsText = new JTextArea(settingsSummary());
        pathsText.setEditable(false);
        pathsText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        pathsText.setCaretPosition(0);

        JButton installTectonic = button("Install Tectonic", () -> {
            dialog.dispose();
            installToolFromSettings(ToolType.TECTONIC);
        });
        JButton installPandoc = button("Install Pandoc", () -> {
            dialog.dispose();
            installToolFromSettings(ToolType.PANDOC);
        });
        JButton openTemplates = button("Templates Folder", this::openTemplatesFolder);
        JButton apply = button("Apply", () -> {
            ThemeChoice selectedTheme = (ThemeChoice) themeSelector.getSelectedItem();
            if (selectedTheme != null) {
                setThemeMode(selectedTheme.id());
            }
            setAutoCompileDelay((Integer) delaySpinner.getValue());
            setHideGeneratedFiles(hideBuildFiles.isSelected());
            pathsText.setText(settingsSummary());
            pathsText.setCaretPosition(0);
            setStatus("Settings saved");
        });
        JButton close = button("Close", dialog::dispose);

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        buttonRow.add(installTectonic);
        buttonRow.add(installPandoc);
        buttonRow.add(openTemplates);
        buttonRow.add(apply);
        buttonRow.add(close);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(comboRow("Theme", themeSelector));
        controls.add(spinnerRow("Auto compile ms", delaySpinner));
        JPanel checkRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        checkRow.add(hideBuildFiles);
        controls.add(checkRow);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(controls, BorderLayout.NORTH);
        content.add(new JScrollPane(pathsText), BorderLayout.CENTER);
        content.add(buttonRow, BorderLayout.SOUTH);
        applyComponentTheme(content);

        dialog.setContentPane(content);
        dialog.setSize(620, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String settingsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Java\n");
        summary.append("  Version: ").append(System.getProperty("java.version")).append('\n');
        summary.append("  Runtime: ").append(System.getProperty("java.home")).append("\n\n");

        summary.append("Tools\n");
        summary.append(toolStatusLine(ToolType.TECTONIC));
        summary.append(toolStatusLine(ToolType.PANDOC));
        summary.append("  Managed tools folder: ").append(AppPaths.managedToolsDirectory()).append('\n');
        summary.append("  Bundled tools folder: ").append(AppPaths.bundledToolsDirectory()).append("\n\n");

        summary.append("Project\n");
        summary.append("  Folder: ").append(currentProjectRoot == null ? "(none)" : currentProjectRoot).append('\n');
        summary.append("  Main file: ").append(compileSourceFile() == null ? "(none)" : compileSourceFile()).append('\n');
        summary.append("  Output folder: ").append(outputFolderText()).append("\n\n");

        summary.append("Templates\n");
        summary.append("  User templates folder: ").append(templateService.templatesDirectory()).append("\n\n");

        summary.append("Compile\n");
        summary.append("  Auto compile: ").append(autoCompileToggle != null && autoCompileToggle.isSelected() ? "on" : "off").append('\n');
        summary.append("  Auto compile delay: ").append(autoCompileDelayMs).append(" ms\n");
        summary.append("  Hide build files: ").append(hideGeneratedFiles ? "yes" : "no").append("\n\n");

        summary.append("Hidden for now\n");
        summary.append("  Google Drive sync\n");
        summary.append("  PowerPoint export\n");
        return summary.toString();
    }

    private JPanel comboRow(String labelText, JComboBox<?> comboBox) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(110, 26));
        row.add(label, BorderLayout.WEST);
        row.add(comboBox, BorderLayout.CENTER);
        return row;
    }

    private JPanel spinnerRow(String labelText, JSpinner spinner) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(110, 26));
        row.add(label, BorderLayout.WEST);
        row.add(spinner, BorderLayout.CENTER);
        return row;
    }

    private String toolStatusLine(ToolType toolType) {
        try {
            Optional<Path> path = toolManager.findTool(toolType);
            return "  " + toolType.displayName() + ": " + (path.isPresent() ? path.get() : "not installed") + '\n';
        } catch (IOException error) {
            return "  " + toolType.displayName() + ": could not check (" + error.getMessage() + ")\n";
        }
    }

    private String outputFolderText() {
        Path source = compileSourceFile();
        if (source != null && source.getParent() != null) {
            return source.getParent().toString();
        }
        if (currentProjectRoot != null) {
            return currentProjectRoot.toString();
        }
        return "(none)";
    }

    private void installToolFromSettings(ToolType toolType) {
        if (operationRunning) {
            setStatus("Wait for the current compile/export before installing tools.");
            return;
        }

        setStatus("Installing " + toolType.displayName() + "...");
        logs.setText("");
        setLogsVisible(true);
        operationRunning = true;
        setToolbarEnabled(false);

        SwingWorker<Path, String> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return toolManager.ensureTool(MainWindow.this, toolType, message -> publish(message));
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(MainWindow.this::appendLog);
            }

            @Override
            protected void done() {
                operationRunning = false;
                setToolbarEnabled(true);
                try {
                    Path installed = get();
                    if (installed == null) {
                        setStatus(toolType.displayName() + " install cancelled");
                    } else {
                        setStatus(toolType.displayName() + " ready: " + installed);
                    }
                } catch (Exception error) {
                    showError("Could not install " + toolType.displayName(), error);
                }
            }
        };
        worker.execute();
    }

    private void openGeneratedFile(Path file) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }

        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException error) {
            appendLog("Exported file could not be opened automatically: " + error.getMessage());
        }
    }

    private void openProjectFile(Path file) {
        if (file == null) {
            return;
        }

        if (Files.isDirectory(file)) {
            setStatus("Project root stays at " + currentProjectRoot + ". Open files from the tree or use Open to switch folders.");
        } else if (Files.isRegularFile(file) && isTexFile(file)) {
            openLatexFile(file, true);
        } else if (Files.isRegularFile(file)) {
            openGeneratedFile(file);
        }
    }

    private void setMainProjectFile(Path file) {
        if (file == null || !Files.isRegularFile(file) || !isTexFile(file)) {
            setStatus("Choose a .tex file as the main file.");
            return;
        }

        Path normalized = file.toAbsolutePath().normalize();
        mainFile = normalized;
        Path parent = normalized.getParent();
        if (parent != null && (currentProjectRoot == null || !isSameOrInside(currentProjectRoot, normalized))) {
            currentProjectRoot = parent;
            restartProjectWatcher();
        }
        refreshProjectFiles();
        loadExistingPdfPreview();
        updateTitle();
        setStatus("Main file set to " + normalized.getFileName());
    }

    private void navigateBackProjectFolder(Path projectRoot) {
        setStatus("Already at the project root. Use Open to switch folders.");
    }

    private void createProjectTexFile(Path projectRoot) {
        if (operationRunning) {
            setStatus("Wait for the current compile/export to finish before creating a file.");
            return;
        }

        if (!isUsableProjectRoot(projectRoot)) {
            setStatus("Save the current LaTeX file before creating project files.");
            return;
        }

        if (!confirmDiscardUnsavedChanges()) {
            return;
        }

        String name = promptProjectChildName("New LaTeX File", "File name:", "new-file.tex");
        if (name == null) {
            return;
        }

        Path target = resolveNewProjectChild(projectRoot, name, "tex");
        if (target == null) {
            return;
        }

        try {
            Files.writeString(target, STARTER_DOCUMENT, StandardCharsets.UTF_8);
            refreshProjectFiles();
            openLatexFile(target, false);
            setStatus("Created " + target);
        } catch (IOException error) {
            showError("Could not create LaTeX file", error);
        }
    }

    private void createProjectFolder(Path projectRoot) {
        if (operationRunning) {
            setStatus("Wait for the current compile/export to finish before creating a folder.");
            return;
        }

        if (!isUsableProjectRoot(projectRoot)) {
            setStatus("Save the current LaTeX file before creating project folders.");
            return;
        }

        String name = promptProjectChildName("New Folder", "Folder name:", "figures");
        if (name == null) {
            return;
        }

        Path target = resolveNewProjectChild(projectRoot, name, "");
        if (target == null) {
            return;
        }

        try {
            Files.createDirectory(target);
            refreshProjectFiles();
            setStatus("Created folder " + target);
        } catch (IOException error) {
            showError("Could not create folder", error);
        }
    }

    private void renameProjectItem(Path item) {
        if (!canChangeProjectItem(item)) {
            return;
        }

        Path source = item.toAbsolutePath().normalize();
        if (!confirmUsedProjectItemChange(source, "renaming")) {
            return;
        }

        Path parent = source.getParent();
        if (parent == null) {
            setStatus("This item cannot be renamed.");
            return;
        }

        String oldName = source.getFileName().toString();
        String newName = promptProjectChildName("Rename", "New name:", oldName);
        if (newName == null || newName.equals(oldName)) {
            return;
        }

        String extension = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) ? "" : extensionWithoutDot(source);
        Path target = resolveNewProjectChild(parent, newName, extension);
        if (target == null) {
            return;
        }

        try {
            Files.move(source, target);
            updateOpenFileAfterRelocation(source, target);
            refreshProjectFiles();
            setStatus("Renamed " + oldName + " to " + target.getFileName());
        } catch (IOException error) {
            showError("Could not rename item", error);
        }
    }

    private void copyProjectItem(Path item) {
        if (!canChangeProjectItem(item)) {
            return;
        }

        Path source = item.toAbsolutePath().normalize();
        Path destinationFolder = chooseDestinationFolder("Copy To Folder");
        if (destinationFolder == null) {
            return;
        }

        Path target = destinationFolder.resolve(source.getFileName()).toAbsolutePath().normalize();
        if (!validateCopyOrMoveTarget(source, target, "copy")) {
            return;
        }

        try {
            copyRecursively(source, target);
            refreshProjectFiles();
            setStatus("Copied " + source.getFileName() + " to " + destinationFolder);
        } catch (IOException error) {
            showError("Could not copy item", error);
        }
    }

    private void moveProjectItem(Path item) {
        if (!canChangeProjectItem(item)) {
            return;
        }

        Path source = item.toAbsolutePath().normalize();
        Path destinationFolder = chooseDestinationFolder("Move To Folder");
        if (destinationFolder == null) {
            return;
        }

        moveProjectItemToFolder(source, destinationFolder);
    }

    private void moveProjectItemToFolder(Path item, Path destinationFolder) {
        if (!canChangeProjectItem(item)) {
            return;
        }

        Path source = item.toAbsolutePath().normalize();
        Path destination = destinationFolder == null ? null : destinationFolder.toAbsolutePath().normalize();
        if (destination == null || !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
            setStatus("Drop files onto an existing folder.");
            return;
        }

        if (currentProjectRoot != null && (!isSameOrInside(currentProjectRoot, source) || !isSameOrInside(currentProjectRoot, destination))) {
            setStatus("Drag and drop stays inside the opened project folder.");
            return;
        }

        if (source.getParent() != null && samePath(source.getParent(), destination)) {
            setStatus(source.getFileName() + " is already in " + pathDisplayName(destination, destination.toString()));
            return;
        }

        if (!confirmUsedProjectItemChange(source, "moving")) {
            return;
        }

        Path target = destination.resolve(source.getFileName()).toAbsolutePath().normalize();
        if (!validateCopyOrMoveTarget(source, target, "move")) {
            return;
        }

        try {
            Files.move(source, target);
            updateOpenFileAfterRelocation(source, target);
            refreshProjectFiles();
            setStatus("Moved " + source.getFileName() + " to " + destination);
        } catch (IOException error) {
            showError("Could not move item", error);
        }
    }

    private void deleteProjectItem(Path item) {
        if (!canChangeProjectItem(item)) {
            return;
        }

        Path target = item.toAbsolutePath().normalize();
        if (!confirmUsedProjectItemChange(target, "deleting")) {
            return;
        }

        boolean directory = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
        boolean deletesOpenFile = currentFile != null && isSameOrInside(target, currentFile);
        if (deletesOpenFile && dirty && !confirmDiscardUnsavedChanges()) {
            return;
        }

        String message = directory
            ? "Delete folder " + target.getFileName() + " and all of its contents?"
            : "Delete file " + target.getFileName() + "?";
        int choice = JOptionPane.showConfirmDialog(
            this,
            message,
            "Delete Project Item",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            deleteRecursively(target);
            if (mainFile != null && isSameOrInside(target, mainFile)) {
                mainFile = detectMainFile(currentProjectRoot);
            }
            removeOpenTabsAtOrInside(target);
            if (deletesOpenFile) {
                clearOpenFileAfterDelete();
            } else {
                refreshEditorTabs();
            }
            refreshProjectFiles();
            setStatus("Deleted " + target.getFileName());
        } catch (IOException error) {
            showError("Could not delete item", error);
        }
    }

    private boolean canChangeProjectItem(Path item) {
        if (operationRunning) {
            setStatus("Wait for the current compile/export to finish before changing files.");
            return false;
        }

        if (item == null || !Files.exists(item, LinkOption.NOFOLLOW_LINKS)) {
            refreshProjectFiles();
            setStatus("That project item no longer exists.");
            return false;
        }

        return true;
    }

    private boolean confirmUsedProjectItemChange(Path item, String action) {
        if (projectFilesPanel == null || !projectFilesPanel.hasKnownUsedItemAtOrInside(item)) {
            return true;
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            "This item appears to be used by the current LaTeX project.\n\nContinue " + action + " it?",
            "Project Reference Warning",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
    }

    private Path chooseDestinationFolder(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (currentProjectRoot != null) {
            chooser.setCurrentDirectory(currentProjectRoot.toFile());
        }

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Path destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
            JOptionPane.showMessageDialog(
                this,
                "Choose an existing folder.",
                "Invalid Folder",
                JOptionPane.WARNING_MESSAGE
            );
            return null;
        }
        return destination;
    }

    private boolean validateCopyOrMoveTarget(Path source, Path target, String operation) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            JOptionPane.showMessageDialog(
                this,
                target.getFileName() + " already exists in that folder.",
                "Already Exists",
                JOptionPane.WARNING_MESSAGE
            );
            return false;
        }

        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && isSameOrInside(source, target)) {
            JOptionPane.showMessageDialog(
                this,
                "Choose a folder outside " + source.getFileName() + " to " + operation + " it.",
                "Invalid Folder",
                JOptionPane.WARNING_MESSAGE
            );
            return false;
        }

        return true;
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        // Folder copy is recursive so project subfolders can be moved around from the file panel.
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(target);
            try (DirectoryStream<Path> children = Files.newDirectoryStream(source)) {
                for (Path child : children) {
                    copyRecursively(child, target.resolve(child.getFileName()));
                }
            }
            return;
        }

        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
    }

    private void deleteRecursively(Path target) throws IOException {
        // Symlinked folders are not followed; deleting a link should not delete the linked folder's contents.
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(target)) {
                for (Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(target);
    }

    private void updateOpenFileAfterRelocation(Path source, Path target) {
        updateOpenTabsAfterRelocation(source, target);

        if (mainFile != null) {
            Path compileFile = mainFile.toAbsolutePath().normalize();
            if (samePath(compileFile, source)) {
                mainFile = target;
            } else if (isSameOrInside(source, compileFile)) {
                mainFile = target.resolve(source.relativize(compileFile)).toAbsolutePath().normalize();
            }
        }

        if (currentFile == null) {
            return;
        }

        Path openFile = currentFile.toAbsolutePath().normalize();
        if (samePath(openFile, source)) {
            currentFile = target;
        } else if (isSameOrInside(source, openFile)) {
            currentFile = target.resolve(source.relativize(openFile)).toAbsolutePath().normalize();
        } else {
            return;
        }

        if (currentProjectRoot == null || !isSameOrInside(currentProjectRoot, currentFile)) {
            currentProjectRoot = currentFile.getParent();
        }
        restartProjectWatcher();
        loadExistingPdfPreview();
        refreshEditorTabs();
        updateTitle();
    }

    private void updateOpenTabsAfterRelocation(Path source, Path target) {
        if (source == null || target == null || openFileOrder.isEmpty()) {
            return;
        }

        Map<Path, OpenFileTab> relocatedTabs = new LinkedHashMap<>();
        List<Path> relocatedOrder = new ArrayList<>();
        for (Path path : openFileOrder) {
            OpenFileTab tab = openFileTabs.get(path);
            if (tab == null) {
                continue;
            }

            Path newPath = path;
            if (samePath(path, source)) {
                newPath = target.toAbsolutePath().normalize();
            } else if (isSameOrInside(source, path)) {
                newPath = target.resolve(source.relativize(path)).toAbsolutePath().normalize();
            }

            OpenFileTab relocated = new OpenFileTab(newPath, tab.text);
            relocated.dirty = tab.dirty;
            relocated.caretPosition = tab.caretPosition;
            relocated.selectionStart = tab.selectionStart;
            relocated.selectionEnd = tab.selectionEnd;
            relocatedTabs.put(newPath, relocated);
            relocatedOrder.add(newPath);
        }

        openFileTabs.clear();
        openFileTabs.putAll(relocatedTabs);
        openFileOrder.clear();
        openFileOrder.addAll(relocatedOrder);
        refreshEditorTabs();
    }

    private void clearOpenFileAfterDelete() {
        removeOpenTabsAtOrInside(currentFile);
        currentFile = null;
        if (mainFile == null || !Files.isRegularFile(mainFile)) {
            mainFile = detectMainFile(currentProjectRoot);
        }
        loading = true;
        try {
            editor.setText("");
            editor.setCaretPosition(0);
        } finally {
            loading = false;
        }
        dirty = false;
        resetUndoHistory();
        refreshSyntaxHighlighting();
        refreshEditorTabs();
        pdfPreview.clear("Create or open a .tex file to preview the PDF here.");
        logs.setText("");
        updateTitle();
    }

    private boolean isSameOrInside(Path parent, Path child) {
        if (parent == null || child == null) {
            return false;
        }

        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        String parentText = normalizedParent.toString().toLowerCase(Locale.ROOT);
        String childText = normalizedChild.toString().toLowerCase(Locale.ROOT);
        return childText.equals(parentText) || childText.startsWith(parentText + File.separator);
    }

    private String extensionWithoutDot(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Path detectMainFile(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return null;
        }

        Path explicitMain = projectRoot.resolve("main.tex").toAbsolutePath().normalize();
        if (Files.isRegularFile(explicitMain)) {
            return explicitMain;
        }

        Path onlyTexFile = null;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(projectRoot, "*.tex")) {
            for (Path child : children) {
                if (onlyTexFile != null) {
                    return null;
                }
                onlyTexFile = child.toAbsolutePath().normalize();
            }
        } catch (IOException ignored) {
            // A folder can still be browsed even if automatic main-file detection fails.
        }
        return onlyTexFile;
    }

    private boolean isUsableProjectRoot(Path projectRoot) {
        return projectRoot != null && Files.isDirectory(projectRoot);
    }

    private String promptProjectChildName(String title, String message, String defaultValue) {
        Object result = JOptionPane.showInputDialog(
            this,
            message,
            title,
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            defaultValue
        );
        if (!(result instanceof String value)) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Path resolveNewProjectChild(Path projectRoot, String rawName, String extension) {
        String name = rawName.trim();
        if (!extension.isBlank() && !name.toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            name += "." + extension;
        }

        if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || name.matches(".*[<>:\"|?*].*")) {
            JOptionPane.showMessageDialog(
                this,
                "Use a simple name without slashes or Windows special characters.",
                "Invalid Name",
                JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        Path target = projectRoot.resolve(name).toAbsolutePath().normalize();
        if (!samePath(target.getParent(), projectRoot.toAbsolutePath().normalize())) {
            JOptionPane.showMessageDialog(
                this,
                "The new item must stay inside the current LaTeX folder.",
                "Invalid Location",
                JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        if (Files.exists(target)) {
            JOptionPane.showMessageDialog(
                this,
                target.getFileName() + " already exists.",
                "Already Exists",
                JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        return target;
    }

    private boolean isTexFile(Path file) {
        return file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".tex");
    }

    private void refreshProjectFiles() {
        if (projectFilesPanel != null) {
            projectFilesPanel.refresh(
                currentProjectRoot,
                currentFile,
                compileSourceFile(),
                currentFile == null ? "" : editor.getText(),
                projectReferenceSourceText()
            );
        }
    }

    private String projectReferenceSourceText() {
        Path source = compileSourceFile();
        if (source == null) {
            return currentFile == null ? "" : editor.getText();
        }

        if (currentFile != null && samePath(source, currentFile)) {
            return editor.getText();
        }

        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private void scheduleProjectFilesRefresh() {
        if (projectFilesRefreshTimer != null) {
            projectFilesRefreshTimer.restart();
        }
    }

    private void restartProjectWatcher() {
        stopProjectWatcher();
        if (currentProjectRoot == null || !Files.isDirectory(currentProjectRoot)) {
            return;
        }

        try {
            WatchService watcher = currentProjectRoot.getFileSystem().newWatchService();
            Map<WatchKey, Path> directories = new HashMap<>();
            registerProjectWatchDirectories(watcher, directories, currentProjectRoot, 0);
            projectWatchService = watcher;
            watchedProjectRoot = currentProjectRoot.toAbsolutePath().normalize();
            projectWatchThread = new Thread(
                () -> runProjectWatchLoop(watcher, directories),
                "LaTeX-Compiler-project-watch"
            );
            projectWatchThread.setDaemon(true);
            projectWatchThread.start();
        } catch (IOException error) {
            appendLog("Project file watcher could not start: " + error.getMessage());
        }
    }

    private void stopProjectWatcher() {
        WatchService watcher = projectWatchService;
        projectWatchService = null;
        watchedProjectRoot = null;
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException ignored) {
                // Closing a watcher is best effort during project switches and app shutdown.
            }
        }
        projectWatchThread = null;
    }

    private void registerProjectWatchDirectories(
        WatchService watcher,
        Map<WatchKey, Path> directories,
        Path root,
        int depth
    ) throws IOException {
        if (root == null || depth > PROJECT_WATCH_DEPTH || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try {
            WatchKey key = root.register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            );
            directories.put(key, root.toAbsolutePath().normalize());
        } catch (IOException | SecurityException ignored) {
            return;
        }

        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    registerProjectWatchDirectories(watcher, directories, child, depth + 1);
                }
            }
        } catch (IOException | SecurityException ignored) {
            // Protected folders should not disable watching the rest of the project.
        }
    }

    private void runProjectWatchLoop(WatchService watcher, Map<WatchKey, Path> directories) {
        while (watcher == projectWatchService) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (ClosedWatchServiceException ignored) {
                return;
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }

            Path directory = directories.get(key);
            boolean changed = false;
            if (directory != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        changed = true;
                        continue;
                    }

                    changed = true;
                    Path child = directory.resolve((Path) event.context()).toAbsolutePath().normalize();
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        try {
                            registerProjectWatchDirectories(watcher, directories, child, 0);
                        } catch (IOException ignored) {
                            // The file panel will still refresh even if the new folder cannot be watched.
                        }
                    }
                }
            }

            if (!key.reset()) {
                directories.remove(key);
            }

            if (changed) {
                SwingUtilities.invokeLater(this::handleWatchedProjectChange);
            }
        }
    }

    private void handleWatchedProjectChange() {
        if (watchedProjectRoot == null || currentProjectRoot == null || !samePath(watchedProjectRoot, currentProjectRoot)) {
            return;
        }

        scheduleProjectFilesRefresh();
        setStatus("Project files refreshed");
    }

    private void appendLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        logs.append(message);
        if (!message.endsWith(System.lineSeparator())) {
            logs.append(System.lineSeparator());
        }
        logs.setCaretPosition(logs.getDocument().getLength());
    }

    private void navigateFromLogClick(MouseEvent event) {
        if (event.getClickCount() != 1 || logs.getDocument().getLength() == 0) {
            return;
        }

        LogReference reference = logReferenceAt(event.getPoint());
        if (reference != null) {
            navigateToLogReference(reference);
        }
    }

    private LogReference logReferenceAt(Point point) {
        if (logs == null || logs.getDocument().getLength() == 0) {
            return null;
        }

        int offset = logs.viewToModel2D(point);
        if (offset < 0) {
            return null;
        }

        try {
            Element root = logs.getDocument().getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(offset));
            int length = line.getEndOffset() - line.getStartOffset();
            String text = logs.getDocument().getText(line.getStartOffset(), length);
            return parseLogReference(text);
        } catch (BadLocationException error) {
            setStatus("Could not read clicked log line: " + error.getMessage());
            return null;
        }
    }

    private LogReference parseLogReference(String text) {
        Matcher pathMatcher = LOG_TEX_PATH_WITH_LINE.matcher(text);
        if (pathMatcher.find()) {
            Path source = resolveLogSourcePath(pathMatcher.group(1));
            return new LogReference(source, parsePositiveInt(pathMatcher.group(2)));
        }

        Matcher lineMatcher = LOG_LINE_NUMBER.matcher(text);
        if (lineMatcher.find()) {
            return new LogReference(compileSourceFile(), parsePositiveInt(lineMatcher.group(1)));
        }

        return null;
    }

    private Path resolveLogSourcePath(String rawPath) {
        String cleaned = rawPath.trim();
        int arrow = cleaned.lastIndexOf("-->");
        if (arrow >= 0) {
            cleaned = cleaned.substring(arrow + 3).trim();
        }

        Matcher windowsPath = Pattern.compile("[A-Za-z]:[\\\\/].*").matcher(cleaned);
        if (windowsPath.find()) {
            cleaned = windowsPath.group();
        } else {
            int space = cleaned.lastIndexOf(' ');
            if (space >= 0) {
                cleaned = cleaned.substring(space + 1);
            }
        }

        cleaned = cleaned.replace("\"", "");
        Path path = Path.of(cleaned);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }

        Path base = currentProjectRoot;
        if (base == null && compileSourceFile() != null) {
            base = compileSourceFile().getParent();
        }
        return base == null ? path.toAbsolutePath().normalize() : base.resolve(path).toAbsolutePath().normalize();
    }

    private int parsePositiveInt(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void navigateToLogReference(LogReference reference) {
        if (reference.sourceFile() != null && Files.isRegularFile(reference.sourceFile()) && isTexFile(reference.sourceFile())) {
            if (!samePath(reference.sourceFile(), currentFile)) {
                openLatexFile(reference.sourceFile(), true);
                if (!samePath(reference.sourceFile(), currentFile)) {
                    return;
                }
            }
        } else if (currentFile == null) {
            setStatus("Open a LaTeX source file before using line-only log navigation.");
            return;
        }

        try {
            selectEditorLine(reference.line());
            setStatus("Log -> line " + reference.line());
        } catch (BadLocationException error) {
            setStatus("Could not jump to log line: " + error.getMessage());
        }
    }

    private void closeWindow() {
        if (confirmDiscardUnsavedChanges()) {
            stopProjectWatcher();
            dispose();
            System.exit(0);
        }
    }

    private boolean confirmDiscardUnsavedChanges() {
        rememberCurrentOpenTab();
        if (!hasUnsavedChanges()) {
            return true;
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            "You have unsaved changes. Continue without saving?",
            "Unsaved Changes",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
    }

    private boolean hasUnsavedChanges() {
        if (dirty) {
            return true;
        }
        return openFileTabs.values().stream().anyMatch(tab -> tab.dirty);
    }

    private Path ensureExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            return path;
        }
        return path.resolveSibling(fileName + "." + extension);
    }

    private void setToolbarEnabled(boolean enabled) {
        if (toolbar == null) {
            return;
        }

        for (java.awt.Component component : toolbar.getComponents()) {
            if (component == autoCompileToggle || component == toggleFilesButton || component == toggleLogsButton) {
                component.setEnabled(true);
            } else {
                component.setEnabled(enabled);
            }
        }
        updateAutoCompileToggleAppearance();
        updateUndoRedoButtons();
    }

    private void markDirty() {
        if (loading || (syntaxHighlighter != null && syntaxHighlighter.isHighlighting())) {
            return;
        }
        dirty = true;
        rememberCurrentOpenTab();
        updateEditorTabTitles();
        updateTitle();
        scheduleSyntaxHighlighting();
        scheduleAutoCompile();
        scheduleProjectFilesRefresh();
    }

    // Turning auto compile on starts watching the current saved file immediately.
    private void toggleAutoCompile() {
        if (autoCompileToggle.isSelected()) {
            setStatus("Auto compile on");
            scheduleAutoCompile();
        } else {
            autoCompileTimer.stop();
            compileQueued = false;
            setStatus("Auto compile off");
        }
        updateAutoCompileToggleAppearance();
    }

    // Debouncing restarts the timer on each edit and compiles only after typing pauses.
    private void scheduleAutoCompile() {
        if (autoCompileToggle == null || !autoCompileToggle.isSelected() || loading) {
            return;
        }

        if (compileSourceFile() == null) {
            setStatus("Choose or save a main .tex file to use auto compile");
            return;
        }

        autoCompileTimer.restart();
    }

    private void toggleLogs() {
        setLogsVisible(logsPanel == null || !logsPanel.isVisible());
    }

    private void toggleProjectFiles() {
        setProjectFilesVisible(!projectFilesVisible);
    }

    private void setProjectFilesVisible(boolean visible) {
        if (projectFilesCard == null || projectFilesSlot == null || projectWorkspaceSplit == null) {
            return;
        }

        projectFilesVisible = visible;
        if (visible) {
            setProjectFilesSlotWidth(Math.max(180, projectFilesDividerLocation));
            projectFilesCard.show(projectFilesSlot, PROJECT_FILES_EXPANDED_CARD);
            projectWorkspaceSplit.setDividerSize(projectFilesDividerSize);
            projectWorkspaceSplit.setDividerLocation(Math.max(180, projectFilesDividerLocation));
        } else {
            projectFilesDividerLocation = Math.max(180, projectWorkspaceSplit.getDividerLocation());
            setProjectFilesSlotWidth(COLLAPSED_PROJECT_FILES_WIDTH);
            projectFilesCard.show(projectFilesSlot, PROJECT_FILES_COLLAPSED_CARD);
            projectWorkspaceSplit.setDividerSize(COLLAPSED_PROJECT_FILES_DIVIDER_SIZE);
            projectWorkspaceSplit.setDividerLocation(COLLAPSED_PROJECT_FILES_WIDTH);
        }

        if (toggleFilesButton != null) {
            toggleFilesButton.setText(visible ? "Hide Files" : "Show Files");
        }
        if (toggleFilesMenuItem != null) {
            toggleFilesMenuItem.setText(visible ? "Hide Files" : "Show Files");
        }
        revalidate();
        repaint();
    }

    private void setProjectFilesSlotWidth(int width) {
        if (projectFilesSlot == null) {
            return;
        }

        projectFilesSlot.setMinimumSize(new Dimension(width, 120));
        projectFilesSlot.setPreferredSize(new Dimension(width, 100));
    }

    private void setLogsVisible(boolean visible) {
        if (logsPanel == null) {
            return;
        }

        logsPanel.setVisible(visible);
        if (toggleLogsButton != null) {
            toggleLogsButton.setText(visible ? "Hide Logs" : "Show Logs");
        }
        if (toggleLogsMenuItem != null) {
            toggleLogsMenuItem.setText(visible ? "Hide Logs" : "Show Logs");
        }
        revalidate();
        repaint();
    }

    private void updateTitle() {
        String fileName = openLocationDisplayName();
        setTitle((dirty ? "*" : "") + fileName + " - LaTeX Compiler");
        updateCaretStatus();
    }

    private String openLocationDisplayName() {
        if (currentFile != null) {
            return pathDisplayName(currentFile, "Untitled");
        }
        if (currentProjectRoot != null) {
            return pathDisplayName(currentProjectRoot, "Project");
        }
        return "Untitled";
    }

    private String pathDisplayName(Path path, String fallback) {
        if (path == null) {
            return fallback;
        }

        Path fileName = path.getFileName();
        if (fileName != null) {
            return fileName.toString();
        }

        // Drive roots such as C:\ have no file-name segment, so use the whole path.
        String text = path.toAbsolutePath().normalize().toString();
        return text.isBlank() ? fallback : text;
    }

    private void installEditorShortcuts(JTextComponent area) {
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        area.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                undo();
            }
        });

        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "redo");
        area.getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                redo();
            }
        });
    }

    private void installGlobalShortcuts() {
        addGlobalShortcut("undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), this::undo);
        addGlobalShortcut("redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), this::redo);
        addGlobalShortcut("redo-shift", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::redo);
        addGlobalShortcut("find", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), this::showFindDialog);
        addGlobalShortcut("replace", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), this::showReplaceDialog);
        addGlobalShortcut("templates", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::showTemplatesDialog);
        addGlobalShortcut("format-latex", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::formatLatex);
    }

    private void addGlobalShortcut(String name, KeyStroke keyStroke, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    private void undo() {
        if (operationRunning || !undoManager.canUndo()) {
            return;
        }

        undoManager.undo();
        dirty = true;
        rememberCurrentOpenTab();
        updateEditorTabTitles();
        updateTitle();
        updateUndoRedoButtons();
        scheduleAutoCompile();
    }

    private void redo() {
        if (operationRunning || !undoManager.canRedo()) {
            return;
        }

        undoManager.redo();
        dirty = true;
        rememberCurrentOpenTab();
        updateEditorTabTitles();
        updateTitle();
        updateUndoRedoButtons();
        scheduleAutoCompile();
    }

    private void resetUndoHistory() {
        undoManager.discardAllEdits();
        updateUndoRedoButtons();
    }

    private boolean isStyleOnlyEdit(javax.swing.undo.UndoableEdit edit) {
        if (syntaxHighlighter != null && syntaxHighlighter.isHighlighting()) {
            return true;
        }
        if (edit instanceof AbstractDocument.DefaultDocumentEvent documentEvent) {
            return documentEvent.getType() == DocumentEvent.EventType.CHANGE;
        }
        return false;
    }

    private void scheduleSyntaxHighlighting() {
        if (syntaxHighlighter != null) {
            syntaxHighlighter.schedule();
        }
    }

    private void refreshSyntaxHighlighting() {
        if (syntaxHighlighter != null) {
            syntaxHighlighter.refreshNow();
        }
    }

    private void updateUndoRedoButtons() {
        if (undoButton != null) {
            undoButton.setEnabled(!operationRunning && undoManager.canUndo());
        }
        if (redoButton != null) {
            redoButton.setEnabled(!operationRunning && undoManager.canRedo());
        }
    }

    private void updateCaretStatus() {
        int caret = editor.getCaretPosition();
        int line = 1;
        int column = 1;

        try {
            int lineStart = javax.swing.text.Utilities.getRowStart(editor, caret);
            line = editor.getDocument().getDefaultRootElement().getElementIndex(caret) + 1;
            column = caret - Math.max(0, lineStart) + 1;
        } catch (javax.swing.text.BadLocationException ignored) {
            // Keep the previous coarse position if Swing cannot resolve the caret rectangle.
        }

        String fileName = openLocationDisplayName();
        String caretText = "Line " + line + ", Column " + column;
        String fileKind = currentFile == null && currentProjectRoot != null ? "Folder" : "File";
        fileStatus.setText((dirty ? "Unsaved changes - " : "") + fileKind + ": " + fileName + "  |  " + caretText);
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void showError(String title, Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        appendLog(title + ": " + error.getMessage());
    }

    private final class TemplateListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof LatexTemplate template) {
                String suffix = template.custom() ? "  custom" : "";
                setText(template.category() + "  -  " + template.name() + suffix);
                setToolTipText(template.description());
            }
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setBackground(isSelected ? theme.listSelectionBackground() : theme.panelBackground());
            setForeground(isSelected ? theme.listSelectionForeground() : theme.text());
            return this;
        }
    }

    private final class EditorTextUndoEdit extends AbstractUndoableEdit {
        private final String name;
        private final String beforeText;
        private final int beforeSelectionStart;
        private final int beforeSelectionEnd;
        private final String afterText;
        private final int afterSelectionStart;
        private final int afterSelectionEnd;

        private EditorTextUndoEdit(
            String name,
            String beforeText,
            int beforeSelectionStart,
            int beforeSelectionEnd,
            String afterText,
            int afterSelectionStart,
            int afterSelectionEnd
        ) {
            this.name = name;
            this.beforeText = beforeText;
            this.beforeSelectionStart = beforeSelectionStart;
            this.beforeSelectionEnd = beforeSelectionEnd;
            this.afterText = afterText;
            this.afterSelectionStart = afterSelectionStart;
            this.afterSelectionEnd = afterSelectionEnd;
        }

        @Override
        public void undo() {
            super.undo();
            replaceEditorTextFromUndoableAction(beforeText, beforeSelectionStart, beforeSelectionEnd);
        }

        @Override
        public void redo() {
            super.redo();
            replaceEditorTextFromUndoableAction(afterText, afterSelectionStart, afterSelectionEnd);
        }

        @Override
        public String getPresentationName() {
            return name;
        }
    }

    private record LogReference(Path sourceFile, int line) {
    }
}
