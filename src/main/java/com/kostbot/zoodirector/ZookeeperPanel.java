package com.kostbot.zoodirector;

import com.google.common.base.Strings;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.state.ConnectionState;
import com.netflix.curator.framework.state.ConnectionStateListener;
import com.netflix.curator.retry.RetryOneTime;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class ZookeeperPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperPanel.class);

    private ZookeeperSync zookeeperSync;
    private CuratorFramework client;

    private final String connectionString;
    private final int connectionRetryPeriod;

    private volatile boolean offline; // prevent operations if offline.

    private final Set<String> createdPaths;

    // Main UI
    private final JPanel mainPanel;

    private final JDialog logDialog;
    private final JTextArea logTextArea;
    private final JTextField lastLogTextField;

    // Zookeeper View UI
    private final JSplitPane splitPane;
    protected final DefaultTreeModel treeModel;
    protected final JTree tree;
    protected final DefaultMutableTreeNode rootNode;

    private final JMenuItem addNodeMenuItem;
    private final JMenuItem deleteNodeMenuItem;
    private final JMenuItem trimNodeMenuItem;
    private final JMenuItem pruneNodeMenuItem;

    private final JMenuItem addWatchMenuItem;
    private final JMenuItem removeWatchMenuItem;

    private final JTabbedPane tabbedPane;
    private final ZookeeperNodeEditPanel nodeEditPanel;
    private final ZookeeperWatchPanel watchPanel;

    private final SwingWorker<Void, Void> connectionWorker;

    /**
     * Helper method for extracting the Zookeeper path from tree node's user object.
     *
     * @param node tree node to extract ZookeeperNode instance from
     * @return zookeeper path of node
     */
    protected static String getZookeeperNodePath(DefaultMutableTreeNode node) {
        if (node == null)
            return null;
        return ((ZookeeperNode) node.getUserObject()).path;
    }

    /**
     * Helper method for getting tree path of node
     *
     * @param node node to get TreePath for
     * @return TreePath of given node
     */
    private static TreePath getTreePath(DefaultMutableTreeNode node) {
        return new TreePath(node.getPath());
    }

    /**
     * Return the currently set connection string.
     *
     * @return connection string
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Panel used for editing specified zookeeper node
     *
     * @param connectionString      zookeeper connection string
     * @param connectionRetryPeriod time to sleep between retries
     */
    public ZookeeperPanel(String connectionString, int connectionRetryPeriod) {
        this.connectionString = connectionString;
        this.connectionRetryPeriod = connectionRetryPeriod;

        this.setLayout(new BorderLayout());

        mainPanel = new JPanel(new BorderLayout());

        this.add(mainPanel, BorderLayout.CENTER);

        // Logging UI Setup

        JPanel lastLogPanel = new JPanel(new BorderLayout());
        JButton lastLogButton = new JButton(UIManager.getIcon("FileChooser.listViewIcon"));
        lastLogButton.setToolTipText("click to view/hide log viewer");
        lastLogPanel.add(lastLogButton, BorderLayout.WEST);

        lastLogTextField = new JTextField();
        lastLogTextField.setEditable(false);
        lastLogTextField.setHorizontalAlignment(JLabel.LEFT);
        lastLogTextField.setFont(ZooDirector.FONT_MONOSPACED);
        lastLogPanel.add(lastLogTextField, BorderLayout.CENTER);

        lastLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logDialog.setVisible(!logDialog.isVisible());
            }
        });

        this.add(lastLogPanel, BorderLayout.SOUTH);

        logDialog = new JDialog(new JFrame(), "Logs");
        logTextArea = new JTextArea(25, 100);
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(false);
        logTextArea.setFont(ZooDirector.FONT_MONOSPACED);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        logDialog.add(logScrollPane);
        logDialog.pack();
        logDialog.setLocationRelativeTo(SwingUtilities.getRoot(this));

        // TODO replace with better logging panel
        org.apache.log4j.Logger.getRootLogger().addAppender(new AppenderSkeleton() {
            @Override
            protected void append(LoggingEvent loggingEvent) {
                final Layout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p : %m%n");
                String line = layout.format(loggingEvent);
                logTextArea.append(line);
                lastLogTextField.setText(line);
            }

            @Override
            public void close() {
            }

            @Override
            public boolean requiresLayout() {
                return false;
            }
        });

        // Zookeeper View UI Setup

        rootNode = new DefaultMutableTreeNode(ZookeeperNode.root);
        treeModel = new DefaultTreeModel(rootNode);

        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        JPanel treePane = new JPanel(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane(tree);
        treePane.add(scrollPane, BorderLayout.CENTER);

        final JPopupMenu popupMenu = new JPopupMenu();

        addNodeMenuItem = new JMenuItem("add");
        addNodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNode(getSelectedNode());
            }
        });
        popupMenu.add(addNodeMenuItem);

        deleteNodeMenuItem = new JMenuItem("delete");
        deleteNodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteNode(getSelectedNode(), false);
            }
        });
        popupMenu.add(deleteNodeMenuItem);

        pruneNodeMenuItem = new JMenuItem("prune");
        pruneNodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pruneNode(getSelectedNode());
            }
        });
        popupMenu.add(pruneNodeMenuItem);

        trimNodeMenuItem = new JMenuItem("trim");
        trimNodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                trimNode(getSelectedNode());
            }
        });
        popupMenu.add(trimNodeMenuItem);

        popupMenu.addSeparator();

        JMenuItem expandPathMenuItem = new JMenuItem("expand all");
        expandPathMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                expandAll(getSelectedNode());
            }
        });
        popupMenu.add(expandPathMenuItem);

        JMenuItem collapsePathMenuItem = new JMenuItem("collapse all");
        collapsePathMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                collapseAll(getSelectedNode());
            }
        });
        popupMenu.add(collapsePathMenuItem);

        popupMenu.addSeparator();

        addWatchMenuItem = new JMenuItem("add watch");
        addWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                watchPanel.addWatch(getZookeeperNodePath(getSelectedNode()));
            }
        });
        popupMenu.add(addWatchMenuItem);

        removeWatchMenuItem = new JMenuItem("remove watch");
        removeWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                watchPanel.removeWatch(getZookeeperNodePath(getSelectedNode()));
            }
        });
        popupMenu.add(removeWatchMenuItem);

        // Context menu for selected tree node
        MouseListener ml = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Select if row is clicked not just text
                int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                tree.setSelectionRow(row);
                if (SwingUtilities.isRightMouseButton(e)) {
                    DefaultMutableTreeNode selectedNode = getSelectedNode();
                    addNodeMenuItem.setEnabled(!offline);
                    deleteNodeMenuItem.setEnabled(!offline && !selectedNode.isRoot());
                    pruneNodeMenuItem.setEnabled(!offline && !selectedNode.isRoot());
                    trimNodeMenuItem.setEnabled(!offline && selectedNode.getChildCount() > 0);

                    boolean hasWatch = watchPanel.hasWatch(getZookeeperNodePath(selectedNode));
                    addWatchMenuItem.setEnabled(!offline && !hasWatch);
                    removeWatchMenuItem.setEnabled(!offline && hasWatch);

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };

        tree.addMouseListener(ml);
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                if (!offline) {
                    nodeEditPanel.setZookeeperPath(getZookeeperNodePath(getSelectedNode()));
                }
            }
        });

        tree.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                DefaultMutableTreeNode node = getSelectedNode();

                if (node == null) {
                    return;
                }

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DELETE:
                        deleteNode(node, e.isControlDown());
                        break;
                    case KeyEvent.VK_INSERT:
                        createNode(node);
                        break;
                    case KeyEvent.VK_MULTIPLY:
                        expandAll(node);
                        break;
                    case KeyEvent.VK_DIVIDE:
                        collapseAll(node);
                        break;
                    case KeyEvent.VK_W:
                        // Ctrl + (Shift) + W
                        if (e.isControlDown()) {
                            addWatch(getSelectedNode(), e.isShiftDown());
                        }
                        break;
                    case KeyEvent.VK_R:
                        // Ctrl + (Shift) + R
                        if (e.isControlDown()) {
                            removeWatch(getSelectedNode(), e.isShiftDown());
                        }
                        break;
                }
            }
        });

        tabbedPane = new JTabbedPane();
        nodeEditPanel = new ZookeeperNodeEditPanel();
        tabbedPane.add(nodeEditPanel, "View/Edit");

        watchPanel = new ZookeeperWatchPanel(this);
        tabbedPane.add(watchPanel, "Watches");

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePane, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(200);

        createdPaths = new HashSet<String>();

        connectionWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                client = CuratorFrameworkFactory.newClient(
                        ZookeeperPanel.this.connectionString,
                        new RetryOneTime(ZookeeperPanel.this.connectionRetryPeriod)
                );
                client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
                    @Override
                    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
                        switch (connectionState) {
                            case LOST:
                            case SUSPENDED:
                                offline = true;
                                nodeEditPanel.setOffline();
                                logger.warn("connection to {} has been " + (connectionState == ConnectionState.LOST ? "lost" : "suspended") +
                                        ". Attempts will be made to reestablish the connection", ZookeeperPanel.this.connectionString);
                                break;
                            case RECONNECTED:
                                offline = false;
                                logger.info("connection to {} has been reestablished", ZookeeperPanel.this.connectionString);
                            default:
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        load();
                                    }
                                });
                        }
                    }
                });

                // Responsible for managing all tree additions and removals.
                zookeeperSync = new ZookeeperSync(client);
                zookeeperSync.addListener(new ZookeeperSync.Listener() {
                    @Override
                    public void process(final ZookeeperSync.Event e) {
                        final boolean created;
                        synchronized (createdPaths) {
                            created = createdPaths.remove(e.path);
                        }
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                switch (e.type) {
                                    case add:
                                        addNodeToTree(e.path, created);
                                        break;
                                    case delete:
                                        removeNodeFromTree(e.path);
                                        break;
                                }
                            }
                        });
                    }
                });

                watchPanel.setZookeeperSync(zookeeperSync);
                nodeEditPanel.setZookeeperSync(zookeeperSync);
                logger.info("connecting to cluster {}", ZookeeperPanel.this.connectionString);
                client.start();
                return null;
            }

            @Override
            public void done() {
                if (this.isCancelled()) {
                    logger.info("connection request cancelled");
                    close();
                    return;
                }
            }
        };
    }

    /**
     * Get the selected tree node.
     *
     * @return selected tree node containing helpful ZookeeperNode object
     */
    private DefaultMutableTreeNode getSelectedNode() {
        TreePath currentSelection = tree.getSelectionPath();

        if (currentSelection == null)
            return null;

        return (DefaultMutableTreeNode) currentSelection.getLastPathComponent();
    }

    protected DefaultMutableTreeNode getNodeFromPath(String path) {
        DefaultMutableTreeNode parent = rootNode;

        if (path.equals("/"))
            return parent;

        String[] segments = path.substring(1).split("/");

        boolean foundParent = true;

        for (int i = 0; foundParent && i < segments.length; ++i) {

            String segment = segments[i];

            foundParent = false;

            for (int j = 0; j < parent.getChildCount(); ++j) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(j);
                if (segment.equals(child.toString())) {

                    // If we have found the path, remove it.
                    if (i == segments.length - 1) {
                        return child;
                    }

                    // Keep searching down path.
                    foundParent = true;
                    parent = child;
                    break;
                }
            }
        }

        return null;
    }

    private void selectTreeNode(DefaultMutableTreeNode node) {
        TreePath treePath = getTreePath(node);
        tree.setSelectionPath(treePath);
        tree.scrollPathToVisible(treePath);
    }

    public DefaultMutableTreeNode selectTreeNode(String path) {
        DefaultMutableTreeNode target = getNodeFromPath(path);
        if (target != null) {
            selectTreeNode(target);
        }
        return target;
    }

    public void viewEditTreeNode(String path) {
        DefaultMutableTreeNode target = selectTreeNode(path);
        if (target != null) {
            tabbedPane.setSelectedIndex(0);
        }
    }

    /**
     * Add the given path as a node on the tree in sorted order.
     *
     * @param path
     */
    protected void addNodeToTree(String path, boolean select) {
        // Ignore root
        if ("/".equals(path)) {
            return;
        }

        DefaultMutableTreeNode parent = rootNode;

        String[] segments = path.substring(1).split("/");

        // Ensure a node exists for all segments of the path.
        for (int i = 0; i < segments.length; ++i) {

            String segment = segments[i];

            DefaultMutableTreeNode node = null;

            int insertAt = 0;

            // Add to tree in sorted order.
            for (int j = 0; j < parent.getChildCount(); ++j) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(j);
                if (segment.compareTo(child.toString()) > 0) {
                    insertAt = j + 1;
                } else if (segment.equals(child.toString())) {
                    node = (DefaultMutableTreeNode) parent.getChildAt(j);
                    break;
                }
            }

            if (node == null) {
                node = new DefaultMutableTreeNode(ZookeeperNode.create(getZookeeperNodePath(parent), segment));
                treeModel.insertNodeInto(node, parent, insertAt);
            }

            parent = node;
        }

        if (select) {
            selectTreeNode(parent);
        }
    }

    /**
     * Remove the given path from the tree if it exists.
     *
     * @param path
     */
    protected void removeNodeFromTree(String path) {
        DefaultMutableTreeNode target = getNodeFromPath(path);
        if (target != null && target != rootNode)
            treeModel.removeNodeFromParent(target);
    }

    /**
     * Prune the branch the node is on. This call will delete the node plus all ancestors with only nodes on this path.
     *
     * @param node
     * @throws Exception
     */
    private void pruneNode(DefaultMutableTreeNode node) {
        String path = getZookeeperNodePath(node);

        int option = showYesNoDialog(
                "Prune Node: " + path,
                "Are you sure you want to prune this nodes and all its lonely ancestors?");

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // TODO run on SwingWorker or use ZK Background
            zookeeperSync.prune(path);
        } catch (Exception e) {
            logger.error("prune {} failed [{}]", path, e);
        }
    }

    /**
     * Delete input nodes children from zookeeper and tree with optional user confirmation.
     *
     * @param node node to have children delete for
     */
    private void trimNode(DefaultMutableTreeNode node) {
        String path = getZookeeperNodePath(node);

        int option = showYesNoDialog(
                "Delete Children: " + path,
                "Are you sure you want to delete this nodes children and all its lovely descendants?");

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // TODO run on SwingWorker or use ZK Background
            zookeeperSync.trim(path);
        } catch (Exception e) {
            logger.error("trim {} failed [{}]", path, e);
        }
    }

    /**
     * Delete the input node (and all children) from zookeeper and the tree with optional user confirmation.
     *
     * @param node             node to be deleted
     * @param skipConfirmation if true user confirmation is bypassed
     */
    private void deleteNode(DefaultMutableTreeNode node, boolean skipConfirmation) {
        if (node.isRoot())
            return;

        String path = getZookeeperNodePath(node);

        if (!skipConfirmation) {
            int option = showYesNoDialog(
                    "Delete: " + node,
                    "Are you sure you want to delete this node" + (node.getChildCount() > 0 ? " and all of its lovely children?" : "?"));

            if (option != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            // TODO run on SwingWorker or use ZK Background
            zookeeperSync.delete(path);
        } catch (Exception e) {
            logger.error("delete {} failed [{}]", path, e);
        }
    }

    /**
     * Create a child node in zookeeper and add it to the tree based on user node name input.
     *
     * @param parent parent node to add child to
     */
    private void createNode(DefaultMutableTreeNode parent) {
        final Pattern BAD_PATH = Pattern.compile("(.*/\\s*/.*|/$)");
        final String badPathMessage = "Bad path. Cannot end with / or contain and empty or whitespace segments";

        String value = null;

        while (value == null || BAD_PATH.matcher(value).find()) {
            value = (String) JOptionPane.showInputDialog(
                    SwingUtilities.getRoot(this),
                    "Enter name for new node" + (value == null ? "" : "\n" + badPathMessage),
                    "Create",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    value);

            if (Strings.isNullOrEmpty(value))
                return;

            if (BAD_PATH.matcher(value).find()) {
                logger.error("create {} failed [{}]", value, badPathMessage);
            }
        }

        String path;

        // Either add as child or as absolute path
        if (value.startsWith("/")) {
            path = value;
        } else {
            String parentPath = getZookeeperNodePath(parent);
            path = ("/".equals(parentPath) ? "/" : (parentPath + "/")) + value;
        }

        try {
            if (zookeeperSync.getStat(path) != null) {
                addNodeToTree(path, true);
                return;
            }
        } catch (Exception e) {
            logger.error("create {} failed [{}]", path, e);
        }

        synchronized (createdPaths) {
            createdPaths.add(path);
        }

        try {
            // TODO run on SwingWorker or use ZK Background
            zookeeperSync.create(path);
        } catch (Exception e) {
            synchronized (createdPaths) {
                createdPaths.remove(path);
            }
            logger.error("create {} failed [{}]", path, e);
        }
    }

    /**
     * Load tree from zookeeper and display panel.
     */
    private void load() {
        if (!offline) {
            logger.info("loading zookeeper nodes");
            mainPanel.removeAll();
            rootNode.removeAllChildren();
            treeModel.reload();
            SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        zookeeperSync.watch();
                    } catch (Exception e) {
                        logger.error("Failed to execute ZookeeperSync watch [{}]", e);
                    }
                    return null;
                }

                @Override
                protected void done() {
                    logger.info("loading zookeeper nodes complete");
                }
            };
            swingWorker.execute();
            mainPanel.add(splitPane, BorderLayout.CENTER);
            refresh();
            tree.grabFocus();
        }
        tree.expandPath(getTreePath(rootNode));
        tree.setSelectionRow(0);
    }

    /**
     * Add watch for the given node's zookeeper path.
     *
     * @param node      node to add watch to
     * @param recursive if set watches for all descendant nodes will be created (if they do not already exist)
     */
    private void addWatch(DefaultMutableTreeNode node, boolean recursive) {
        watchPanel.addWatch(getZookeeperNodePath(node));
        if (recursive) {
            for (int i = 0; i < node.getChildCount(); ++i) {
                addWatch((DefaultMutableTreeNode) node.getChildAt(i), true);
            }
        }
    }

    /**
     * Remove watch for the given node's zookeeper path.
     *
     * @param node      node to remove watch from
     * @param recursive if true watches for all descendant nodes will be removed (if they exist)
     */
    private void removeWatch(DefaultMutableTreeNode node, boolean recursive) {
        watchPanel.removeWatch(getZookeeperNodePath(node));
        if (recursive) {
            for (int i = 0; i < node.getChildCount(); ++i) {
                removeWatch((DefaultMutableTreeNode) node.getChildAt(i), true);
            }
        }
    }

    /**
     * Expand all nodes under node
     *
     * @param node
     */
    private void expandAll(DefaultMutableTreeNode node) {
        Enumeration<DefaultMutableTreeNode> childNodes = node.breadthFirstEnumeration();
        while (childNodes.hasMoreElements()) {
            tree.expandPath(getTreePath(childNodes.nextElement()));
        }
    }

    /**
     * Collapse all nodes under node
     *
     * @param node
     */
    private void collapseAll(DefaultMutableTreeNode node) {
        Enumeration<DefaultMutableTreeNode> childNodes = node.depthFirstEnumeration();
        while (childNodes.hasMoreElements()) {
            tree.collapsePath(getTreePath(childNodes.nextElement()));
        }
    }

    /**
     * Refresh UI
     */
    private void refresh() {
        this.revalidate();
        this.repaint();
    }

    /**
     * Start curator client
     */
    public void connect() {
        // Loading UI Setup

        JPanel loadingPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        c.gridwidth = 1;
        c.insets.top = c.insets.bottom = 5;
        c.gridy = 0;
        loadingPanel.add(new JLabel("Establishing connection to zookeeper @ " + connectionString), c);

        c.gridy += 1;
        JButton cancelConnectionButton = new JButton("Cancel");
        cancelConnectionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectionWorker.cancel(true);
                close();
            }
        });
        loadingPanel.add(cancelConnectionButton, c);
        mainPanel.add(loadingPanel, BorderLayout.CENTER);

        refresh();

        connectionWorker.execute();
    }

    /**
     * Close curator client
     */
    public void close() {
        connectionWorker.cancel(true);
        client.close();
        mainPanel.removeAll();
        refresh();
    }

    /**
     * Simple class to represent zookeeper node path/name
     */
    public static class ZookeeperNode {
        public final static ZookeeperNode root = new ZookeeperNode("/");

        private final String name;
        public final String path;

        private ZookeeperNode(String path) {
            super();
            this.path = path;
            String[] subPaths = path.split("/");
            if (subPaths.length > 1) {
                name = subPaths[subPaths.length - 1];
            } else {
                name = "";
            }
        }

        public static ZookeeperNode create(String parent, String name) {
            if ("/".equals(parent)) {
                return new ZookeeperNode('/' + name);
            }
            return new ZookeeperNode(parent + '/' + name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Object[] YES_NO = {"Yes", "No"};

    int showYesNoDialog(String title, String message) {
        return JOptionPane.showOptionDialog(
                SwingUtilities.getRoot(this),
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                YES_NO,
                JOptionPane.YES_OPTION);
    }
}