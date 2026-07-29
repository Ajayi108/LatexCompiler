package latexcompiler.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfPreviewPanel extends JPanel {
    private static final float DISPLAY_DPI = 120f;
    private static final float MIN_RENDER_DPI = 220f;
    private static final float MAX_RENDER_DPI = 320f;
    private static final float MIN_ZOOM = 0.40f;
    private static final float MAX_ZOOM = 3.00f;
    private static final float ZOOM_STEP = 0.10f;
    private static final int SCROLL_UNIT_INCREMENT = 42;
    private static final int ZOOM_RENDER_DELAY_MS = 180;

    private final DocumentCanvas documentCanvas;
    private final JLabel statusLabel;
    private final JButton previousButton;
    private final JButton nextButton;
    private final JButton zoomOutButton;
    private final JButton zoomInButton;
    private final Timer renderDebounceTimer;
    private JScrollPane scrollPane;

    private Path currentPdf;
    private int pageCount;
    private float zoom;
    private int renderRequestId;
    private SourceNavigationHandler sourceNavigationHandler;
    private SwingWorker<RenderedDocument, Void> renderWorker;

    public PdfPreviewPanel() {
        super(new BorderLayout());
        this.documentCanvas = new DocumentCanvas();
        this.statusLabel = new JLabel("No PDF");
        this.previousButton = new JButton("Previous");
        this.nextButton = new JButton("Next");
        this.zoomOutButton = new JButton("-");
        this.zoomInButton = new JButton("+");
        this.zoom = 1.0f;
        this.renderDebounceTimer = new Timer(ZOOM_RENDER_DELAY_MS, event -> renderDocument(false));
        this.renderDebounceTimer.setRepeats(false);

        setBorder(BorderFactory.createTitledBorder("PDF Preview"));
        add(createToolbar(), BorderLayout.NORTH);
        add(createScrollPane(), BorderLayout.CENTER);
        installSourceNavigation();
        updateControls();
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
        JPanel panel = new JPanel(new BorderLayout());

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        previousButton.addActionListener(event -> scrollToAdjacentPage(-1));
        nextButton.addActionListener(event -> scrollToAdjacentPage(1));
        navigation.add(previousButton);
        navigation.add(nextButton);
        navigation.add(statusLabel);

        JPanel zoomControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        zoomOutButton.setPreferredSize(new Dimension(44, 28));
        zoomInButton.setPreferredSize(new Dimension(44, 28));
        zoomOutButton.setToolTipText("Zoom out");
        zoomInButton.setToolTipText("Zoom in");
        zoomOutButton.addActionListener(event -> changeZoom(-ZOOM_STEP, null));
        zoomInButton.addActionListener(event -> changeZoom(ZOOM_STEP, null));
        zoomControls.add(zoomOutButton);
        zoomControls.add(zoomInButton);

        panel.add(navigation, BorderLayout.WEST);
        panel.add(zoomControls, BorderLayout.EAST);
        return panel;
    }

    private JScrollPane createScrollPane() {
        scrollPane = new JScrollPane(documentCanvas);
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getVerticalScrollBar().setBlockIncrement(SCROLL_UNIT_INCREMENT * 6);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(SCROLL_UNIT_INCREMENT * 6);
        scrollPane.setWheelScrollingEnabled(true);
        scrollPane.setToolTipText("Click PDF text to jump to LaTeX. Use Ctrl + mouse wheel to zoom.");
        scrollPane.addMouseWheelListener(this::handleMouseWheel);
        scrollPane.getViewport().addChangeListener(event -> updateControls());
        documentCanvas.addMouseWheelListener(this::handleMouseWheel);
        return scrollPane;
    }

    private void installSourceNavigation() {
        documentCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        documentCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    navigateToSource(event.getPoint());
                }
            }
        });
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

    private void changeZoom(float amount, Point anchorPoint) {
        if (currentPdf == null) {
            return;
        }

        float oldZoom = zoom;
        float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + amount));
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RenderedDocument renderPdfDocument(Path pdfToRender, float requestedZoom) throws Exception {
        float renderDpi = renderDpiForZoom(requestedZoom);
        try {
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Class<?> documentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> rendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer");
            Class<?> imageTypeClass = Class.forName("org.apache.pdfbox.rendering.ImageType");

            Object document = loaderClass
                .getMethod("loadPDF", File.class)
                .invoke(null, new File(pdfToRender.toString()));

            try {
                int pages = (int) documentClass.getMethod("getNumberOfPages").invoke(document);
                if (pages == 0) {
                    throw new IOException("PDF has no pages.");
                }

                Object renderer = rendererClass.getConstructor(documentClass).newInstance(document);
                Object rgb = Enum.valueOf((Class<Enum>) imageTypeClass.asSubclass(Enum.class), "RGB");
                Method renderMethod = rendererClass.getMethod("renderImageWithDPI", int.class, float.class, imageTypeClass);
                List<BufferedImage> renderedPages = new ArrayList<>(pages);

                for (int page = 0; page < pages; page++) {
                    BufferedImage image = (BufferedImage) renderMethod.invoke(renderer, page, renderDpi, rgb);
                    renderedPages.add(image);
                }

                return new RenderedDocument(renderedPages, pages, renderDpi);
            } finally {
                documentClass.getMethod("close").invoke(document);
            }
        } catch (ClassNotFoundException error) {
            throw new IOException("PDFBox is missing from the app libraries.", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IOException("PDF rendering failed.", cause);
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
        int amount = (int) Math.round(event.getPreciseWheelRotation()
            * Math.max(1, event.getScrollAmount())
            * Math.max(SCROLL_UNIT_INCREMENT, scrollBar.getUnitIncrement()));
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
        int visiblePage = hasPdf && scrollPane != null
            ? documentCanvas.pageAtY(scrollPane.getViewport().getViewPosition().y)
            : 0;

        previousButton.setEnabled(hasPdf && visiblePage > 0);
        nextButton.setEnabled(hasPdf && visiblePage < pageCount - 1);
        zoomOutButton.setEnabled(hasPdf && zoom > MIN_ZOOM);
        zoomInButton.setEnabled(hasPdf && zoom < MAX_ZOOM);

        if (hasPdf) {
            statusLabel.setText("Page " + (visiblePage + 1) + " of " + pageCount + "  Zoom " + Math.round(zoom * 100) + "%");
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

    private static final class DocumentCanvas extends JComponent {
        private static final int PAGE_MARGIN = 24;
        private static final int PAGE_GAP = 18;

        private List<BufferedImage> pages = List.of();
        private float zoom = 1.0f;
        private float renderDpi = MIN_RENDER_DPI;
        private String message = "Compile a document to preview the PDF here.";

        private DocumentCanvas() {
            setOpaque(true);
            setBackground(new Color(238, 238, 238));
            setFont(previewFont());
        }

        private void setDocument(List<BufferedImage> pages, float zoom, float renderDpi) {
            this.pages = List.copyOf(pages);
            this.zoom = zoom;
            this.renderDpi = renderDpi;
            this.message = null;
            revalidate();
            repaint();
        }

        private void setZoom(float zoom) {
            this.zoom = zoom;
            revalidate();
            repaint();
        }

        private boolean hasPages() {
            return !pages.isEmpty();
        }

        private void showMessage(String message) {
            this.message = message;
            repaint();
        }

        private void clear(String message) {
            this.pages = List.of();
            this.message = message;
            revalidate();
            repaint();
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
                    }

                    y += page.height + PAGE_GAP;
                }
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintPage(Graphics2D graphics, BufferedImage image, int x, int y, Dimension page) {
            graphics.setColor(new Color(0, 0, 0, 34));
            graphics.fillRect(x + 4, y + 5, page.width, page.height);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(x, y, page.width, page.height);
            graphics.drawImage(image, x, y, page.width, page.height, null);
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

            graphics.setColor(new Color(95, 95, 95));
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
