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
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import java.util.regex.Pattern;

public class ZookeeperPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperPanel.class);

    private final CuratorFramework client;

    // Main UI
    private JPanel mainPanel;

    private final JDialog logDialog;
    private final JTextArea logTextArea;
    private final JTextField lastLogTextField;

    // Loading UI
    private JPanel loadingPanel;

    // Zookeeper View UI
    private final JSplitPane splitPane;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final DefaultMutableTreeNode rootNode;

    private final JMenuItem addNodeMenuItem;
    private final JMenuItem deleteNodeMenuItem;
    private final JMenuItem trimNodeMenuItem;
    private final JMenuItem pruneNodeMenuItem;

    private final JMenuItem addWatchMenuItem;
    private final JMenuItem removeWatchMenuItem;

    private final ZookeeperNodeEditPanel nodeEditPanel;
    private final ZookeeperWatchPanel watchPanel;

    private SwingWorker<Void, Void> connectionWorker;

    /**
     * Helper method for extracting ZookeeperNode from tree node's user object.
     *
     * @param node tree node to extract ZookeeperNode instance from
     * @return ZookeeperNode instance of tree node
     */
    private static ZookeeperNode getZookeeperNode(DefaultMutableTreeNode node) {
        if (node == null)
            return null;
        return (ZookeeperNode) node.getUserObject();
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
     * Panel used for editing specified zookeeper node
     *
     * @param connectionString      zookeeper connection string
     * @param connectionRetryPeriod time to sleep between retries
     */
    public ZookeeperPanel(final String connectionString, int connectionRetryPeriod) {
        client = CuratorFrameworkFactory.newClient(connectionString, new RetryOneTime(connectionRetryPeriod));
        client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
            @Override
            public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
                switch (connectionState) {
                    case LOST:
                    case SUSPENDED:
                        logger.warn("connection to {} has been " + (connectionState == ConnectionState.LOST ? "lost" : "suspended") +
                                ". Attempts will be made to reestablish the connection", connectionString);
                        break;
                    case RECONNECTED:
                        logger.info("connection to {} has been reestablished", connectionString);
                    default:
                        load();
                }
            }
        });

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
        logDialog.setLocationRelativeTo(null);

        // TODO replace with better logging panel
        org.apache.log4j.Logger.getRootLogger().addAppender(new AppenderSkeleton() {
            @Override
            protected void append(LoggingEvent loggingEvent) {
                final Layout layout = new PatternLayout("%-5p : %m%n");
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

        // Loading UI Setup

        loadingPanel = new JPanel(new GridBagLayout());
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

        // Zookeeper View UI Setup

        rootNode = new DefaultMutableTreeNode(ZookeeperNode.root);
        treeModel = new DefaultTreeModel(rootNode);

        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F5) {
                    load();
                }
            }
        });

        JPanel treePane = new JPanel(new BorderLayout());
        JButton syncButton = new JButton("Sync");

        syncButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                load();
            }
        });

        treePane.add(syncButton, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(tree);
        treePane.add(scrollPane, BorderLayout.CENTER);

        final JPopupMenu popupMenu = new JPopupMenu();

        addNodeMenuItem = new JMenuItem("add");
        addNodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createChildNode(getSelectedNode());
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
                watchPanel.addWatch(getZookeeperNode(getSelectedNode()).path);
            }
        });
        popupMenu.add(addWatchMenuItem);

        removeWatchMenuItem = new JMenuItem("remove watch");
        removeWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                watchPanel.removeWatch(getZookeeperNode(getSelectedNode()).path);
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
                    deleteNodeMenuItem.setEnabled(!selectedNode.isRoot());
                    pruneNodeMenuItem.setEnabled(!selectedNode.isRoot());
                    trimNodeMenuItem.setEnabled(selectedNode.getChildCount() > 0);

                    boolean hasWatch = watchPanel.hasWatch(getZookeeperNode(selectedNode).path);
                    addWatchMenuItem.setEnabled(!hasWatch);
                    removeWatchMenuItem.setEnabled(hasWatch);

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };

        tree.addMouseListener(ml);
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                ZookeeperNode zookeeperNode = getZookeeperNode(getSelectedNode());
                if (zookeeperNode == null)
                    nodeEditPanel.setZookeeperPath(null);
                else
                    nodeEditPanel.setZookeeperPath(zookeeperNode.path);
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
                        createChildNode(node);
                        break;
                    case KeyEvent.VK_MULTIPLY:
                        expandAll(node);
                        break;
                    case KeyEvent.VK_DIVIDE:
                        collapseAll(node);
                        break;
                    case KeyEvent.VK_W:
                        addWatch(getSelectedNode(), e.isControlDown());
                        break;
                    case KeyEvent.VK_R:
                        removeWatch(getSelectedNode(), e.isControlDown());
                        break;
                }
            }
        });

        JTabbedPane tabbedPane = new JTabbedPane();
        nodeEditPanel = new ZookeeperNodeEditPanel(client);
        tabbedPane.add(nodeEditPanel, "View/Edit");

        watchPanel = new ZookeeperWatchPanel(client);
        tabbedPane.add(watchPanel, "Watches");

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePane, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(150);

        connectionWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                logger.info("connecting to cluster {}", connectionString);
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

        connectionWorker.execute();
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

    /**
     * Helper method for loading children from zookeeper into the tree.
     *
     * @param parent parent of child to be added to the tree
     * @param child  child to be added to the tree
     * @throws Exception
     */
    private void loadChild(DefaultMutableTreeNode parent, String child) throws Exception {
        ZookeeperNode childNode = ZookeeperNode.create(getZookeeperNode(parent), child);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(childNode);
        addNodeToTree(parent, node, false);
        logger.debug("loaded {}", childNode.path);
        for (String childName : client.getChildren().forPath(childNode.path)) {
            loadChild(node, childName);
        }
    }

    /**
     * Helper method for consistent logging of delete failures.
     *
     * @param node node which failed to be deleted
     * @param e    exception caught
     */
    private void deleteFailed(DefaultMutableTreeNode node, Exception e) {
        logger.error("delete {} failed [{}]", getZookeeperNode(node).path, e.getMessage());
    }

    /**
     * Delete node and optionally children from zookeeper and remove it/them from the tree.
     *
     * @param node      node to be deleted
     * @param recursive if true recursively delete all sub nodes
     * @throws Exception if an error occurred deleting node or it's children
     */
    private void deleteNodeInner(DefaultMutableTreeNode node, boolean recursive) throws Exception {

        if (recursive) {
            // Delete only visible nodes (there may be nodes in zookeeper that still exist)
            for (int i = node.getChildCount() - 1; i >= 0; --i) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
                try {
                    deleteNodeInner(childNode, true);
                } catch (KeeperException.BadArgumentsException e) {
                    deleteFailed(childNode, e);
                }
            }
        }

        String path = getZookeeperNode(node).path;
        if (client.checkExists().forPath(path) != null) {
            client.delete().forPath(path);
            logger.info("deleted {}", path);
        } else {
            logger.warn("deleted {} (did not exist in zookeeper)", path);
        }
        treeModel.removeNodeFromParent(node);
    }

    /**
     * Prune the branch the node is on. This call will delete the node plus all ancestors with only nodes on this path.
     *
     * @param node
     * @throws Exception
     */
    private void pruneNode(DefaultMutableTreeNode node) {
        int option = JOptionPane.showConfirmDialog(
                SwingUtilities.getRoot(this),
                "Are you sure you want to prune this nodes and all its lonely ancestors?",
                "Prune Node: " + getZookeeperNode(node).path,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        // Determine which parents will be included in the prune
        while (node.getParent() != rootNode && node.getParent().getChildCount() == 1) {
            node = (DefaultMutableTreeNode) node.getParent();
        }

        try {
            deleteNodeInner(node, true);
            // Set selected node to parent
            TreePath parentPath = getTreePath(node).getParentPath();
            if (parentPath == null) {
                tree.setSelectionPath(getTreePath(rootNode));
            } else {
                tree.setSelectionPath(parentPath);
            }
            tree.grabFocus();
        } catch (Exception e) {
            deleteFailed(node, e);
        }
    }

    /**
     * Delete input nodes children from zookeeper and tree with optional user confirmation.
     *
     * @param node node to have children delete for
     */
    private void trimNode(DefaultMutableTreeNode node) {
        int option = JOptionPane.showConfirmDialog(
                SwingUtilities.getRoot(this),
                "Are you sure you want to delete this nodes children and all its lovely descendants?",
                "Delete Children: " + getZookeeperNode(node).path,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        for (int i = node.getChildCount() - 1; i >= 0; --i) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            try {
                deleteNodeInner(childNode, true);
            } catch (Exception e) {
                deleteFailed(childNode, e);
            }
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

        if (!skipConfirmation) {
            int option = JOptionPane.showConfirmDialog(
                    SwingUtilities.getRoot(this),
                    "Are you sure you want to delete this node" + (node.getChildCount() > 0 ? " and all of its lovely children?" : "?"),
                    "Delete: " + getZookeeperNode(node).path,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (option != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            TreePath parentPath = getTreePath(node).getParentPath();
            deleteNodeInner(node, true);
            // Set selected node to parent
            tree.setSelectionPath(parentPath);
            tree.grabFocus();
        } catch (Exception e) {
            deleteFailed(node, e);
        }
    }

    /**
     * Add node to zookeeper and to the tree in sorted order while preventing duplicate child nodes. Optionally sets the
     * focus to the newly added node or the node that already exists with the same name.
     *
     * @param parent   parent node to add child to
     * @param node     new node to be added (if node of same name does not already exist)
     * @param setFocus determine if focus should be set set to new node
     */
    private void addNodeToTree(DefaultMutableTreeNode parent, DefaultMutableTreeNode node, boolean setFocus) {
        boolean alreadyExists = false;
        int insertAt = 0;

        for (int i = 0; i < parent.getChildCount(); ++i) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (node.toString().compareTo(child.toString()) > 0) {
                insertAt = i + 1;
            } else if (node.toString().equals(child.toString())) {
                alreadyExists = true;
                node = (DefaultMutableTreeNode) parent.getChildAt(i);
            }
        }
        if (!alreadyExists) {
            treeModel.insertNodeInto(node, parent, insertAt);
        }

        if (setFocus) {
            TreePath childPath = getTreePath(node);
            tree.scrollPathToVisible(childPath);
            tree.setSelectionPath(childPath);
            tree.grabFocus();
        }
    }

    /**
     * Add node to zookeeper and to the tree in sorted order while preventing duplicate child nodes. Sets the focus to
     * the newly added node or the node that already exists with the same name.
     *
     * @param parent parent node to add child to
     * @param node   new node to be added (if node of same name does not already exist)
     */
    private void addNodeToTree(DefaultMutableTreeNode parent, DefaultMutableTreeNode node) {
        addNodeToTree(parent, node, true);
    }

    /**
     * Create a child node in zookeeper and add it to the tree based on user node name input.
     *
     * @param parent parent node to add child to
     */
    private void createChildNode(DefaultMutableTreeNode parent) {
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

        if (value.startsWith("/")) {
            parent = rootNode;
            value = value.substring(1);
        }

        String[] paths = value.split("/");
        ZookeeperNode child = getZookeeperNode(parent);
        DefaultMutableTreeNode parentNode = parent;

        for (String subPath : paths) {
            subPath = subPath.trim();

            DefaultMutableTreeNode childNode = null;

            Enumeration<DefaultMutableTreeNode> children = parentNode.children();
            while (children.hasMoreElements()) {
                DefaultMutableTreeNode childNodeCandidate = children.nextElement();
                if (childNodeCandidate.toString().equals(subPath)) {
                    childNode = childNodeCandidate;
                    break;
                }
            }

            child = ZookeeperNode.create(child, subPath);

            if (childNode == null) {
                try {
                    boolean existedInZookeeper = false;
                    if (client.checkExists().forPath(child.path) == null) {
                        try {
                            client.create().forPath(child.path);
                            logger.info("created {}", child.path);
                        } catch (KeeperException.NodeExistsException e) {
                            existedInZookeeper = true;
                        }
                    } else {
                        existedInZookeeper = true;
                    }

                    if (existedInZookeeper) {
                        logger.warn("created {} (already existed in zookeeper)", child.path);
                    }
                } catch (Exception e) {
                    logger.error("create {} failed [{}]", child.path, e.getMessage());
                    return;
                }
                childNode = new DefaultMutableTreeNode(child);
            }

            addNodeToTree(parentNode, childNode);
            parentNode = childNode;
        }
    }

    /**
     * Load tree from zookeeper and display panel.
     */
    private void load() {
        mainPanel.removeAll();
        rootNode.removeAllChildren();
        treeModel.reload();
        try {
            for (String childName : client.getChildren().forPath(getZookeeperNode(rootNode).path)) {
                loadChild(rootNode, childName);
            }
        } catch (Exception e) {
            logger.error("Failed to load node tree: {}", e.getMessage());
        }
        mainPanel.add(splitPane, BorderLayout.CENTER);
        refresh();
        tree.expandPath(getTreePath(rootNode));
        tree.setSelectionRow(0);
        tree.grabFocus();
        logger.info("finished synchronizing node tree with zookeeper");
    }

    /**
     * Add watch for the given node's zookeeper path.
     *
     * @param node      node to add watch to
     * @param recursive if set watches for all descendant nodes will be created (if they do not already exist)
     */
    private void addWatch(DefaultMutableTreeNode node, boolean recursive) {
        ZookeeperNode zookeeperNode = getZookeeperNode(node);
        if (zookeeperNode != null) {
            watchPanel.addWatch(zookeeperNode.path);
            if (recursive) {
                Enumeration<DefaultMutableTreeNode> childNodes = node.breadthFirstEnumeration();
                while (childNodes.hasMoreElements()) {
                    addWatch(childNodes.nextElement(), false);
                }
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
        ZookeeperNode zookeeperNode = getZookeeperNode(node);
        if (zookeeperNode != null) {
            watchPanel.removeWatch(zookeeperNode.path);
            if (recursive) {
                Enumeration<DefaultMutableTreeNode> childNodes = node.breadthFirstEnumeration();
                while (childNodes.hasMoreElements()) {
                    removeWatch(childNodes.nextElement(), false);
                }
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

        public final String name;
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

        public static ZookeeperNode create(ZookeeperNode parent, String name) {
            if (parent == root) {
                return new ZookeeperNode('/' + name);
            }
            return new ZookeeperNode(parent.path + '/' + name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}