package com.kostbot.zoodirector.ui;

import com.kostbot.zoodirector.ui.helpers.UIUtils;
import com.kostbot.zoodirector.zookeepersync.ZookeeperSync;
import org.apache.zookeeper.CreateMode;
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

public class ZooDirectorNavPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirectorNavPanel.class);

    private final ZooDirectorPanel zooDirectorPanel;

    protected final DefaultTreeModel treeModel;
    protected final JTree tree;
    protected final DefaultMutableTreeNode rootNode;

    private final JMenuItem createNodeMenuItem;
    private final JMenuItem deleteNodeMenuItem;
    private final JMenuItem trimNodeMenuItem;
    private final JMenuItem pruneNodeMenuItem;

    private final JMenuItem addWatchMenuItem;
    private final JMenuItem removeWatchMenuItem;

    private final Set<String> createdPaths;

    public ZooDirectorNavPanel(ZooDirectorPanel zooDirectorPanel) {
        super(new BorderLayout());

        this.zooDirectorPanel = zooDirectorPanel;

        rootNode = new DefaultMutableTreeNode(ZookeeperNode.root);
        treeModel = new DefaultTreeModel(rootNode);

        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        JScrollPane scrollPane = new JScrollPane(tree);
        this.add(scrollPane, BorderLayout.CENTER);

        final JPopupMenu popupMenu = new JPopupMenu();

        createNodeMenuItem = new JMenuItem("create");
        createNodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNode(getSelectedNode());
            }
        });
        popupMenu.add(createNodeMenuItem);

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
                ZooDirectorNavPanel.this.zooDirectorPanel.addWatch(getZookeeperNodePath(getSelectedNode()));
            }
        });
        popupMenu.add(addWatchMenuItem);

        removeWatchMenuItem = new JMenuItem("remove watch");
        removeWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ZooDirectorNavPanel.this.zooDirectorPanel.removeWatch(getZookeeperNodePath(getSelectedNode()));
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

                    boolean isOnline = ZooDirectorNavPanel.this.zooDirectorPanel.isOnline();

                    createNodeMenuItem.setEnabled(isOnline);
                    deleteNodeMenuItem.setEnabled(isOnline && !selectedNode.isRoot());
                    pruneNodeMenuItem.setEnabled(isOnline && !selectedNode.isRoot());
                    trimNodeMenuItem.setEnabled(isOnline && selectedNode.getChildCount() > 0);

                    boolean hasWatch = ZooDirectorNavPanel.this.zooDirectorPanel.hasWatch(getZookeeperNodePath(selectedNode));
                    addWatchMenuItem.setEnabled(isOnline && !hasWatch);
                    removeWatchMenuItem.setEnabled(isOnline && hasWatch);

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };

        tree.addMouseListener(ml);
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                if (ZooDirectorNavPanel.this.zooDirectorPanel.isOnline()) {
                    String path = getZookeeperNodePath(getSelectedNode());
                    ZooDirectorNavPanel.this.zooDirectorPanel.viewEditTreeNode(path);
                }
            }
        });

        tree.addKeyListener(new KeyAdapter() {
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
                            addWatch(node, e.isShiftDown());
                        }
                        break;
                    case KeyEvent.VK_R:
                        // Ctrl + (Shift) + R
                        if (e.isControlDown()) {
                            removeWatch(node, e.isShiftDown());
                        }
                        break;
                }
            }
        });

        createdPaths = new HashSet<String>();
    }

    public boolean wasCreated(String path) {
        synchronized (createdPaths) {
            return createdPaths.remove(path);
        }
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

        if (path == null || path.equals("/")) {
            return parent;
        }

        if (!path.startsWith("/")) {
            return null;
        }

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

    /**
     * Add the given path as a node on the tree in sorted order.
     *
     * @param path
     */
    public void addNodeToTree(String path, boolean select) {
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
    public void removeNodeFromTree(String path) {
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
            selectTreeNode(zooDirectorPanel.getZookeeperSync().prune(path));
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
            zooDirectorPanel.getZookeeperSync().trim(path);
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
            zooDirectorPanel.getZookeeperSync().delete(path);
            selectTreeNode((DefaultMutableTreeNode) node.getParent());
        } catch (Exception e) {
            logger.error("delete {} failed [{}]", path, e);
        }
    }

    private static final CreateMode[] CREATE_MODES = new CreateMode[]{CreateMode.PERSISTENT, CreateMode.EPHEMERAL};

    /**
     * Create a child node in zookeeper and add it to the tree based on user node name input.
     *
     * @param parent parent node to add child to
     */
    private void createNode(DefaultMutableTreeNode parent) {

        JPanel inputPanel = new JPanel(new BorderLayout());

        JLabel messageLabel = new JLabel("Enter name or full path for new node");
        inputPanel.add(messageLabel, BorderLayout.NORTH);

        final JTextField pathTextField = new JTextField();
        UIUtils.highlightIfConditionMetOnUpdate(pathTextField, new UIUtils.Condition() {
            @Override
            public boolean isMet() {
                return ZookeeperSync.isValidPath(pathTextField.getText(), true);
            }
        });
        inputPanel.add(pathTextField, BorderLayout.CENTER);

        JComboBox<CreateMode> createModeComboBox = new JComboBox<CreateMode>(CREATE_MODES);
        inputPanel.add(createModeComboBox, BorderLayout.SOUTH);

        boolean isValid = false;
        String path = null;

        while (!isValid) {
            int result = JOptionPane.showConfirmDialog(
                    SwingUtilities.getRoot(this),
                    inputPanel,
                    "Create",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            path = pathTextField.getText();
            isValid = ZookeeperSync.isValidSubPath(path);
        }

        // Either add as child or as absolute path
        if (!path.startsWith("/")) {
            String parentPath = getZookeeperNodePath(parent);
            path = ("/".equals(parentPath) ? "/" : (parentPath + "/")) + path;
        }

        synchronized (createdPaths) {
            createdPaths.add(path);
        }

        try {
            // TODO run on SwingWorker or use ZK Background
            zooDirectorPanel.getZookeeperSync().create(path, (CreateMode) createModeComboBox.getSelectedItem());
        } catch (Exception e) {
            synchronized (createdPaths) {
                createdPaths.remove(path);
            }
            logger.error("create {} failed [{}]", path, e);
        }
    }

    /**
     * Add watch for the given node's zookeeper path.
     *
     * @param node      node to add watch to
     * @param recursive if set watches for all descendant nodes will be created (if they do not already exist)
     */
    private void addWatch(DefaultMutableTreeNode node, boolean recursive) {
        zooDirectorPanel.addWatch(getZookeeperNodePath(node));
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
        zooDirectorPanel.removeWatch(getZookeeperNodePath(node));
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

    @Override
    public void grabFocus() {
        tree.grabFocus();
    }

    public void removeAll() {
        rootNode.removeAllChildren();
        treeModel.reload();
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

    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final Object[] YES_NO = {YES, NO};

    int showYesNoDialog(String title, String message) {
        return JOptionPane.showOptionDialog(
                SwingUtilities.getRoot(this),
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                YES_NO,
                NO);
    }
}
