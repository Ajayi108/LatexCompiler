package latexcompiler.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfPreviewPanel extends JPanel {
    private static final float DISPLAY_DPI = 120f;
    private static final float MIN_RENDER_DPI = 240f;
    private static final float MAX_RENDER_DPI = 420f;
    private static final float MIN_ZOOM = 0.40f;
    private static final float MAX_ZOOM = 3.00f;
    private static final float ZOOM_STEP = 0.15f;
    private static final int SCROLL_UNIT_INCREMENT = 34;
    private static final int WHEEL_SCROLL_PIXELS = 58;
    private static final int ZOOM_RENDER_DELAY_MS = 90;

    private final DocumentCanvas documentCanvas;
    private final JLabel statusLabel;
    private final JButton previousButton;
    private final JButton nextButton;
    private final JButton zoomOutButton;
    private final JButton zoomInButton;
    private final JButton fitWidthButton;
    private final JButton fitPageButton;
    private final JButton copyPageButton;
    private final JButton copyAllButton;
    private final JComboBox<String> zoomCombo;
    private final Timer renderDebounceTimer;
    private UiTheme theme = UiTheme.light();
    private JPanel toolbarPanel;
    private JPanel navigationPanel;
    private JPanel zoomControlsPanel;
    private JScrollPane scrollPane;

    private Path currentPdf;
    private int pageCount;
    private float zoom;
    private int renderRequestId;
    private boolean updatingZoomCombo;
    private SourceNavigationHandler sourceNavigationHandler;
    private SwingWorker<RenderedDocument, Void> renderWorker;
    private SwingWorker<String, Void> textCopyWorker;
    private boolean suppressNextSourceClick;

    public PdfPreviewPanel() {
        super(new BorderLayout());
        this.documentCanvas = new DocumentCanvas();
        this.statusLabel = new JLabel("No PDF");
        this.previousButton = new JButton("Previous");
        this.nextButton = new JButton("Next");
        this.zoomOutButton = new JButton("-");
        this.zoomInButton = new JButton("+");
        this.fitWidthButton = new JButton("Fit Width");
        this.fitPageButton = new JButton("Fit Page");
        this.copyPageButton = new JButton("Copy Page");
        this.copyAllButton = new JButton("Copy All");
        this.zoomCombo = new JComboBox<>(new String[] {"50%", "75%", "100%", "125%", "150%", "200%"});
        this.zoom = 1.0f;
        this.renderDebounceTimer = new Timer(ZOOM_RENDER_DELAY_MS, event -> renderDocument(false));
        this.renderDebounceTimer.setRepeats(false);

        setBorder(BorderFactory.createTitledBorder("PDF Preview"));
        add(createToolbar(), BorderLayout.NORTH);
        add(createScrollPane(), BorderLayout.CENTER);
        applyTheme(theme);
        installSourceNavigation();
        updateControls();
    }

    public void applyTheme(UiTheme theme) {
        this.theme = theme;
        setBackground(theme.panelBackground());
        setForeground(theme.text());
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(theme.border()),
            "PDF Preview",
            javax.swing.border.TitledBorder.LEADING,
            javax.swing.border.TitledBorder.TOP,
            getFont(),
            theme.text()
        ));

        styleToolbarPanel(toolbarPanel, theme);
        styleToolbarPanel(navigationPanel, theme);
        styleToolbarPanel(zoomControlsPanel, theme);
        statusLabel.setForeground(theme.text());
        styleButton(previousButton, theme);
        styleButton(nextButton, theme);
        styleButton(zoomOutButton, theme);
        styleButton(zoomInButton, theme);
        styleButton(fitWidthButton, theme);
        styleButton(fitPageButton, theme);
        styleButton(copyPageButton, theme);
        styleButton(copyAllButton, theme);
        styleCombo(zoomCombo);
        documentCanvas.applyTheme(theme);
        if (scrollPane != null) {
            scrollPane.setBorder(BorderFactory.createLineBorder(theme.border()));
            scrollPane.getViewport().setBackground(theme.pdfBackground());
        }
        repaint();
    }

    public void setSourceNavigationHandler(SourceNavigationHandler sourceNavigationHandler) {
        this.sourceNavigationHandler = sourceNavigationHandler;
    }

    public void loadPdf(Path pdfFile) {
        if (pdfFile == null || !Files.isRegularFile(pdfFile)) {
            clear("PDF not found");
            return;
        }

        currentPdf = pdfFile;
        renderDocument(true);
    }

    public void clear(String message) {
        renderRequestId++;
        renderDebounceTimer.stop();
        cancelRender();
        currentPdf = null;
        pageCount = 0;
        documentCanvas.clear(message);
        statusLabel.setText("No PDF");
        updateControls();
    }

    private JPanel createToolbar() {
        toolbarPanel = new JPanel(new BorderLayout());

        navigationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        previousButton.addActionListener(event -> scrollToAdjacentPage(-1));
        nextButton.addActionListener(event -> scrollToAdjacentPage(1));
        navigationPanel.add(previousButton);
        navigationPanel.add(nextButton);
        navigationPanel.add(statusLabel);

        zoomControlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        zoomOutButton.setPreferredSize(new Dimension(44, 28));
        zoomInButton.setPreferredSize(new Dimension(44, 28));
        zoomOutButton.setToolTipText("Zoom out");
        zoomInButton.setToolTipText("Zoom in");
        fitWidthButton.setToolTipText("Fit the PDF page width to the preview");
        fitPageButton.setToolTipText("Fit the whole PDF page in the preview");
        copyPageButton.setToolTipText("Copy text from the visible PDF page");
        copyAllButton.setToolTipText("Copy text from every PDF page");
        zoomCombo.setEditable(true);
        zoomCombo.setPrototypeDisplayValue("100%");
        zoomCombo.setPreferredSize(new Dimension(76, 28));
        zoomCombo.setToolTipText("PDF zoom percentage");
        zoomOutButton.addActionListener(event -> changeZoom(-ZOOM_STEP, null));
        zoomInButton.addActionListener(event -> changeZoom(ZOOM_STEP, null));
        fitWidthButton.addActionListener(event -> fitWidth());
        fitPageButton.addActionListener(event -> fitPage());
        copyPageButton.addActionListener(event -> copyCurrentPageText());
        copyAllButton.addActionListener(event -> copyAllText());
        zoomCombo.addActionListener(event -> applyZoomComboSelection());
        zoomControlsPanel.add(copyPageButton);
        zoomControlsPanel.add(copyAllButton);
        zoomControlsPanel.add(zoomOutButton);
        zoomControlsPanel.add(zoomCombo);
        zoomControlsPanel.add(zoomInButton);
        zoomControlsPanel.add(fitWidthButton);
        zoomControlsPanel.add(fitPageButton);

        toolbarPanel.add(navigationPanel, BorderLayout.WEST);
        toolbarPanel.add(zoomControlsPanel, BorderLayout.EAST);
        return toolbarPanel;
    }

    private void styleToolbarPanel(JPanel panel, UiTheme theme) {
        if (panel != null) {
            panel.setBackground(theme.panelBackground());
            panel.setForeground(theme.text());
        }
    }

    private void styleButton(JButton button, UiTheme theme) {
        UiButtons.style(button, theme, new java.awt.Insets(4, 10, 4, 10));
    }

    private void styleCombo(JComboBox<?> comboBox) {
        comboBox.setBackground(theme.raisedBackground());
        comboBox.setForeground(theme.text());
    }

    private JScrollPane createScrollPane() {
        scrollPane = new JScrollPane(documentCanvas);
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getVerticalScrollBar().setBlockIncrement(SCROLL_UNIT_INCREMENT * 6);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(SCROLL_UNIT_INCREMENT * 6);
        scrollPane.setWheelScrollingEnabled(true);
        scrollPane.setToolTipText("Drag to select PDF text. Ctrl+C copies the selection or visible page. Use Ctrl + mouse wheel to zoom.");
        scrollPane.addMouseWheelListener(this::handleMouseWheel);
        scrollPane.getViewport().addChangeListener(event -> updateControls());
        documentCanvas.addMouseWheelListener(this::handleMouseWheel);
        installCopyShortcuts();
        return scrollPane;
    }

    private void installSourceNavigation() {
        documentCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        documentCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                documentCanvas.requestFocusInWindow();
                showContextMenuIfNeeded(event);
                if (event.getButton() == MouseEvent.BUTTON1 && documentCanvas.pageHitAt(event.getPoint()) != null) {
                    suppressNextSourceClick = false;
                    documentCanvas.beginSelection(event.getPoint());
                } else if (event.getButton() == MouseEvent.BUTTON1) {
                    documentCanvas.clearSelection();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showContextMenuIfNeeded(event);
                if (event.getButton() == MouseEvent.BUTTON1 && documentCanvas.isSelecting()) {
                    boolean selectedText = documentCanvas.finishSelection();
                    suppressNextSourceClick = selectedText;
                    if (selectedText) {
                        statusLabel.setText("Selection ready. Press Ctrl+C to copy.");
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (suppressNextSourceClick) {
                    suppressNextSourceClick = false;
                    return;
                }
                if (event.getButton() == MouseEvent.BUTTON1) {
                    navigateToSource(event.getPoint());
                }
            }
        });
        documentCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (documentCanvas.isSelecting()) {
                    documentCanvas.updateSelection(event.getPoint());
                }
            }
        });
    }

    private void installCopyShortcuts() {
        documentCanvas.getInputMap(JComponent.WHEN_FOCUSED).put(
            javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
            "copy-page-text"
        );
        documentCanvas.getActionMap().put("copy-page-text", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                copySelectionOrCurrentPageText();
            }
        });

        documentCanvas.getInputMap(JComponent.WHEN_FOCUSED).put(
            javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "copy-all-text"
        );
        documentCanvas.getActionMap().put("copy-all-text", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                copyAllText();
            }
        });
    }

    private void showContextMenuIfNeeded(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }

        boolean hasPdf = currentPdf != null && pageCount > 0;
        boolean hasSelection = documentCanvas.hasTextSelection();
        JMenuItem copySelection = new JMenuItem("Copy Selection Text");
        JMenuItem copyPage = new JMenuItem("Copy Page Text");
        JMenuItem copyAll = new JMenuItem("Copy All Text");
        copySelection.setEnabled(hasPdf && hasSelection && textCopyWorker == null);
        copyPage.setEnabled(hasPdf && textCopyWorker == null);
        copyAll.setEnabled(hasPdf && textCopyWorker == null);
        copySelection.addActionListener(action -> copySelectedText());
        copyPage.addActionListener(action -> copyCurrentPageText());
        copyAll.addActionListener(action -> copyAllText());

        JPopupMenu menu = new JPopupMenu();
        menu.add(copySelection);
        menu.add(copyPage);
        menu.add(copyAll);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private void scrollToAdjacentPage(int direction) {
        if (scrollPane == null || pageCount == 0) {
            return;
        }

        int currentPage = documentCanvas.pageAtY(scrollPane.getViewport().getViewPosition().y);
        int targetPage = Math.max(0, Math.min(pageCount - 1, currentPage + direction));
        scrollToPage(targetPage);
    }

    private void scrollToPage(int pageIndex) {
        if (scrollPane == null) {
            return;
        }

        JViewport viewport = scrollPane.getViewport();
        Dimension viewSize = documentCanvas.getPreferredSize();
        int y = Math.max(0, Math.min(documentCanvas.pageTop(pageIndex), Math.max(0, viewSize.height - viewport.getHeight())));
        viewport.setViewPosition(new Point(viewport.getViewPosition().x, y));
        updateControls();
    }

    private void navigateToSource(Point point) {
        if (sourceNavigationHandler == null || currentPdf == null || pageCount == 0) {
            return;
        }

        PageHit hit = documentCanvas.pageHitAt(point);
        if (hit == null) {
            return;
        }

        sourceNavigationHandler.navigate(currentPdf, hit.pageIndex() + 1, hit.normalizedX(), hit.normalizedY());
    }

    private void copyCurrentPageText() {
        if (currentPdf == null || pageCount == 0) {
            return;
        }

        int page = visiblePageIndex() + 1;
        copyPdfText(page, page, "page " + page);
    }

    private void copySelectionOrCurrentPageText() {
        if (documentCanvas.hasTextSelection()) {
            copySelectedText();
        } else {
            copyCurrentPageText();
        }
    }

    private void copySelectedText() {
        if (currentPdf == null || pageCount == 0 || !documentCanvas.hasTextSelection()) {
            return;
        }

        List<PageSelection> selections = documentCanvas.pageSelections();
        if (selections.isEmpty()) {
            statusLabel.setText("No PDF text selected");
            return;
        }

        copySelectedPdfText(selections);
    }

    private void copyAllText() {
        if (currentPdf == null || pageCount == 0) {
            return;
        }

        copyPdfText(1, pageCount, "document");
    }

    private void copyPdfText(int startPage, int endPage, String label) {
        if (currentPdf == null || textCopyWorker != null) {
            return;
        }

        Path pdfToCopy = currentPdf;
        int firstPage = Math.max(1, Math.min(startPage, pageCount));
        int lastPage = Math.max(firstPage, Math.min(endPage, pageCount));

        textCopyWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return extractPdfText(pdfToCopy, firstPage, lastPage);
            }

            @Override
            protected void done() {
                String message = "Could not copy PDF text";
                try {
                    String text = get();
                    if (text == null || text.isBlank()) {
                        message = "No selectable text found";
                        return;
                    }

                    Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                    message = "Copied " + label + " text";
                } catch (Exception error) {
                    message = "Could not copy PDF text";
                } finally {
                    textCopyWorker = null;
                    updateControls();
                    statusLabel.setText(message);
                }
            }
        };

        updateControls();
        statusLabel.setText("Copying " + label + " text...");
        textCopyWorker.execute();
    }

    private String extractPdfText(Path pdfToCopy, int startPage, int endPage) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(pdfToCopy.toString()))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            return stripper.getText(document);
        }
    }

    private void copySelectedPdfText(List<PageSelection> selections) {
        if (currentPdf == null || textCopyWorker != null) {
            return;
        }

        Path pdfToCopy = currentPdf;
        textCopyWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return extractSelectedPdfText(pdfToCopy, selections);
            }

            @Override
            protected void done() {
                String message = "Could not copy selected PDF text";
                try {
                    String text = get();
                    if (text == null || text.isBlank()) {
                        message = "No selectable text found in selection";
                        return;
                    }

                    Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                    message = "Copied selected PDF text";
                } catch (Exception error) {
                    message = "Could not copy selected PDF text";
                } finally {
                    textCopyWorker = null;
                    updateControls();
                    statusLabel.setText(message);
                }
            }
        };

        updateControls();
        statusLabel.setText("Copying selected text...");
        textCopyWorker.execute();
    }

    private String extractSelectedPdfText(Path pdfToCopy, List<PageSelection> selections) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfToCopy.toString()))) {
            StringBuilder selectedText = new StringBuilder();
            for (PageSelection selection : selections) {
                if (selection.pageIndex() < 0 || selection.pageIndex() >= document.getNumberOfPages()) {
                    continue;
                }

                PDPage page = document.getPage(selection.pageIndex());
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);
                stripper.addRegion("selection", selection.toPdfRectangle(page.getCropBox()));
                stripper.extractRegions(page);
                String text = stripper.getTextForRegion("selection").trim();
                if (text.isBlank()) {
                    continue;
                }

                if (selectedText.length() > 0) {
                    selectedText.append(System.lineSeparator());
                }
                selectedText.append(text);
            }
            return selectedText.toString();
        }
    }

    private void changeZoom(float amount, Point anchorPoint) {
        setZoom(zoom + amount, anchorPoint);
    }

    private void setZoom(float requestedZoom, Point anchorPoint) {
        if (currentPdf == null) {
            return;
        }

        float oldZoom = zoom;
        float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
        if (Float.compare(oldZoom, newZoom) == 0) {
            return;
        }

        renderRequestId++;
        cancelRender();
        zoom = newZoom;

        ZoomAnchor zoomAnchor = createZoomAnchor(anchorPoint, oldZoom, newZoom);
        if (documentCanvas.hasPages()) {
            documentCanvas.setZoom(zoom);
            restoreZoomAnchor(zoomAnchor);
            renderDebounceTimer.restart();
        } else {
            renderDocument(true);
        }
        updateControls();
    }

    private void fitWidth() {
        fitToViewport(true, false);
    }

    private void fitPage() {
        fitToViewport(true, true);
    }

    private void fitToViewport(boolean fitWidth, boolean fitHeight) {
        if (scrollPane == null || currentPdf == null || !documentCanvas.hasPages()) {
            return;
        }

        int pageIndex = documentCanvas.pageAtY(scrollPane.getViewport().getViewPosition().y);
        Dimension rawPage = documentCanvas.rawPageSize(pageIndex);
        if (rawPage.width <= 0 || rawPage.height <= 0) {
            return;
        }

        JViewport viewport = scrollPane.getViewport();
        double widthZoom = (Math.max(120, viewport.getWidth() - 64) * documentCanvas.renderDpi())
            / (DISPLAY_DPI * rawPage.width);
        double heightZoom = (Math.max(120, viewport.getHeight() - 64) * documentCanvas.renderDpi())
            / (DISPLAY_DPI * rawPage.height);
        double target = fitHeight ? Math.min(widthZoom, heightZoom) : widthZoom;
        setZoom((float) target, new Point(
            viewport.getViewPosition().x + viewport.getWidth() / 2,
            viewport.getViewPosition().y + viewport.getHeight() / 2
        ));
    }

    private void applyZoomComboSelection() {
        if (updatingZoomCombo || currentPdf == null) {
            return;
        }

        Object selected = zoomCombo.getSelectedItem();
        if (selected == null) {
            return;
        }

        String text = selected.toString().trim().replace("%", "");
        try {
            float requestedZoom = Float.parseFloat(text) / 100f;
            setZoom(requestedZoom, null);
        } catch (NumberFormatException ignored) {
            updateZoomCombo();
        }
    }

    private void renderDocument(boolean showMessage) {
        renderDebounceTimer.stop();
        cancelRender();
        if (showMessage || !documentCanvas.hasPages()) {
            documentCanvas.showMessage("Rendering PDF...");
            statusLabel.setText("Rendering...");
        } else {
            statusLabel.setText("Sharpening preview...");
        }
        updateControls();

        Path pdfToRender = currentPdf;
        float requestedZoom = zoom;
        int requestId = ++renderRequestId;

        // Render all pages so the preview behaves like one connected, scrollable document.
        renderWorker = new SwingWorker<>() {
            @Override
            protected RenderedDocument doInBackground() throws Exception {
                return renderPdfDocument(pdfToRender, requestedZoom);
            }

            @Override
            protected void done() {
                if (isCancelled() || requestId != renderRequestId) {
                    return;
                }

                try {
                    RenderedDocument document = get();
                    pageCount = document.pageCount();
                    documentCanvas.setDocument(document.pages(), zoom, document.renderDpi());
                } catch (Exception error) {
                    documentCanvas.clear("Could not render PDF: " + error.getMessage());
                    pageCount = 0;
                } finally {
                    renderWorker = null;
                    updateControls();
                }
            }
        };

        renderWorker.execute();
    }

    private RenderedDocument renderPdfDocument(Path pdfToRender, float requestedZoom) throws Exception {
        float renderDpi = renderDpiForZoom(requestedZoom);
        try (PDDocument document = Loader.loadPDF(new File(pdfToRender.toString()))) {
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                throw new IOException("PDF has no pages.");
            }

            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> renderedPages = new ArrayList<>(pages);
            for (int page = 0; page < pages; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, renderDpi, ImageType.RGB);
                renderedPages.add(image);
            }

            return new RenderedDocument(renderedPages, pages, renderDpi);
        }
    }

    private float renderDpiForZoom(float requestedZoom) {
        return Math.max(MIN_RENDER_DPI, Math.min(MAX_RENDER_DPI, DISPLAY_DPI * requestedZoom * 1.35f));
    }

    private void cancelRender() {
        if (renderWorker != null && !renderWorker.isDone()) {
            // Do not interrupt PDFBox while it is reading fonts or embedded PDF streams.
            // Older renders are ignored through renderRequestId when they finish.
            renderWorker.cancel(false);
        }
    }

    private void handleMouseWheel(MouseWheelEvent event) {
        if (event.isControlDown()) {
            event.consume();
            float amount = event.getWheelRotation() < 0 ? ZOOM_STEP : -ZOOM_STEP;
            changeZoom(amount, canvasPoint(event));
            return;
        }

        scrollByMouseWheel(event);
    }

    private void scrollByMouseWheel(MouseWheelEvent event) {
        if (scrollPane == null) {
            return;
        }

        event.consume();
        JScrollBar scrollBar = event.isShiftDown()
            ? scrollPane.getHorizontalScrollBar()
            : scrollPane.getVerticalScrollBar();
        int amount = (int) Math.round(event.getPreciseWheelRotation() * WHEEL_SCROLL_PIXELS);
        if (amount == 0) {
            amount = event.getWheelRotation() < 0 ? -SCROLL_UNIT_INCREMENT : SCROLL_UNIT_INCREMENT;
        }
        scrollBar.setValue(scrollBar.getValue() + amount);
    }

    private Point canvasPoint(MouseWheelEvent event) {
        if (event.getComponent() == documentCanvas) {
            return event.getPoint();
        }
        return SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), documentCanvas);
    }

    private ZoomAnchor createZoomAnchor(Point anchorPoint, float oldZoom, float newZoom) {
        if (scrollPane == null) {
            return null;
        }

        JViewport viewport = scrollPane.getViewport();
        Point viewPosition = viewport.getViewPosition();
        Point viewPoint = anchorPoint == null
            ? new Point(viewPosition.x + viewport.getWidth() / 2, viewPosition.y + viewport.getHeight() / 2)
            : anchorPoint;
        Point viewportOffset = new Point(viewPoint.x - viewPosition.x, viewPoint.y - viewPosition.y);
        return new ZoomAnchor(viewPoint, viewportOffset, oldZoom, newZoom);
    }

    private void restoreZoomAnchor(ZoomAnchor anchor) {
        if (anchor == null || scrollPane == null) {
            return;
        }

        double ratio = anchor.newZoom() / anchor.oldZoom();
        JViewport viewport = scrollPane.getViewport();
        Dimension viewSize = documentCanvas.getPreferredSize();
        Rectangle extent = viewport.getViewRect();
        int x = (int) Math.round(anchor.viewPoint().x * ratio - anchor.viewportOffset().x);
        int y = (int) Math.round(anchor.viewPoint().y * ratio - anchor.viewportOffset().y);
        x = Math.max(0, Math.min(x, Math.max(0, viewSize.width - extent.width)));
        y = Math.max(0, Math.min(y, Math.max(0, viewSize.height - extent.height)));
        viewport.setViewPosition(new Point(x, y));
    }

    private void updateControls() {
        boolean hasPdf = currentPdf != null && pageCount > 0;
        int visiblePage = visiblePageIndex();
        boolean canCopy = hasPdf && textCopyWorker == null;

        previousButton.setEnabled(hasPdf && visiblePage > 0);
        nextButton.setEnabled(hasPdf && visiblePage < pageCount - 1);
        zoomOutButton.setEnabled(hasPdf && zoom > MIN_ZOOM);
        zoomInButton.setEnabled(hasPdf && zoom < MAX_ZOOM);
        fitWidthButton.setEnabled(hasPdf);
        fitPageButton.setEnabled(hasPdf);
        copyPageButton.setEnabled(canCopy);
        copyAllButton.setEnabled(canCopy);
        zoomCombo.setEnabled(hasPdf);
        updateZoomCombo();

        if (hasPdf) {
            statusLabel.setText("Page " + (visiblePage + 1) + " of " + pageCount + "  Zoom " + Math.round(zoom * 100) + "%");
        }
    }

    private int visiblePageIndex() {
        if (currentPdf == null || pageCount == 0 || scrollPane == null) {
            return 0;
        }

        return documentCanvas.pageAtY(scrollPane.getViewport().getViewPosition().y);
    }

    private void updateZoomCombo() {
        updatingZoomCombo = true;
        try {
            zoomCombo.setSelectedItem(Math.round(zoom * 100) + "%");
        } finally {
            updatingZoomCombo = false;
        }
    }

    public interface SourceNavigationHandler {
        void navigate(Path pdfFile, int pageNumber, double normalizedX, double normalizedY);
    }

    private record RenderedDocument(List<BufferedImage> pages, int pageCount, float renderDpi) {
    }

    private record ZoomAnchor(Point viewPoint, Point viewportOffset, float oldZoom, float newZoom) {
    }

    private record PageHit(int pageIndex, double normalizedX, double normalizedY) {
    }

    private record PageSelection(
        int pageIndex,
        double normalizedX,
        double normalizedY,
        double normalizedWidth,
        double normalizedHeight
    ) {
        private Rectangle2D toPdfRectangle(PDRectangle pageBounds) {
            double width = pageBounds.getWidth();
            double height = pageBounds.getHeight();
            return new Rectangle2D.Double(
                normalizedX * width,
                normalizedY * height,
                Math.max(1d, normalizedWidth * width),
                Math.max(1d, normalizedHeight * height)
            );
        }
    }

    private static final class DocumentCanvas extends JComponent {
        private static final int PAGE_MARGIN = 24;
        private static final int PAGE_GAP = 18;
        private static final int MIN_SELECTION_PIXELS = 4;
        private static final Color SELECTION_FILL = new Color(0, 120, 215, 74);
        private static final Color SELECTION_BORDER = new Color(0, 95, 180, 160);

        private List<BufferedImage> pages = List.of();
        private UiTheme theme = UiTheme.light();
        private float zoom = 1.0f;
        private float renderDpi = MIN_RENDER_DPI;
        private String message = "Compile a document to preview the PDF here.";
        private Point selectionStart;
        private Point selectionEnd;
        private boolean selecting;

        private DocumentCanvas() {
            setOpaque(true);
            setFocusable(true);
            setBackground(new Color(238, 238, 238));
            setFont(previewFont());
        }

        private void applyTheme(UiTheme theme) {
            this.theme = theme;
            setBackground(theme.pdfBackground());
            setForeground(theme.messageText());
            repaint();
        }

        private void setDocument(List<BufferedImage> pages, float zoom, float renderDpi) {
            this.pages = List.copyOf(pages);
            this.zoom = zoom;
            this.renderDpi = renderDpi;
            this.message = null;
            clearSelection();
            revalidate();
            repaint();
        }

        private void setZoom(float zoom) {
            this.zoom = zoom;
            clearSelection();
            revalidate();
            repaint();
        }

        private boolean hasPages() {
            return !pages.isEmpty();
        }

        private float renderDpi() {
            return renderDpi;
        }

        private Dimension rawPageSize(int pageIndex) {
            if (pages.isEmpty()) {
                return new Dimension(0, 0);
            }

            BufferedImage page = pages.get(Math.max(0, Math.min(pageIndex, pages.size() - 1)));
            return new Dimension(page.getWidth(), page.getHeight());
        }

        private void showMessage(String message) {
            this.message = message;
            repaint();
        }

        private void clear(String message) {
            this.pages = List.of();
            this.message = message;
            clearSelection();
            revalidate();
            repaint();
        }

        private void beginSelection(Point point) {
            this.selectionStart = new Point(point);
            this.selectionEnd = new Point(point);
            this.selecting = true;
            repaint();
        }

        private void updateSelection(Point point) {
            if (!selecting || selectionStart == null) {
                return;
            }

            this.selectionEnd = new Point(point);
            repaint();
        }

        private boolean finishSelection() {
            selecting = false;
            if (!hasTextSelection()) {
                clearSelection();
                return false;
            }

            repaint();
            return true;
        }

        private boolean isSelecting() {
            return selecting;
        }

        private boolean hasTextSelection() {
            Rectangle selection = selectionBounds();
            return selection != null
                && selection.width >= MIN_SELECTION_PIXELS
                && selection.height >= MIN_SELECTION_PIXELS
                && !pageSelections().isEmpty();
        }

        private void clearSelection() {
            selectionStart = null;
            selectionEnd = null;
            selecting = false;
            repaint();
        }

        private List<PageSelection> pageSelections() {
            Rectangle selection = selectionBounds();
            if (selection == null || pages.isEmpty()) {
                return List.of();
            }

            List<PageSelection> selectedPages = new ArrayList<>();
            int y = PAGE_MARGIN;
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                Dimension page = displaySize(pages.get(pageIndex));
                int x = Math.max(PAGE_MARGIN, (getWidth() - page.width) / 2);
                Rectangle pageBounds = new Rectangle(x, y, page.width, page.height);
                Rectangle selectedArea = selection.intersection(pageBounds);
                if (selectedArea.width >= MIN_SELECTION_PIXELS && selectedArea.height >= MIN_SELECTION_PIXELS) {
                    selectedPages.add(new PageSelection(
                        pageIndex,
                        (selectedArea.x - pageBounds.x) / (double) pageBounds.width,
                        (selectedArea.y - pageBounds.y) / (double) pageBounds.height,
                        selectedArea.width / (double) pageBounds.width,
                        selectedArea.height / (double) pageBounds.height
                    ));
                }

                y += page.height + PAGE_GAP;
            }

            return selectedPages;
        }

        private Rectangle selectionBounds() {
            if (selectionStart == null || selectionEnd == null) {
                return null;
            }

            int x = Math.min(selectionStart.x, selectionEnd.x);
            int y = Math.min(selectionStart.y, selectionEnd.y);
            int width = Math.abs(selectionStart.x - selectionEnd.x);
            int height = Math.abs(selectionStart.y - selectionEnd.y);
            return new Rectangle(x, y, width, height);
        }

        @Override
        public Dimension getPreferredSize() {
            if (pages.isEmpty()) {
                return new Dimension(520, 680);
            }

            int width = 0;
            int height = PAGE_MARGIN;
            for (int page = 0; page < pages.size(); page++) {
                Dimension size = displaySize(pages.get(page));
                width = Math.max(width, size.width);
                height += size.height;
                if (page < pages.size() - 1) {
                    height += PAGE_GAP;
                }
            }
            height += PAGE_MARGIN;
            return new Dimension(width + PAGE_MARGIN * 2, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                Rectangle clip = graphics.getClipBounds();
                graphics2D.setColor(getBackground());
                graphics2D.fillRect(clip.x, clip.y, clip.width, clip.height);
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (pages.isEmpty()) {
                    drawMessage(graphics2D);
                    return;
                }

                int y = PAGE_MARGIN;
                for (BufferedImage pageImage : pages) {
                    Dimension page = displaySize(pageImage);
                    int x = Math.max(PAGE_MARGIN, (getWidth() - page.width) / 2);

                    if (y + page.height >= clip.y && y <= clip.y + clip.height) {
                        paintPage(graphics2D, pageImage, x, y, page);
                        paintSelection(graphics2D, new Rectangle(x, y, page.width, page.height));
                    }

                    y += page.height + PAGE_GAP;
                }
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintPage(Graphics2D graphics, BufferedImage image, int x, int y, Dimension page) {
            graphics.setColor(theme.pageShadow());
            graphics.fillRect(x + 4, y + 5, page.width, page.height);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(x, y, page.width, page.height);
            graphics.drawImage(image, x, y, page.width, page.height, null);
        }

        private void paintSelection(Graphics2D graphics, Rectangle pageBounds) {
            Rectangle selection = selectionBounds();
            if (selection == null) {
                return;
            }

            Rectangle selectedArea = selection.intersection(pageBounds);
            if (selectedArea.width < MIN_SELECTION_PIXELS || selectedArea.height < MIN_SELECTION_PIXELS) {
                return;
            }

            graphics.setColor(SELECTION_FILL);
            graphics.fill(selectedArea);
            graphics.setColor(SELECTION_BORDER);
            graphics.draw(selectedArea);
        }

        private int pageTop(int pageIndex) {
            int safePage = Math.max(0, Math.min(pageIndex, pages.size() - 1));
            int y = PAGE_MARGIN;
            for (int page = 0; page < safePage; page++) {
                y += displaySize(pages.get(page)).height + PAGE_GAP;
            }
            return y;
        }

        private int pageAtY(int yPosition) {
            if (pages.isEmpty()) {
                return 0;
            }

            int y = PAGE_MARGIN;
            for (int page = 0; page < pages.size(); page++) {
                int bottom = y + displaySize(pages.get(page)).height;
                if (yPosition < bottom + PAGE_GAP / 2) {
                    return page;
                }
                y = bottom + PAGE_GAP;
            }
            return pages.size() - 1;
        }

        private PageHit pageHitAt(Point point) {
            if (pages.isEmpty()) {
                return null;
            }

            int y = PAGE_MARGIN;
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                Dimension page = displaySize(pages.get(pageIndex));
                int x = Math.max(PAGE_MARGIN, (getWidth() - page.width) / 2);
                Rectangle pageBounds = new Rectangle(x, y, page.width, page.height);
                if (pageBounds.contains(point)) {
                    double normalizedX = (point.x - pageBounds.x) / (double) pageBounds.width;
                    double normalizedY = (point.y - pageBounds.y) / (double) pageBounds.height;
                    return new PageHit(pageIndex, normalizedX, normalizedY);
                }

                y += page.height + PAGE_GAP;
            }

            return null;
        }

        private Dimension displaySize(BufferedImage pageImage) {
            double scale = DISPLAY_DPI * zoom / renderDpi;
            int width = Math.max(1, (int) Math.round(pageImage.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(pageImage.getHeight() * scale));
            return new Dimension(width, height);
        }

        private void drawMessage(Graphics2D graphics) {
            if (message == null || message.isBlank()) {
                return;
            }

            graphics.setColor(theme.messageText());
            graphics.setFont(getFont());
            FontMetrics metrics = graphics.getFontMetrics();
            int x = Math.max(16, (getWidth() - metrics.stringWidth(message)) / 2);
            int y = Math.max(28, getHeight() / 2);
            graphics.drawString(message, x, y);
        }

        private Font previewFont() {
            Font font = UIManager.getFont("Label.font");
            if (font == null) {
                font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
            }
            return font.deriveFont(Font.PLAIN, 14f);
        }
    }
}
