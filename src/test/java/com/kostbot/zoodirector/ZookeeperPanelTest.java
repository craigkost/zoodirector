package com.kostbot.zoodirector;

import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.*;
import java.util.Enumeration;

public class ZookeeperPanelTest {

    /**
     * Test that the tree has exactly the expected node count, excluding the root node.
     *
     * @param root
     * @param expectedCount
     */
    private void testTreeNodeCount(DefaultMutableTreeNode root, int expectedCount) {
        if (!root.isRoot()) {
            throw new IllegalArgumentException("root must be the root node");
        }

        Enumeration<DefaultMutableTreeNode> nodes = root.depthFirstEnumeration();

        int count = 0;

        while (nodes.hasMoreElements()) {
            nodes.nextElement();
            count++;
        }

        Assert.assertEquals(expectedCount, count - 1);
    }

    /**
     * Test the path exists given the root node. Also tests that all paths contain the correct zookeeper path and name
     * along the way.
     *
     * @param root
     * @param path
     */
    private void testPathExists(DefaultMutableTreeNode root, String path) {
        if (!root.isRoot()) {
            throw new IllegalArgumentException("root must be the root node");
        }

        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must be absolute. received: " + path);
        }

        String[] segments = path.substring(1).split("/");
        String subPath = "";
        DefaultMutableTreeNode parent = root;

        for (int i = 0; i < segments.length; ++i) {

            String segment = segments[i];
            subPath += "/" + segment;

            DefaultMutableTreeNode node = null;

            for (int j = 0; j < parent.getChildCount(); ++j) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(j);
                if (segment.equals(child.toString())) {
                    node = (DefaultMutableTreeNode) parent.getChildAt(j);

                    Assert.assertEquals(subPath, ZookeeperPanel.getZookeeperNodePath(node));

                    if (i == segments.length) {
                        // The path exists!
                        return;
                    }

                    // The path continues to exist!
                    break;
                }
            }

            Assert.assertNotNull(node);

            parent = node;
        }

    }

    @Test
    public void testAddNodeToTree() {
        ZookeeperPanel zookeeperPanel = new ZookeeperPanel("localhost", 1000);

        String path1 = "/test/all/path/segments/are/added/to/tree";
        String path2 = path1 + "/but/only/once";

        zookeeperPanel.addNodeToTree(path1, true);
        zookeeperPanel.addNodeToTree(path2, false);

        DefaultMutableTreeNode root = zookeeperPanel.rootNode;

        testPathExists(root, path1);
        Assert.assertEquals(path1, ZookeeperPanel.getZookeeperNodePath((DefaultMutableTreeNode) zookeeperPanel.tree.getSelectionPath().getLastPathComponent()));

        testPathExists(root, path2);
        testTreeNodeCount(root, 11);
    }

    @Test
    public void testRemoveNodeFromTree() {
        ZookeeperPanel zookeeperPanel = new ZookeeperPanel("localhost", 1000);

        String base = "/test/delete/removes";
        String path = base + "/all";

        zookeeperPanel.addNodeToTree(path + "/child/segments", false);

        DefaultMutableTreeNode root = zookeeperPanel.rootNode;

        // Cannot remove root
        zookeeperPanel.removeNodeFromTree("/");
        testTreeNodeCount(root, 6);

        // Removes children
        zookeeperPanel.removeNodeFromTree(path);
        testPathExists(root, base);
        testTreeNodeCount(root, 3);

        // Removes all
        zookeeperPanel.removeNodeFromTree("/test");
        testTreeNodeCount(root, 0);
    }
}
