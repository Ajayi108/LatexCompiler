package latexcompiler.ui;

import latexcompiler.export.ExportFormat;
import latexcompiler.export.ExportResult;
import latexcompiler.export.ExportService;
import latexcompiler.format.LatexFormatter;
import latexcompiler.process.ProcessRunner;
import latexcompiler.sync.DriveSyncService;
import latexcompiler.synctex.SourcePosition;
import latexcompiler.synctex.SyncTexService;
import latexcompiler.tools.GithubReleaseClient;
import latexcompiler.tools.ToolDownloader;
import latexcompiler.tools.ToolManager;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MainWindow extends JFrame {
    // Auto compile waits for typing to pause so the app does not compile on every keystroke.
    private static final int AUTO_COMPILE_DELAY_MS = 1500;

    private final JTextPane editor;
    private final JTextArea logs;
    private final JLabel status;
    private final JLabel fileStatus;
    private final PdfPreviewPanel pdfPreview;
    private final ExportService exportService;
    private final DriveSyncService driveSyncService;
    private final SyncTexService syncTexService;
    private final LatexFormatter latexFormatter;
    private final UndoManager undoManager;
    private final Timer autoCompileTimer;
    private LatexSyntaxHighlighter syntaxHighlighter;

    private JToolBar toolbar;
    private JButton undoButton;
    private JButton redoButton;
    private JButton compileButton;
    private JButton toggleLogsButton;
    private JToggleButton autoCompileToggle;
    private JPanel logsPanel;

    private Path currentFile;
    private boolean dirty;
    // Programmatic editor changes should not mark the document as modified.
    private boolean loading;
    // Export work runs in the background; these flags prevent overlapping compiler processes.
    private boolean operationRunning;
    private boolean compileQueued;
    private CompoundEdit activeCompoundEdit;

    public MainWindow() {
        super("LatexCompiler");
        this.exportService = new ExportService(
            new ToolManager(new GithubReleaseClient(), new ToolDownloader()),
            new ProcessRunner()
        );
        this.driveSyncService = new DriveSyncService();
        this.syncTexService = new SyncTexService();
        this.latexFormatter = new LatexFormatter();
        this.undoManager = new UndoManager();
        this.editor = createEditor();
        this.syntaxHighlighter = new LatexSyntaxHighlighter(editor);
        this.logs = createLogs();
        this.status = new JLabel("Ready");
        this.fileStatus = new JLabel("Untitled");
        this.pdfPreview = new PdfPreviewPanel();
        this.pdfPreview.setSourceNavigationHandler(this::navigateFromPdfToSource);
        // Swing Timer fires on the UI thread after the user stops editing for a moment.
        this.autoCompileTimer = new Timer(AUTO_COMPILE_DELAY_MS, event -> compilePdf(true));
        this.autoCompileTimer.setRepeats(false);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(createToolbar(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        installGlobalShortcuts();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                closeWindow();
            }
        });

        loadStarterDocument();
        updateTitle();
    }

    private JTextPane createEditor() {
        JTextPane area = new CodeEditorPane();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
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
        return area;
    }

    private JToolBar createToolbar() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        toolbar.add(button("New", this::newFile));
        toolbar.add(button("Open", this::openFile));
        toolbar.add(button("Save", this::saveFile));
        toolbar.add(button("Save As", this::saveFileAs));
        toolbar.addSeparator();
        undoButton = button("Undo", this::undo);
        redoButton = button("Redo", this::redo);
        toolbar.add(undoButton);
        toolbar.add(redoButton);
        toolbar.add(button("Format LaTeX", this::formatLatex));
        toolbar.addSeparator();

        // Compile is the main action: it saves the .tex file and builds the matching PDF.
        compileButton = button("Compile", () -> compilePdf(false));
        compileButton.setFont(compileButton.getFont().deriveFont(Font.BOLD));
        compileButton.setMargin(new Insets(6, 16, 6, 16));
        toolbar.add(compileButton);

        autoCompileToggle = new JToggleButton("Auto Compile");
        autoCompileToggle.setFocusable(false);
        autoCompileToggle.setMargin(new Insets(6, 12, 6, 12));
        autoCompileToggle.addActionListener(event -> toggleAutoCompile());
        toolbar.add(autoCompileToggle);

        toolbar.addSeparator();
        toggleLogsButton = button("Hide Logs", this::toggleLogs);
        toolbar.add(toggleLogsButton);
        toolbar.addSeparator();
        toolbar.add(button("Export PDF", () -> export(ExportFormat.PDF)));
        toolbar.add(button("Export Word", () -> export(ExportFormat.WORD)));
        toolbar.add(button("Export PowerPoint", () -> export(ExportFormat.POWERPOINT)));
        toolbar.addSeparator();
        toolbar.add(button("Drive Sync", this::showDriveSyncStatus));
        return toolbar;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        button.setFocusable(false);
        button.setMargin(new Insets(6, 10, 6, 10));
        return button;
    }

    private JPanel createMainContent() {
        JScrollPane editorPane = new JScrollPane(editor);
        editorPane.setBorder(BorderFactory.createTitledBorder("LaTeX Source"));
        editorPane.setRowHeaderView(new LineNumberView(editor));
        editorPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPane, pdfPreview);
        workspace.setResizeWeight(0.54);
        workspace.setDividerLocation(600);

        logsPanel = new JPanel(new BorderLayout());
        logsPanel.setBorder(BorderFactory.createTitledBorder("Logs"));
        logsPanel.setPreferredSize(new Dimension(100, 190));
        logsPanel.add(new JScrollPane(logs), BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout());
        content.add(workspace, BorderLayout.CENTER);
        content.add(logsPanel, BorderLayout.SOUTH);
        return content;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        panel.add(status, BorderLayout.WEST);
        panel.add(fileStatus, BorderLayout.EAST);
        return panel;
    }

    private void loadStarterDocument() {
        loading = true;
        editor.setText("""
            \\documentclass{article}
            \\usepackage[utf8]{inputenc}
            
            \\title{Untitled Document}
            \\author{}
            \\date{\\today}
            
            \\begin{document}
            
            \\maketitle
            
            Start writing here.
            
            \\end{document}
            """);
        editor.setCaretPosition(0);
        loading = false;
        dirty = false;
        resetUndoHistory();
        refreshSyntaxHighlighting();
    }

    private void newFile() {
        if (!confirmDiscardUnsavedChanges()) {
            return;
        }
        currentFile = null;
        loadStarterDocument();
        logs.setText("");
        pdfPreview.clear("Compile a document to preview the PDF here.");
        setStatus("New document");
        autoCompileTimer.stop();
        updateTitle();
    }

    private void openFile() {
        if (!confirmDiscardUnsavedChanges()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("LaTeX files (*.tex)", "tex"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath();
        try {
            loading = true;
            editor.setText(Files.readString(selected, StandardCharsets.UTF_8));
            editor.setCaretPosition(0);
            currentFile = selected;
            dirty = false;
            resetUndoHistory();
            refreshSyntaxHighlighting();
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
        } else {
            chooser.setSelectedFile(Path.of("document.tex").toFile());
        }

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = ensureExtension(chooser.getSelectedFile().toPath(), "tex");
        currentFile = selected;
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
        logs.setText("");
        appendLog((automatic ? "Auto compile" : format.label()) + " target: " + target);
        setStatus(startMessage);
        operationRunning = true;
        setToolbarEnabled(false);

        SwingWorker<ExportResult, String> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                return exportService.export(MainWindow.this, currentFile, target, format, message -> publish(message));
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
            return currentFile != null && !dirty;
        }

        if (dirty) {
            saveFile();
        }
        return currentFile != null && !dirty;
    }

    private boolean saveBeforeExport() {
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
            return currentFile != null && !dirty;
        }

        if (dirty) {
            saveFile();
        }
        return currentFile != null && !dirty;
    }

    private Path defaultExportFile(ExportFormat format) {
        Path source = currentFile == null ? Path.of("document.tex") : currentFile;
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path parent = source.toAbsolutePath().getParent();
        return parent.resolve(base + "." + format.extension());
    }

    private void loadExistingPdfPreview() {
        if (currentFile == null) {
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

    private void navigateFromPdfToSource(Path pdfFile, int pageNumber, double normalizedX, double normalizedY) {
        if (currentFile == null) {
            setStatus("Open or save the LaTeX source before using PDF click navigation.");
            return;
        }

        try {
            var position = syncTexService.findSourcePosition(pdfFile, currentFile, pageNumber, normalizedX, normalizedY);
            if (position.isEmpty()) {
                setStatus("No SyncTeX match for that PDF position. Recompile the PDF and try again.");
                return;
            }

            SourcePosition sourcePosition = position.get();
            if (!samePath(sourcePosition.sourceFile(), currentFile)) {
                setStatus("PDF maps to " + sourcePosition.sourceFile() + ":" + sourcePosition.line());
                return;
            }

            selectEditorLine(sourcePosition.line());
            setStatus("PDF page " + pageNumber + " -> line " + sourcePosition.line());
        } catch (IOException | BadLocationException error) {
            setStatus("Could not jump from PDF to source: " + error.getMessage());
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

    private void showDriveSyncStatus() {
        JOptionPane.showMessageDialog(
            this,
            "Google Drive sync is not connected yet.\n\nThis app is local-first. The sync layer is reserved so OAuth-based Drive backup can be added without changing the editor/export flow.",
            "Drive Sync",
            JOptionPane.INFORMATION_MESSAGE
        );
        setStatus("Drive sync: " + driveSyncService.status());
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

    private void closeWindow() {
        if (confirmDiscardUnsavedChanges()) {
            dispose();
            System.exit(0);
        }
    }

    private boolean confirmDiscardUnsavedChanges() {
        if (!dirty) {
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
            if (component == autoCompileToggle || component == toggleLogsButton) {
                component.setEnabled(true);
            } else {
                component.setEnabled(enabled);
            }
        }
        updateUndoRedoButtons();
    }

    private void markDirty() {
        if (loading || (syntaxHighlighter != null && syntaxHighlighter.isHighlighting())) {
            return;
        }
        dirty = true;
        updateTitle();
        scheduleSyntaxHighlighting();
        scheduleAutoCompile();
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
    }

    // Debouncing restarts the timer on each edit and compiles only after typing pauses.
    private void scheduleAutoCompile() {
        if (autoCompileToggle == null || !autoCompileToggle.isSelected() || loading) {
            return;
        }

        if (currentFile == null) {
            setStatus("Save the file to use auto compile");
            return;
        }

        autoCompileTimer.restart();
    }

    private void toggleLogs() {
        setLogsVisible(logsPanel == null || !logsPanel.isVisible());
    }

    private void setLogsVisible(boolean visible) {
        if (logsPanel == null) {
            return;
        }

        logsPanel.setVisible(visible);
        if (toggleLogsButton != null) {
            toggleLogsButton.setText(visible ? "Hide Logs" : "Show Logs");
        }
        revalidate();
        repaint();
    }

    private void updateTitle() {
        String fileName = currentFile == null ? "Untitled" : currentFile.getFileName().toString();
        setTitle((dirty ? "*" : "") + fileName + " - LatexCompiler");
        updateCaretStatus();
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

        String fileName = currentFile == null ? "Untitled" : currentFile.getFileName().toString();
        String caretText = "Line " + line + ", Column " + column;
        fileStatus.setText((dirty ? "Unsaved changes - " : "") + fileName + "  |  " + caretText);
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void showError(String title, Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        appendLog(title + ": " + error.getMessage());
    }
}
