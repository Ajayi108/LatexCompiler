package latexcompiler.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectFilesPanel extends JPanel {
    private static final int MAX_SCAN_DEPTH = 6;
    private static final int MAX_PROJECT_FILES = 1200;
    private static final int MAX_PROJECT_FOLDERS = 500;
    private static final String POPUP_MENU_CHANGE_LISTENER_KEY = "latex-compiler.popupMenuChangeListener";
    private static final DataFlavor PROJECT_PATH_FLAVOR = createProjectPathFlavor();
    private static final Pattern BRACED_COMMAND = Pattern.compile(
        "\\\\(includegraphics|input|include|lstinputlisting)\\s*(?:\\[[^\\]]*])?\\s*\\{([^}]+)}"
    );
    private static final Pattern BIBLIOGRAPHY_COMMAND = Pattern.compile("\\\\bibliography\\s*\\{([^}]+)}");
    private static final Pattern BIB_RESOURCE_COMMAND = Pattern.compile(
        "\\\\addbibresource\\s*(?:\\[[^\\]]*])?\\s*\\{([^}]+)}"
    );
    private static final Pattern GRAPHICS_PATH_ENTRY = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern OUTLINE_COMMAND = Pattern.compile(
        "\\\\(part|chapter|section|subsection|subsubsection|paragraph|subparagraph)\\*?\\s*(?:\\[[^\\]]*])?\\s*\\{([^{}]+)}"
    );
    private static final Pattern SIMPLE_BRACED_FORMAT_COMMAND = Pattern.compile("\\\\[a-zA-Z]+\\*?\\s*\\{([^{}]*)}");
    private static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".pdf", ".eps");
    private final DefaultTreeModel fileTreeModel = new DefaultTreeModel(
        new DefaultMutableTreeNode(ProjectFileItem.placeholder())
    );
    private final DefaultListModel<OutlineItem> outlineModel = new DefaultListModel<>();
    private final JTree fileTree = new JTree(fileTreeModel);
    private final JList<OutlineItem> outlineList = new JList<>(outlineModel);
    private final JLabel statusLabel = new JLabel("No project");
    private final JLabel outlineStatusLabel = new JLabel("No outline");
    private final JButton backButton = new JButton("Back");
    private final JButton newTexButton = new JButton("New TeX");
    private final JButton newFolderButton = new JButton("New Folder");
    private final JButton refreshButton = new JButton("Refresh");
    private final JComboBox<Path> mainFileSelector = new JComboBox<>();
    private final JCheckBox hideGeneratedFilesBox = new JCheckBox("Hide build files", true);
    private UiTheme theme = UiTheme.light();
    private JSplitPane fileOutlineSplit;
    private JPanel fileTreePanel;
    private JPanel outlinePanel;
    private JScrollPane fileScrollPane;
    private JScrollPane outlineScrollPane;
    private boolean outlineDividerInitialized;
    private Path projectRoot;
    private Path activeFile;
    private Path mainFile;
    private String activeSourceText = "";
    private String referenceSourceText = "";
    private FileActionHandler fileActionHandler;
    private final Map<Path, ProjectFileItem> knownFileItems = new HashMap<>();
    private TreePath hoveredTreePath;
    private int hoveredOutlineIndex = -1;
    private boolean hideGeneratedFiles = true;
    private boolean updatingMainFileSelector;

    public ProjectFilesPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createTitledBorder("Files"));
        setMinimumSize(new Dimension(190, 220));
        setPreferredSize(new Dimension(260, 100));

        fileTree.setCellRenderer(new ProjectTreeRenderer());
        fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        fileTree.setRootVisible(true);
        fileTree.setShowsRootHandles(false);
        fileTree.setToggleClickCount(0);
        fileTree.setRowHeight(28);
        fileTree.setDragEnabled(true);
        fileTree.setDropMode(javax.swing.DropMode.ON);
        fileTree.setTransferHandler(new FileTreeTransferHandler());
        fileTree.putClientProperty("JTree.lineStyle", "None");
        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showItemMenu(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showItemMenu(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    return;
                }

                TreePath path = treePathAt(event);
                if (event.getClickCount() == 1 && isChevronClick(path, event)) {
                    fileTree.setSelectionPath(path);
                    toggleTreeFolder(path);
                } else if (event.getClickCount() == 1 && path != null) {
                    fileTree.setSelectionPath(path);
                    openSingleClickedTreeFile(path);
                } else if (event.getClickCount() == 2) {
                    openSelectedTreeItem();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredTreePath = null;
                fileTree.setCursor(java.awt.Cursor.getDefaultCursor());
                fileTree.repaint();
            }
        });
        fileTree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                TreePath path = treePathAt(event);
                if ((path == null && hoveredTreePath != null) || (path != null && !path.equals(hoveredTreePath))) {
                    hoveredTreePath = path;
                    fileTree.repaint();
                }
                fileTree.setCursor(path == null ? java.awt.Cursor.getDefaultCursor() : java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            }
        });

        mainFileSelector.setRenderer(new MainFileRenderer());
        mainFileSelector.setToolTipText("Main .tex file compiled by the Compile button");
        mainFileSelector.addActionListener(event -> {
            if (updatingMainFileSelector || fileActionHandler == null) {
                return;
            }

            Path selected = (Path) mainFileSelector.getSelectedItem();
            if (selected != null && !samePath(selected, mainFile)) {
                fileActionHandler.setMainFile(selected);
            }
        });

        hideGeneratedFilesBox.setFocusable(false);
        hideGeneratedFilesBox.setToolTipText("Hide generated LaTeX build outputs such as .aux, .log, .toc, and SyncTeX files");
        hideGeneratedFilesBox.addActionListener(event -> {
            hideGeneratedFiles = hideGeneratedFilesBox.isSelected();
            if (fileActionHandler != null) {
                fileActionHandler.hideGeneratedFilesChanged(hideGeneratedFiles);
            }
            refresh(projectRoot, activeFile, mainFile, activeSourceText, referenceSourceText);
        });

        outlineList.setCellRenderer(new OutlineRenderer());
        outlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outlineList.setFixedCellHeight(28);
        outlineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1) {
                    openOutlineItemAt(event);
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredOutlineIndex = -1;
                outlineList.setCursor(java.awt.Cursor.getDefaultCursor());
                outlineList.repaint();
            }
        });
        outlineList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int index = outlineList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : outlineList.getCellBounds(index, index);
                int hoverIndex = bounds != null && bounds.contains(event.getPoint()) ? index : -1;
                if (hoverIndex != hoveredOutlineIndex) {
                    hoveredOutlineIndex = hoverIndex;
                    outlineList.repaint();
                }
                outlineList.setCursor(hoverIndex < 0 ? java.awt.Cursor.getDefaultCursor() : java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            }
        });

        add(createHeader(), BorderLayout.NORTH);
        add(createFileAndOutlineContent(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        applyTheme(theme);
        updateActionButtons();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::initializeOutlineDivider);
    }

    public void applyTheme(UiTheme theme) {
        this.theme = theme;
        setBackground(theme.panelBackground());
        setForeground(theme.text());
        setBorder(titleBorder("Files"));
        applyPanelTheme(this);
        applyPanelTheme(fileTreePanel);
        applyPanelTheme(outlinePanel);
        if (fileTreePanel != null) {
            fileTreePanel.setBorder(titleBorder("File tree"));
        }
        if (outlinePanel != null) {
            outlinePanel.setBorder(titleBorder("File outline"));
        }
        styleTree(fileTree);
        styleList(outlineList);
        styleScrollPane(fileScrollPane);
        styleScrollPane(outlineScrollPane);
        statusLabel.setForeground(theme.mutedText());
        outlineStatusLabel.setForeground(theme.mutedText());
        styleCombo(mainFileSelector);
        hideGeneratedFilesBox.setBackground(theme.panelBackground());
        hideGeneratedFilesBox.setForeground(theme.text());
        styleButtons(this);
        repaint();
    }

    public void setFileActionHandler(FileActionHandler fileActionHandler) {
        this.fileActionHandler = fileActionHandler;
    }

    public void setHideGeneratedFiles(boolean hideGeneratedFiles) {
        this.hideGeneratedFiles = hideGeneratedFiles;
        hideGeneratedFilesBox.setSelected(hideGeneratedFiles);
        refresh(projectRoot, activeFile, mainFile, activeSourceText, referenceSourceText);
    }

    public boolean hideGeneratedFiles() {
        return hideGeneratedFiles;
    }

    public void refresh(
        Path projectRoot,
        Path activeFile,
        Path mainFile,
        String activeSourceText,
        String referenceSourceText
    ) {
        this.projectRoot = normalizeProjectRoot(projectRoot, mainFile == null ? activeFile : mainFile);
        this.activeFile = activeFile == null ? null : activeFile.toAbsolutePath().normalize();
        this.mainFile = mainFile == null ? this.activeFile : mainFile.toAbsolutePath().normalize();
        this.referenceSourceText = referenceSourceText == null ? "" : referenceSourceText;
        this.activeSourceText = outlineSourceText(activeSourceText, this.referenceSourceText);
        knownFileItems.clear();
        refreshOutline();

        if (this.projectRoot == null) {
            fileTreeModel.setRoot(new DefaultMutableTreeNode(ProjectFileItem.placeholder()));
            refreshMainFileSelector(new ProjectScan(List.of(), 0, List.of()));
            statusLabel.setText("Open a folder or save the source");
            updateActionButtons();
            return;
        }

        try {
            ProjectScan scan = scanProject(this.projectRoot, this.activeFile, this.mainFile, this.referenceSourceText);
            rebuildFileTree(scan);
            refreshMainFileSelector(scan);
            statusLabel.setText(scan.usedCount() + " used / " + scan.items().size() + " files");
        } catch (IOException error) {
            fileTreeModel.setRoot(new DefaultMutableTreeNode(ProjectFileItem.root(this.projectRoot)));
            statusLabel.setText("Could not read project folder");
        } finally {
            updateActionButtons();
        }
    }

    public void clear() {
        projectRoot = null;
        activeFile = null;
        mainFile = null;
        activeSourceText = "";
        referenceSourceText = "";
        knownFileItems.clear();
        fileTreeModel.setRoot(new DefaultMutableTreeNode(ProjectFileItem.placeholder()));
        outlineModel.clear();
        refreshMainFileSelector(new ProjectScan(List.of(), 0, List.of()));
        statusLabel.setText("No project");
        outlineStatusLabel.setText("No outline");
        updateActionButtons();
    }

    private JSplitPane createFileAndOutlineContent() {
        fileTreePanel = new JPanel(new BorderLayout());
        fileTreePanel.setMinimumSize(new Dimension(180, 120));
        fileTreePanel.setPreferredSize(new Dimension(240, 380));
        fileTreePanel.setBorder(titleBorder("File tree"));
        fileScrollPane = new JScrollPane(fileTree);
        fileTreePanel.add(fileScrollPane, BorderLayout.CENTER);

        outlinePanel = new JPanel(new BorderLayout(0, 4));
        outlinePanel.setMinimumSize(new Dimension(180, 150));
        outlinePanel.setPreferredSize(new Dimension(240, 230));
        outlinePanel.setBorder(titleBorder("File outline"));
        outlineScrollPane = new JScrollPane(outlineList);
        outlinePanel.add(outlineScrollPane, BorderLayout.CENTER);
        outlinePanel.add(outlineStatusLabel, BorderLayout.SOUTH);

        fileOutlineSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileTreePanel, outlinePanel);
        fileOutlineSplit.setResizeWeight(0.62);
        fileOutlineSplit.setDividerSize(6);
        fileOutlineSplit.setBorder(BorderFactory.createEmptyBorder());
        fileOutlineSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                initializeOutlineDivider();
            }
        });
        return fileOutlineSplit;
    }

    private void initializeOutlineDivider() {
        if (outlineDividerInitialized || fileOutlineSplit == null || fileOutlineSplit.getHeight() <= 0) {
            return;
        }

        int height = fileOutlineSplit.getHeight();
        int outlineHeight = Math.max(170, Math.min(260, height / 3));
        int divider = Math.max(160, height - outlineHeight);
        fileOutlineSplit.setDividerLocation(divider);
        outlineDividerInitialized = true;
    }

    private static DataFlavor createProjectPathFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.nio.file.Path");
        } catch (ClassNotFoundException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private javax.swing.border.TitledBorder titleBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(theme.border()),
            title,
            javax.swing.border.TitledBorder.LEADING,
            javax.swing.border.TitledBorder.TOP,
            getFont(),
            theme.text()
        );
    }

    private void styleList(JList<?> list) {
        list.setBackground(theme.panelBackground());
        list.setForeground(theme.text());
        list.setSelectionBackground(theme.listSelectionBackground());
        list.setSelectionForeground(theme.listSelectionForeground());
    }

    private void styleTree(JTree tree) {
        tree.setBackground(theme.panelBackground());
        tree.setForeground(theme.text());
        tree.setOpaque(true);
        tree.setFont(tree.getFont().deriveFont(Font.PLAIN, 14f));
        tree.repaint();
    }

    private void styleCombo(JComboBox<?> comboBox) {
        comboBox.setBackground(theme.raisedBackground());
        comboBox.setForeground(theme.text());
        comboBox.setFocusable(false);
    }

    private void styleScrollPane(JScrollPane scrollPane) {
        if (scrollPane != null) {
            scrollPane.setBorder(BorderFactory.createLineBorder(theme.border()));
            scrollPane.getViewport().setBackground(theme.panelBackground());
        }
    }

    private void applyPanelTheme(Container container) {
        if (container == null) {
            return;
        }

        container.setBackground(theme.panelBackground());
        container.setForeground(theme.text());
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                applyPanelTheme(child);
            } else {
                component.setBackground(theme.panelBackground());
                component.setForeground(theme.text());
            }
        }
    }

    private void styleButtons(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) {
                UiButtons.style(button, theme, button.getMargin());
            }
            if (component instanceof Container child) {
                styleButtons(child);
            }
        }
    }

    private Color hoverBackground() {
        return blend(theme.panelBackground(), theme.text(), theme.darkMode() ? 0.10 : 0.06);
    }

    private Color blend(Color base, Color overlay, double amount) {
        double keep = 1.0 - amount;
        return new Color(
            clamp(base.getRed() * keep + overlay.getRed() * amount),
            clamp(base.getGreen() * keep + overlay.getGreen() * amount),
            clamp(base.getBlue() * keep + overlay.getBlue() * amount)
        );
    }

    private int clamp(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private String outlineSourceText(String activeSourceText, String referenceSourceText) {
        String activeText = activeSourceText == null ? "" : activeSourceText;
        if (!activeText.isBlank() || activeFile != null) {
            return activeText;
        }
        return referenceSourceText == null ? "" : referenceSourceText;
    }

    private void refreshOutline() {
        outlineModel.clear();
        Path outlineFile = activeFile == null ? mainFile : activeFile;
        List<OutlineItem> outline = parseOutline(activeSourceText, outlineFile);
        outline.forEach(outlineModel::addElement);

        if (outline.isEmpty()) {
            outlineStatusLabel.setText(activeSourceText.isBlank() ? "Open a .tex file" : "No headings");
        } else {
            outlineStatusLabel.setText(outline.size() + " headings");
        }
    }

    private void refreshMainFileSelector(ProjectScan scan) {
        updatingMainFileSelector = true;
        try {
            mainFileSelector.removeAllItems();
            for (Path texFile : scan.texFiles()) {
                mainFileSelector.addItem(texFile);
            }

            if (mainFileSelector.getItemCount() == 0) {
                mainFileSelector.setEnabled(false);
                return;
            }

            mainFileSelector.setEnabled(true);
            Path selected = mainFile == null ? activeFile : mainFile;
            for (int index = 0; index < mainFileSelector.getItemCount(); index++) {
                Path item = mainFileSelector.getItemAt(index);
                if (samePath(item, selected)) {
                    mainFileSelector.setSelectedIndex(index);
                    return;
                }
            }
            mainFileSelector.setSelectedIndex(0);
        } finally {
            updatingMainFileSelector = false;
        }
    }

    private void rebuildFileTree(ProjectScan scan) {
        Set<Path> expanded = expandedProjectPaths();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(ProjectFileItem.root(projectRoot));
        knownFileItems.clear();
        knownFileItems.put(projectRoot, ProjectFileItem.root(projectRoot));

        for (ProjectFileItem item : scan.items()) {
            if (!samePath(item.path(), projectRoot) && isSameOrInside(projectRoot, item.path())) {
                insertTreeItem(root, item);
                knownFileItems.put(item.path(), item);
            }
        }

        sortTreeChildren(root);
        fileTreeModel.setRoot(root);
        fileTreeModel.reload();
        restoreExpandedProjectPaths(root, expanded);
        selectCurrentTreeItem(root);
    }

    private void insertTreeItem(DefaultMutableTreeNode root, ProjectFileItem item) {
        Path relative = projectRoot.relativize(item.path());
        DefaultMutableTreeNode parent = root;
        Path cursor = projectRoot;

        for (int index = 0; index < relative.getNameCount(); index++) {
            cursor = cursor.resolve(relative.getName(index)).toAbsolutePath().normalize();
            boolean last = index == relative.getNameCount() - 1;
            DefaultMutableTreeNode child = findChildNode(parent, cursor);
            if (child == null) {
                ProjectFileItem childItem = last ? item : ProjectFileItem.folder(cursor, projectRoot);
                child = new DefaultMutableTreeNode(childItem);
                parent.add(child);
                knownFileItems.put(childItem.path(), childItem);
            } else if (last) {
                child.setUserObject(item);
                knownFileItems.put(item.path(), item);
            }
            parent = child;
        }
    }

    private DefaultMutableTreeNode findChildNode(DefaultMutableTreeNode parent, Path path) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(index);
            ProjectFileItem item = treeItem(child);
            if (item != null && samePath(item.path(), path)) {
                return child;
            }
        }
        return null;
    }

    private void sortTreeChildren(DefaultMutableTreeNode node) {
        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (int index = 0; index < node.getChildCount(); index++) {
            children.add((DefaultMutableTreeNode) node.getChildAt(index));
        }
        children.sort(this::compareTreeNodes);
        node.removeAllChildren();
        for (DefaultMutableTreeNode child : children) {
            sortTreeChildren(child);
            node.add(child);
        }
    }

    private int compareTreeNodes(DefaultMutableTreeNode first, DefaultMutableTreeNode second) {
        ProjectFileItem firstItem = treeItem(first);
        ProjectFileItem secondItem = treeItem(second);
        if (firstItem == null || secondItem == null) {
            return 0;
        }

        boolean firstFolder = firstItem.kind() == FileKind.FOLDER;
        boolean secondFolder = secondItem.kind() == FileKind.FOLDER;
        if (firstFolder != secondFolder) {
            return firstFolder ? -1 : 1;
        }
        return firstItem.displayName().compareToIgnoreCase(secondItem.displayName());
    }

    private Set<Path> expandedProjectPaths() {
        Set<Path> expanded = new HashSet<>();
        Object root = fileTreeModel.getRoot();
        if (root == null) {
            return expanded;
        }

        Enumeration<TreePath> paths = fileTree.getExpandedDescendants(new TreePath(root));
        if (paths == null) {
            return expanded;
        }

        while (paths.hasMoreElements()) {
            ProjectFileItem item = treeItem(paths.nextElement());
            if (item != null) {
                expanded.add(item.path());
            }
        }
        return expanded;
    }

    private void restoreExpandedProjectPaths(DefaultMutableTreeNode root, Set<Path> expanded) {
        fileTree.expandPath(new TreePath(root.getPath()));
        expandTreeNodes(root, expanded);
        if (activeFile != null) {
            expandParentsOfPath(root, activeFile);
        } else if (mainFile != null) {
            expandParentsOfPath(root, mainFile);
        }
    }

    private void expandTreeNodes(DefaultMutableTreeNode node, Set<Path> expanded) {
        ProjectFileItem item = treeItem(node);
        if (item != null && expanded.contains(item.path())) {
            fileTree.expandPath(new TreePath(node.getPath()));
        }

        for (int index = 0; index < node.getChildCount(); index++) {
            expandTreeNodes((DefaultMutableTreeNode) node.getChildAt(index), expanded);
        }
    }

    private void expandParentsOfPath(DefaultMutableTreeNode root, Path path) {
        DefaultMutableTreeNode node = findNode(root, path);
        if (node == null) {
            return;
        }

        Object[] pathItems = node.getPath();
        for (int index = 0; index < pathItems.length - 1; index++) {
            fileTree.expandPath(new TreePath(java.util.Arrays.copyOf(pathItems, index + 1)));
        }
    }

    private void selectCurrentTreeItem(DefaultMutableTreeNode root) {
        Path selected = activeFile == null ? mainFile : activeFile;
        DefaultMutableTreeNode node = selected == null ? null : findNode(root, selected);
        if (node == null) {
            fileTree.clearSelection();
            return;
        }

        TreePath path = new TreePath(node.getPath());
        fileTree.setSelectionPath(path);
        fileTree.scrollPathToVisible(path);
    }

    private DefaultMutableTreeNode findNode(DefaultMutableTreeNode node, Path path) {
        ProjectFileItem item = treeItem(node);
        if (item != null && samePath(item.path(), path)) {
            return node;
        }

        for (int index = 0; index < node.getChildCount(); index++) {
            DefaultMutableTreeNode result = findNode((DefaultMutableTreeNode) node.getChildAt(index), path);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private List<OutlineItem> parseOutline(String sourceText, Path sourceFile) {
        List<OutlineItem> outline = new ArrayList<>();
        if (sourceText == null || sourceText.isBlank()) {
            return outline;
        }

        String[] lines = sourceText.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = stripLatexComment(lines[index]);
            Matcher matcher = OUTLINE_COMMAND.matcher(line);
            while (matcher.find()) {
                String command = matcher.group(1);
                String title = cleanOutlineTitle(matcher.group(2));
                if (!title.isBlank()) {
                    outline.add(new OutlineItem(sourceFile, title, command, index + 1, outlineLevel(command)));
                }
            }
        }
        return outline;
    }

    private String cleanOutlineTitle(String title) {
        String cleaned = title == null ? "" : title.trim();
        for (int pass = 0; pass < 4; pass++) {
            Matcher matcher = SIMPLE_BRACED_FORMAT_COMMAND.matcher(cleaned);
            String replaced = matcher.replaceAll("$1");
            if (replaced.equals(cleaned)) {
                break;
            }
            cleaned = replaced;
        }

        return cleaned
            .replace("\\&", "&")
            .replace("\\%", "%")
            .replace("\\_", "_")
            .replace("~", " ")
            .trim();
    }

    private int outlineLevel(String command) {
        return switch (command) {
            case "part", "chapter", "section" -> 0;
            case "subsection" -> 1;
            case "subsubsection" -> 2;
            case "paragraph" -> 3;
            case "subparagraph" -> 4;
            default -> 0;
        };
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(4, 4));
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        JLabel title = new JLabel("Project");
        JButton hideButton = smallButton("Hide");
        hideButton.addActionListener(event -> {
            if (fileActionHandler != null) {
                fileActionHandler.hideFilesPanel();
            }
        });

        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(hideButton, BorderLayout.EAST);

        backButton.setFocusable(false);
        backButton.setMargin(new Insets(4, 8, 4, 8));
        backButton.setVisible(false);
        backButton.setToolTipText("Use Open to switch project folders");
        backButton.addActionListener(event -> {
            if (fileActionHandler != null) {
                fileActionHandler.navigateBack(projectRoot());
            }
        });

        newTexButton.setFocusable(false);
        newTexButton.setMargin(new Insets(4, 8, 4, 8));
        newTexButton.addActionListener(event -> {
            if (fileActionHandler != null) {
                fileActionHandler.createTexFile(projectRoot());
            }
        });

        newFolderButton.setFocusable(false);
        newFolderButton.setMargin(new Insets(4, 8, 4, 8));
        newFolderButton.addActionListener(event -> {
            if (fileActionHandler != null) {
                fileActionHandler.createFolder(projectRoot());
            }
        });

        refreshButton.setFocusable(false);
        refreshButton.setMargin(new Insets(4, 8, 4, 8));
        refreshButton.addActionListener(event -> refresh(
            projectRoot,
            activeFile,
            mainFile,
            activeSourceText,
            referenceSourceText
        ));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actionRow.add(backButton);
        actionRow.add(newTexButton);
        actionRow.add(newFolderButton);
        actionRow.add(refreshButton);

        JPanel mainFileRow = new JPanel(new BorderLayout(6, 0));
        JLabel mainFileLabel = new JLabel("Main");
        mainFileLabel.setPreferredSize(new Dimension(38, 24));
        mainFileRow.add(mainFileLabel, BorderLayout.WEST);
        mainFileRow.add(mainFileSelector, BorderLayout.CENTER);

        JPanel generatedFilesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        generatedFilesRow.add(hideGeneratedFilesBox);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(actionRow);
        controls.add(mainFileRow);
        controls.add(generatedFilesRow);

        header.add(titleRow, BorderLayout.NORTH);
        header.add(controls, BorderLayout.CENTER);
        return header;
    }

    private JButton smallButton(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.setMargin(new Insets(4, 8, 4, 8));
        return button;
    }

    private void updateActionButtons() {
        boolean hasProject = projectRoot() != null;
        backButton.setEnabled(false);
        newTexButton.setEnabled(hasProject);
        newFolderButton.setEnabled(hasProject);
        refreshButton.setEnabled(hasProject);
        mainFileSelector.setEnabled(hasProject && mainFileSelector.getItemCount() > 0);
        hideGeneratedFilesBox.setEnabled(hasProject);
    }

    private Path projectRoot() {
        return projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
    }

    private Path normalizeProjectRoot(Path projectRoot, Path sourceFile) {
        if (projectRoot != null) {
            return projectRoot.toAbsolutePath().normalize();
        }
        if (sourceFile != null) {
            return sourceFile.toAbsolutePath().normalize().getParent();
        }
        return null;
    }

    private ProjectScan scanProject(Path projectRoot, Path activeFile, Path mainFile, String sourceText) throws IOException {
        if (projectRoot == null) {
            return new ProjectScan(List.of(), 0, List.of());
        }

        Set<Path> references = referencesInsideProject(projectRoot, collectReferences(projectRoot, sourceText));
        Map<Path, ProjectFileItem> items = new HashMap<>();
        if (mainFile != null) {
            addItem(items, ProjectFileItem.existing(mainFile, projectRoot, true, true));
        }
        if (activeFile != null) {
            addItem(items, ProjectFileItem.existing(activeFile, projectRoot, true, samePath(activeFile, mainFile)));
        }

        // Each directory is opened independently so protected Windows folders are skipped.
        // The caps keep very large workspaces responsive while still feeling like an IDE tree.
        List<Path> projectFiles = new ArrayList<>();
        Set<Path> projectFolders = new HashSet<>();
        collectProjectFiles(projectRoot, mainFile, references, 0, projectFiles, projectFolders);
        for (Path folder : projectFolders) {
            addItem(items, ProjectFileItem.folder(folder, projectRoot));
        }
        for (Path path : projectFiles) {
            Path normalized = path.toAbsolutePath().normalize();
            boolean used = references.contains(normalized) || samePath(normalized, activeFile) || samePath(normalized, mainFile);
            addItem(items, ProjectFileItem.existing(normalized, projectRoot, used, samePath(normalized, mainFile)));
        }

        for (Path reference : references) {
            if (items.containsKey(reference)) {
                continue;
            }

            if (Files.isRegularFile(reference)) {
                addItem(items, ProjectFileItem.existing(reference, projectRoot, true, false));
            } else {
                addItem(items, ProjectFileItem.missing(reference, projectRoot));
            }
        }

        List<ProjectFileItem> sorted = new ArrayList<>(items.values());
        sorted.sort(ProjectFileItem.ORDER);
        List<Path> texFiles = sorted.stream()
            .filter(item -> item.kind() == FileKind.LATEX && !item.missing())
            .map(ProjectFileItem::path)
            .toList();
        long usedCount = sorted.stream().filter(ProjectFileItem::used).count();
        return new ProjectScan(sorted, (int) usedCount, texFiles);
    }

    private Set<Path> referencesInsideProject(Path projectRoot, Set<Path> references) {
        Set<Path> inside = new LinkedHashSet<>();
        for (Path reference : references) {
            if (isSameOrInside(projectRoot, reference)) {
                inside.add(reference);
            }
        }
        return inside;
    }

    private void addItem(Map<Path, ProjectFileItem> items, ProjectFileItem item) {
        items.put(item.path(), item);
    }

    private void collectProjectFiles(
        Path directory,
        Path sourceFile,
        Set<Path> references,
        int depth,
        List<Path> projectFiles,
        Set<Path> projectFolders
    ) {
        if (depth > MAX_SCAN_DEPTH
            || projectFiles.size() >= MAX_PROJECT_FILES
            || projectFolders.size() >= MAX_PROJECT_FOLDERS) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (projectFiles.size() >= MAX_PROJECT_FILES) {
                    return;
                }

                Path normalized = child.toAbsolutePath().normalize();
                if (Files.isDirectory(normalized)) {
                    if (isReadableDirectory(normalized)) {
                        projectFolders.add(normalized);
                    }
                    collectProjectFiles(normalized, sourceFile, references, depth + 1, projectFiles, projectFolders);
                    continue;
                }

                if (Files.isRegularFile(normalized) && isProjectFile(normalized, sourceFile, references)) {
                    projectFiles.add(normalized);
                }
            }
        } catch (IOException | SecurityException ignored) {
            // Some user folders, such as Documents\My Music, are protected junctions.
            // They are unrelated to the LaTeX project, so the file panel simply skips them.
        }
    }

    private boolean isReadableDirectory(Path directory) {
        try (DirectoryStream<Path> ignored = Files.newDirectoryStream(directory)) {
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private Set<Path> collectReferences(Path projectRoot, String sourceText) {
        Set<Path> references = new LinkedHashSet<>();
        Set<Path> graphicsPaths = collectGraphicsPaths(projectRoot, sourceText);

        for (String line : sourceText.lines().map(this::stripLatexComment).toList()) {
            Matcher commandMatcher = BRACED_COMMAND.matcher(line);
            while (commandMatcher.find()) {
                String command = commandMatcher.group(1);
                String value = commandMatcher.group(2).trim();
                if (value.isBlank()) {
                    continue;
                }

                switch (command) {
                    case "includegraphics" -> resolveGraphicsReference(projectRoot, graphicsPaths, value).forEach(references::add);
                    case "input", "include", "lstinputlisting" -> resolveTextReference(projectRoot, value).ifPresent(references::add);
                    default -> {
                        // The regex command list is exhaustive; this branch keeps future edits harmless.
                    }
                }
            }

            collectBibliographyReferences(projectRoot, BIBLIOGRAPHY_COMMAND.matcher(line), references);
            collectBibliographyReferences(projectRoot, BIB_RESOURCE_COMMAND.matcher(line), references);
        }

        return references;
    }

    private Set<Path> collectGraphicsPaths(Path projectRoot, String sourceText) {
        Set<Path> paths = new HashSet<>();
        paths.add(projectRoot);

        for (String line : sourceText.lines().map(this::stripLatexComment).toList()) {
            int commandStart = line.indexOf("\\graphicspath");
            if (commandStart < 0) {
                continue;
            }

            Matcher matcher = GRAPHICS_PATH_ENTRY.matcher(line.substring(commandStart));
            while (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.isBlank()) {
                    paths.add(resolvePath(projectRoot, value));
                }
            }
        }

        return paths;
    }

    private List<Path> resolveGraphicsReference(Path projectRoot, Set<Path> graphicsPaths, String value) {
        List<Path> matches = new ArrayList<>();
        Path direct = resolvePath(projectRoot, value);
        if (hasExtension(value)) {
            for (Path graphicsPath : graphicsPaths) {
                Path candidate = graphicsPath.resolve(value).toAbsolutePath().normalize();
                if (Files.isRegularFile(candidate)) {
                    matches.add(candidate);
                }
            }
            if (matches.isEmpty()) {
                matches.add(existingOrDirect(direct));
            }
            return matches;
        }

        for (Path graphicsPath : graphicsPaths) {
            for (String extension : IMAGE_EXTENSIONS) {
                Path candidate = graphicsPath.resolve(value + extension).toAbsolutePath().normalize();
                if (Files.isRegularFile(candidate)) {
                    matches.add(candidate);
                }
            }
        }

        if (matches.isEmpty()) {
            matches.add(direct);
        }
        return matches;
    }

    private Path existingOrDirect(Path path) {
        return Files.isRegularFile(path) ? path.toAbsolutePath().normalize() : path;
    }

    private java.util.Optional<Path> resolveTextReference(Path projectRoot, String value) {
        if (value.equals("glyphtounicode")) {
            return java.util.Optional.empty();
        }

        Path direct = resolvePath(projectRoot, value);
        if (hasExtension(value)) {
            return java.util.Optional.of(existingOrDirect(direct));
        }

        Path texCandidate = resolvePath(projectRoot, value + ".tex");
        if (Files.isRegularFile(texCandidate)) {
            return java.util.Optional.of(texCandidate);
        }
        return java.util.Optional.of(direct);
    }

    private void collectBibliographyReferences(Path projectRoot, Matcher matcher, Set<Path> references) {
        while (matcher.find()) {
            for (String value : matcher.group(1).split(",")) {
                String trimmed = value.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                references.add(resolvePath(projectRoot, hasExtension(trimmed) ? trimmed : trimmed + ".bib"));
            }
        }
    }

    private Path resolvePath(Path projectRoot, String value) {
        Path path = Path.of(value.replace('\\', '/'));
        if (!path.isAbsolute()) {
            path = projectRoot.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private boolean isProjectFile(Path path, Path sourceFile, Set<Path> references) {
        Path normalized = path.toAbsolutePath().normalize();
        if (references.contains(normalized) || samePath(normalized, sourceFile)) {
            return true;
        }

        String name = path.getFileName().toString();
        if (name.endsWith(".latex-compiler-sync.properties") || name.endsWith(".latexcompiler-sync.properties")) {
            return false;
        }

        if (hideGeneratedFiles && isGeneratedBuildArtifact(path)) {
            return false;
        }
        return true;
    }

    private boolean isGeneratedBuildArtifact(Path path) {
        String fileName = path.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);

        String artifactBase;
        if (lowerName.endsWith(".synctex.gz")) {
            artifactBase = lowerName.substring(0, lowerName.length() - ".synctex.gz".length());
            return hasSiblingTex(path, artifactBase);
        }
        if (lowerName.endsWith(".run.xml")) {
            artifactBase = lowerName.substring(0, lowerName.length() - ".run.xml".length());
            return hasSiblingTex(path, artifactBase);
        }

        String extension = extension(fileName);
        Set<String> generatedExtensions = Set.of(
            ".aux",
            ".bbl",
            ".bcf",
            ".blg",
            ".dvi",
            ".fdb_latexmk",
            ".fls",
            ".lof",
            ".log",
            ".lot",
            ".nav",
            ".out",
            ".pdf",
            ".ps",
            ".snm",
            ".toc",
            ".vrb",
            ".xdv"
        );
        if (!generatedExtensions.contains(extension)) {
            return false;
        }

        artifactBase = baseName(fileName).toLowerCase(Locale.ROOT);
        return hasSiblingTex(path, artifactBase);
    }

    private boolean hasSiblingTex(Path path, String artifactBase) {
        Path parent = path.getParent();
        if (parent == null || artifactBase == null || artifactBase.isBlank()) {
            return false;
        }

        try (DirectoryStream<Path> siblings = Files.newDirectoryStream(parent)) {
            for (Path sibling : siblings) {
                if (!Files.isRegularFile(sibling)) {
                    continue;
                }

                String name = sibling.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).endsWith(".tex")
                    && baseName(name).equalsIgnoreCase(artifactBase)) {
                    return true;
                }
            }
        } catch (IOException | SecurityException ignored) {
            // If a folder cannot be scanned here, keep the file visible instead of hiding real data.
        }
        return false;
    }

    private String stripLatexComment(String line) {
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '%' && !escaped) {
                return line.substring(0, index);
            }
            escaped = character == '\\' && !escaped;
            if (character != '\\') {
                escaped = false;
            }
        }
        return line;
    }

    private boolean samePath(Path first, Path second) {
        return first != null
            && second != null
            && first.toAbsolutePath().normalize().toString().equalsIgnoreCase(second.toAbsolutePath().normalize().toString());
    }

    private boolean hasExtension(String value) {
        return !extension(value).isBlank();
    }

    private String extension(String value) {
        String fileName = Path.of(value).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void openSelectedOutlineItem() {
        OutlineItem item = outlineList.getSelectedValue();
        if (item != null && fileActionHandler != null) {
            fileActionHandler.openOutlineLine(item.sourceFile(), item.lineNumber());
        }
    }

    private void openOutlineItemAt(MouseEvent event) {
        int index = outlineList.locationToIndex(event.getPoint());
        if (index < 0) {
            return;
        }

        Rectangle cellBounds = outlineList.getCellBounds(index, index);
        if (cellBounds == null || !cellBounds.contains(event.getPoint())) {
            return;
        }

        outlineList.setSelectedIndex(index);
        openSelectedOutlineItem();
    }

    private void openSelectedTreeItem() {
        ProjectFileItem item = selectedTreeItem();
        if (item == null || item.missing() || fileActionHandler == null) {
            return;
        }

        TreePath path = fileTree.getSelectionPath();
        if (item.kind() == FileKind.FOLDER) {
            toggleTreeFolder(path);
            return;
        }

        fileActionHandler.openFile(item.path());
    }

    private void openSingleClickedTreeFile(TreePath path) {
        ProjectFileItem item = treeItem(path);
        if (item == null
            || item.missing()
            || item.kind() == FileKind.FOLDER
            || fileActionHandler == null) {
            return;
        }

        fileActionHandler.openFile(item.path());
    }

    private boolean isChevronClick(TreePath path, MouseEvent event) {
        ProjectFileItem item = treeItem(path);
        if (item == null || item.kind() != FileKind.FOLDER) {
            return false;
        }

        Rectangle bounds = fileTree.getPathBounds(path);
        return bounds != null && event.getX() >= bounds.x && event.getX() <= bounds.x + 22;
    }

    private void toggleTreeFolder(TreePath path) {
        if (path == null) {
            return;
        }

        if (fileTree.isExpanded(path)) {
            fileTree.collapsePath(path);
        } else {
            fileTree.expandPath(path);
        }
    }

    private void showItemMenu(MouseEvent event) {
        if (!event.isPopupTrigger() || fileActionHandler == null) {
            return;
        }

        TreePath path = treePathAt(event);
        if (path == null) {
            return;
        }

        fileTree.setSelectionPath(path);
        ProjectFileItem item = treeItem(path);
        if (item == null || item.missing()) {
            return;
        }

        JPopupMenu menu = popupMenu();
        if (item.kind() == FileKind.FOLDER) {
            String expandText = fileTree.isExpanded(path) ? "Collapse" : "Expand";
            menu.add(menuItem(expandText, () -> toggleTreeFolder(path)));
            menu.add(menuItem("New TeX File Here", () -> fileActionHandler.createTexFile(item.path())));
            menu.add(menuItem("New Folder Here", () -> fileActionHandler.createFolder(item.path())));
        } else {
            menu.add(menuItem("Open", () -> fileActionHandler.openFile(item.path())));
        }
        if (item.kind() == FileKind.LATEX) {
            menu.add(menuItem("Set as Main File", () -> fileActionHandler.setMainFile(item.path())));
        }
        if (!item.root()) {
            addPopupSeparator(menu);
            menu.add(menuItem("Rename", () -> fileActionHandler.rename(item.path())));
            menu.add(menuItem("Copy...", () -> fileActionHandler.copy(item.path())));
            menu.add(menuItem("Move...", () -> fileActionHandler.move(item.path())));
            menu.add(menuItem("Delete", () -> fileActionHandler.delete(item.path())));
        }
        addPopupSeparator(menu);
        menu.add(menuItem("Refresh", () -> refresh(
            projectRoot,
            activeFile,
            mainFile,
            activeSourceText,
            referenceSourceText
        )));
        menu.show(fileTree, event.getX(), event.getY());
    }

    private TreePath treePathAt(MouseEvent event) {
        TreePath path = fileTree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return null;
        }

        Rectangle bounds = fileTree.getPathBounds(path);
        return bounds != null && bounds.contains(event.getPoint()) ? path : null;
    }

    private ProjectFileItem selectedTreeItem() {
        return treeItem(fileTree.getSelectionPath());
    }

    private ProjectFileItem treeItem(TreePath path) {
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
            return null;
        }
        return treeItem(node);
    }

    private ProjectFileItem treeItem(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof ProjectFileItem item)) {
            return null;
        }
        return item;
    }

    private JPopupMenu popupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setUI(new BasicPopupMenuUI());
        menu.setOpaque(true);
        menu.setBackground(theme.panelBackground());
        menu.setForeground(theme.text());
        menu.setBorder(BorderFactory.createLineBorder(theme.border()));
        return menu;
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setUI(new BasicMenuItemUI());
        item.setOpaque(true);
        item.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        item.setRolloverEnabled(true);
        item.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        Object oldListener = item.getClientProperty(POPUP_MENU_CHANGE_LISTENER_KEY);
        if (oldListener instanceof ChangeListener listener) {
            item.getModel().removeChangeListener(listener);
        }

        ChangeListener listener = event -> applyPopupMenuItemState(item);
        item.getModel().addChangeListener(listener);
        item.putClientProperty(POPUP_MENU_CHANGE_LISTENER_KEY, listener);
        applyPopupMenuItemState(item);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void applyPopupMenuItemState(JMenuItem item) {
        boolean active = item.getModel().isArmed() || item.getModel().isPressed() || item.getModel().isRollover();
        Color background = theme.panelBackground();
        Color foreground = theme.text();

        if (!item.isEnabled()) {
            foreground = theme.disabledText();
        } else if (active) {
            background = theme.listSelectionBackground();
            foreground = theme.listSelectionForeground();
        }

        item.setBackground(background);
        item.setForeground(foreground);
        item.repaint();
    }

    private void addPopupSeparator(JPopupMenu menu) {
        JSeparator separator = new JSeparator();
        separator.setForeground(theme.border());
        separator.setBackground(theme.panelBackground());
        menu.add(separator);
    }

    public boolean hasKnownUsedItemAtOrInside(Path path) {
        if (path == null) {
            return false;
        }

        Path normalized = path.toAbsolutePath().normalize();
        for (ProjectFileItem item : knownFileItems.values()) {
            if ((item.used() || item.main()) && isSameOrInside(normalized, item.path())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameOrInside(Path parent, Path child) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        String parentText = normalizedParent.toString().toLowerCase(Locale.ROOT);
        String childText = normalizedChild.toString().toLowerCase(Locale.ROOT);
        return childText.equals(parentText) || childText.startsWith(parentText + java.io.File.separator);
    }

    public interface FileActionHandler {
        void openFile(Path file);

        void setMainFile(Path file);

        void openOutlineLine(Path sourceFile, int lineNumber);

        void navigateBack(Path projectRoot);

        void hideFilesPanel();

        void hideGeneratedFilesChanged(boolean hideGeneratedFiles);

        void createTexFile(Path projectRoot);

        void createFolder(Path projectRoot);

        void rename(Path file);

        void copy(Path file);

        void move(Path file);

        void moveToFolder(Path file, Path destinationFolder);

        void delete(Path file);
    }

    private record ProjectScan(List<ProjectFileItem> items, int usedCount, List<Path> texFiles) {
    }

    private record OutlineItem(Path sourceFile, String title, String command, int lineNumber, int level) {
    }

    private enum FileKind {
        FOLDER(0),
        LATEX(1),
        BIBLIOGRAPHY(2),
        STYLE(3),
        IMAGE(4),
        PDF(5),
        WORD(6),
        PRESENTATION(7),
        DATA(8),
        CODE(9),
        SCRIPT(10),
        MARKDOWN(11),
        SYNCTEX(12),
        OTHER(13);

        private final int sortOrder;

        FileKind(int sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    private record ProjectFileItem(
        Path path,
        String displayName,
        FileKind kind,
        boolean used,
        boolean main,
        boolean missing,
        boolean root
    ) {
        private static final Comparator<ProjectFileItem> ORDER = Comparator
            .<ProjectFileItem, Boolean>comparing(item -> item.missing())
            .thenComparing(item -> !item.main())
            .thenComparing(item -> !item.used())
            .thenComparingInt(item -> item.kind().sortOrder)
            .thenComparing(ProjectFileItem::displayName, String.CASE_INSENSITIVE_ORDER);

        private static ProjectFileItem existing(Path path, Path projectRoot, boolean used, boolean main) {
            return new ProjectFileItem(
                path,
                displayName(path, projectRoot),
                kindFor(path),
                used,
                main,
                false,
                false
            );
        }

        private static ProjectFileItem folder(Path path, Path projectRoot) {
            return new ProjectFileItem(
                path,
                displayName(path, projectRoot),
                FileKind.FOLDER,
                false,
                false,
                false,
                false
            );
        }

        private static ProjectFileItem missing(Path path, Path projectRoot) {
            return new ProjectFileItem(
                path,
                displayName(path, projectRoot) + " (missing)",
                kindFor(path),
                true,
                false,
                true,
                false
            );
        }

        private static ProjectFileItem root(Path path) {
            return new ProjectFileItem(
                path,
                rootDisplayName(path),
                FileKind.FOLDER,
                false,
                false,
                false,
                true
            );
        }

        private static ProjectFileItem placeholder() {
            return new ProjectFileItem(
                Path.of(".").toAbsolutePath().normalize(),
                "Open a folder",
                FileKind.FOLDER,
                false,
                false,
                false,
                true
            );
        }

        private static String displayName(Path path, Path projectRoot) {
            try {
                return projectRoot.relativize(path).toString();
            } catch (IllegalArgumentException ignored) {
                return path.toString();
            }
        }

        private static String rootDisplayName(Path path) {
            if (path == null) {
                return "PROJECT";
            }

            Path fileName = path.getFileName();
            String value = fileName == null ? path.toString() : fileName.toString();
            return value.isBlank() ? "PROJECT" : value.toUpperCase(Locale.ROOT);
        }

        private static FileKind kindFor(Path path) {
            if (Files.isDirectory(path)) {
                return FileKind.FOLDER;
            }

            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".synctex.gz")) {
                return FileKind.SYNCTEX;
            }

            String extension = extensionFor(fileName);
            return switch (extension) {
                case ".tex" -> FileKind.LATEX;
                case ".bib" -> FileKind.BIBLIOGRAPHY;
                case ".cls", ".sty" -> FileKind.STYLE;
                case ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".eps", ".svg" -> FileKind.IMAGE;
                case ".pdf" -> FileKind.PDF;
                case ".doc", ".docx" -> FileKind.WORD;
                case ".ppt", ".pptx" -> FileKind.PRESENTATION;
                case ".csv", ".tsv", ".json", ".yaml", ".yml", ".xml", ".xlsx" -> FileKind.DATA;
                case ".java", ".js", ".jsx", ".ts", ".tsx", ".py", ".html", ".css", ".scss", ".gradle" -> FileKind.CODE;
                case ".ps1", ".bat", ".cmd", ".sh" -> FileKind.SCRIPT;
                case ".md", ".markdown", ".txt" -> FileKind.MARKDOWN;
                default -> FileKind.OTHER;
            };
        }

        private static String extensionFor(String fileName) {
            int dot = fileName.lastIndexOf('.');
            return dot >= 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
        }
    }

    private final class MainFileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            if (value instanceof Path path) {
                label.setText(displayPath(path));
                label.setToolTipText(path.toString());
            } else {
                label.setText("No .tex files");
                label.setToolTipText(null);
            }
            label.setBackground(isSelected ? theme.listSelectionBackground() : theme.raisedBackground());
            label.setForeground(isSelected ? theme.listSelectionForeground() : theme.text());
            return label;
        }

        private String displayPath(Path path) {
            if (projectRoot != null && isSameOrInside(projectRoot, path)) {
                return projectRoot.relativize(path).toString();
            }

            Path fileName = path.getFileName();
            return fileName == null ? path.toString() : fileName.toString();
        }
    }

    private final class ProjectTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(
                tree,
                value,
                selected,
                expanded,
                leaf,
                row,
                cellHasFocus
            );
            ProjectFileItem item = value instanceof DefaultMutableTreeNode node ? treeItem(node) : null;
            if (item == null) {
                return label;
            }

            label.setText(treeDisplayName(item));
            if (item.main()) {
                label.setText(treeDisplayName(item) + "  [main]");
            }
            boolean hovered = new TreePath(((DefaultMutableTreeNode) value).getPath()).equals(hoveredTreePath);
            Icon baseIcon = new FileTypeIcon(item.kind(), expanded, selected);
            label.setIcon(new TreeItemIcon(baseIcon, item.kind() == FileKind.FOLDER, expanded, selected));
            label.setToolTipText(item.path().toString());
            label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            label.setFont(tree.getFont().deriveFont(item.root() || item.used() || item.main() ? Font.BOLD : Font.PLAIN));
            label.setBackground(selected ? theme.listSelectionBackground() : (hovered ? hoverBackground() : theme.panelBackground()));

            if (selected) {
                label.setForeground(theme.listSelectionForeground());
            } else if (item.missing()) {
                label.setForeground(theme.missingText());
            } else if (!item.root() && !item.used()) {
                label.setForeground(theme.mutedText());
            } else {
                label.setForeground(theme.text());
            }

            label.setHorizontalTextPosition(JLabel.RIGHT);
            label.setIconTextGap(8);
            ((JComponent) label).setOpaque(true);
            return label;
        }

        private String treeDisplayName(ProjectFileItem item) {
            if (item.root()) {
                return item.displayName();
            }

            Path fileName = item.path().getFileName();
            return fileName == null ? item.displayName() : fileName.toString();
        }
    }

    private final class TreeItemIcon implements Icon {
        private static final int CHEVRON_WIDTH = 16;
        private static final int GAP = 3;

        private final Icon baseIcon;
        private final boolean folder;
        private final boolean expanded;
        private final boolean selected;

        private TreeItemIcon(Icon baseIcon, boolean folder, boolean expanded, boolean selected) {
            this.baseIcon = baseIcon;
            this.folder = folder;
            this.expanded = expanded;
            this.selected = selected;
        }

        @Override
        public int getIconWidth() {
            return CHEVRON_WIDTH + GAP + (baseIcon == null ? 16 : baseIcon.getIconWidth());
        }

        @Override
        public int getIconHeight() {
            return Math.max(16, baseIcon == null ? 16 : baseIcon.getIconHeight());
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            if (folder) {
                paintChevron(graphics, x + 4, y + (getIconHeight() - 8) / 2);
            }

            if (baseIcon != null) {
                int iconX = x + CHEVRON_WIDTH + GAP;
                int iconY = y + (getIconHeight() - baseIcon.getIconHeight()) / 2;
                baseIcon.paintIcon(component, graphics, iconX, iconY);
            }
        }

        private void paintChevron(Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(selected ? theme.listSelectionForeground() : theme.mutedText());
                if (expanded) {
                    g2.drawLine(x, y + 2, x + 4, y + 6);
                    g2.drawLine(x + 4, y + 6, x + 8, y + 2);
                } else {
                    g2.drawLine(x + 2, y, x + 6, y + 4);
                    g2.drawLine(x + 6, y + 4, x + 2, y + 8);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private final class FileTypeIcon implements Icon {
        private static final int SIZE = 16;

        private final FileKind kind;
        private final boolean expanded;
        private final boolean selected;

        private FileTypeIcon(FileKind kind, boolean expanded, boolean selected) {
            this.kind = kind;
            this.expanded = expanded;
            this.selected = selected;
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                switch (kind) {
                    case FOLDER -> paintFolder(g2, x, y);
                    case IMAGE -> paintImage(g2, x, y);
                    case CODE -> paintDocument(component, g2, x, y, accentFor(kind), "<>");
                    case LATEX -> paintDocument(component, g2, x, y, accentFor(kind), "TeX");
                    case BIBLIOGRAPHY -> paintDocument(component, g2, x, y, accentFor(kind), "BIB");
                    case STYLE -> paintDocument(component, g2, x, y, accentFor(kind), "STY");
                    case PDF -> paintDocument(component, g2, x, y, accentFor(kind), "PDF");
                    case WORD -> paintDocument(component, g2, x, y, accentFor(kind), "W");
                    case PRESENTATION -> paintDocument(component, g2, x, y, accentFor(kind), "PPT");
                    case DATA -> paintDocument(component, g2, x, y, accentFor(kind), "CSV");
                    case SCRIPT -> paintDocument(component, g2, x, y, accentFor(kind), ">");
                    case MARKDOWN -> paintDocument(component, g2, x, y, accentFor(kind), "MD");
                    case SYNCTEX -> paintDocument(component, g2, x, y, accentFor(kind), "GZ");
                    case OTHER -> paintDocument(component, g2, x, y, accentFor(kind), "");
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintFolder(Graphics2D g2, int x, int y) {
            Color accent = accentFor(FileKind.FOLDER);
            Color body = expanded
                ? blend(accent, Color.WHITE, theme.darkMode() ? 0.20 : 0.35)
                : blend(accent, theme.panelBackground(), theme.darkMode() ? 0.18 : 0.08);

            g2.setColor(blend(accent, theme.panelBackground(), 0.18));
            g2.fillRoundRect(x + 2, y + 3, 6, 4, 2, 2);
            g2.fillRoundRect(x + 1, y + 6, 14, 8, 2, 2);
            g2.setColor(body);
            g2.fillRoundRect(x + 1, y + 7, 14, 7, 2, 2);
            g2.setColor(blend(accent, theme.text(), theme.darkMode() ? 0.05 : 0.18));
            g2.drawRoundRect(x + 1, y + 6, 14, 8, 2, 2);
        }

        private void paintImage(Graphics2D g2, int x, int y) {
            Color accent = accentFor(FileKind.IMAGE);
            int left = x + 2;
            int top = y + 2;
            paintPageBody(g2, left, top, accent);

            // A tiny landscape mark makes image assets stand apart from source files.
            g2.setColor(blend(accent, Color.WHITE, theme.darkMode() ? 0.10 : 0.30));
            g2.drawRect(left + 4, top + 4, 6, 6);
            g2.drawLine(left + 4, top + 9, left + 6, top + 7);
            g2.drawLine(left + 6, top + 7, left + 8, top + 9);
            g2.fillOval(left + 8, top + 5, 2, 2);
        }

        private void paintDocument(Component component, Graphics2D g2, int x, int y, Color accent, String label) {
            int left = x + 2;
            int top = y + 1;
            paintPageBody(g2, left, top, accent);
            if (!label.isBlank()) {
                drawIconLabel(component, g2, label, accent, left, top);
            }
        }

        private void paintPageBody(Graphics2D g2, int left, int top, Color accent) {
            Color paper = theme.darkMode() ? new Color(30, 36, 44) : Color.WHITE;
            if (selected) {
                paper = blend(paper, theme.listSelectionBackground(), theme.darkMode() ? 0.14 : 0.06);
            }

            g2.setColor(paper);
            g2.fillRect(left, top, 12, 14);
            g2.setColor(accent);
            g2.fillRect(left, top, 3, 14);
            g2.drawLine(left + 8, top, left + 12, top + 4);
            g2.drawLine(left + 12, top + 4, left + 8, top + 4);
            g2.setColor(blend(accent, theme.text(), theme.darkMode() ? 0.10 : 0.20));
            g2.drawRect(left, top, 12, 14);
        }

        private void drawIconLabel(Component component, Graphics2D g2, String label, Color accent, int left, int top) {
            Font baseFont = component == null ? null : component.getFont();
            float size = label.length() > 3 ? 4.4f : (label.length() > 2 ? 5.5f : 7f);
            Font font = baseFont == null
                ? new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size))
                : baseFont.deriveFont(Font.BOLD, size);
            g2.setFont(font);
            g2.setColor(accent);

            int textAreaLeft = left + 4;
            int textAreaWidth = 8;
            int textWidth = g2.getFontMetrics().stringWidth(label);
            int textX = textAreaLeft + Math.max(0, (textAreaWidth - textWidth) / 2);
            g2.drawString(label, textX, top + 11);
        }

        private Color accentFor(FileKind kind) {
            return switch (kind) {
                case FOLDER -> theme.darkMode() ? new Color(121, 192, 255) : new Color(9, 105, 218);
                case LATEX -> new Color(88, 166, 255);
                case BIBLIOGRAPHY -> new Color(219, 109, 40);
                case STYLE -> new Color(210, 168, 255);
                case IMAGE -> new Color(63, 185, 80);
                case PDF -> new Color(248, 81, 73);
                case WORD -> new Color(58, 139, 253);
                case PRESENTATION -> new Color(255, 166, 87);
                case DATA -> new Color(56, 185, 195);
                case CODE -> new Color(163, 113, 247);
                case SCRIPT -> new Color(139, 148, 158);
                case MARKDOWN -> new Color(121, 192, 255);
                case SYNCTEX -> theme.mutedText();
                case OTHER -> theme.mutedText();
            };
        }
    }

    private final class FileTreeTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            ProjectFileItem item = selectedTreeItem();
            if (item == null || item.root() || item.missing()) {
                return null;
            }
            return new ProjectPathTransferable(item.path());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(PROJECT_PATH_FLAVOR)) {
                return false;
            }

            ProjectFileItem destination = dropTargetFolder(support);
            return destination != null && !destination.missing();
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support) || fileActionHandler == null) {
                return false;
            }

            try {
                Path source = (Path) support.getTransferable().getTransferData(PROJECT_PATH_FLAVOR);
                ProjectFileItem destination = dropTargetFolder(support);
                if (source == null || destination == null || samePath(source, destination.path())) {
                    return false;
                }

                fileActionHandler.moveToFolder(source, destination.path());
                return true;
            } catch (IOException | UnsupportedFlavorException error) {
                return false;
            }
        }

        private ProjectFileItem dropTargetFolder(TransferSupport support) {
            if (!(support.getDropLocation() instanceof JTree.DropLocation dropLocation)) {
                return null;
            }

            TreePath path = dropLocation.getPath();
            ProjectFileItem item = treeItem(path);
            return item != null && item.kind() == FileKind.FOLDER ? item : null;
        }
    }

    private record ProjectPathTransferable(Path path) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { PROJECT_PATH_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PROJECT_PATH_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return path;
        }
    }

    private final class OutlineRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!(value instanceof OutlineItem item)) {
                return label;
            }

            label.setText(item.title());
            label.setToolTipText(item.command() + " on line " + item.lineNumber());
            label.setBorder(BorderFactory.createEmptyBorder(0, 8 + item.level() * 14, 0, 6));
            label.setFont(list.getFont().deriveFont(item.level() == 0 ? Font.BOLD : Font.PLAIN));
            label.setIcon(UIManager.getIcon("Tree.leafIcon"));
            label.setIconTextGap(7);

            if (isSelected) {
                label.setBackground(theme.listSelectionBackground());
                label.setForeground(theme.listSelectionForeground());
            } else if (index == hoveredOutlineIndex) {
                label.setBackground(hoverBackground());
                label.setForeground(theme.text());
            } else if (item.level() > 1) {
                label.setBackground(theme.panelBackground());
                label.setForeground(theme.mutedText());
            } else {
                label.setBackground(theme.panelBackground());
                label.setForeground(theme.text());
            }

            ((JComponent) label).setOpaque(true);
            return label;
        }
    }
}
