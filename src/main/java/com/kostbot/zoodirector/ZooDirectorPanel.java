package com.kostbot.zoodirector;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.state.ConnectionState;
import com.netflix.curator.framework.state.ConnectionStateListener;
import com.netflix.curator.retry.RetryOneTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;

public final class ZooDirectorPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirectorPanel.class);

    private ZookeeperSync zookeeperSync;
    private CuratorFramework client;

    private final String connectionString;
    private final int connectionRetryPeriod;

    private volatile boolean offline; // prevent operations if offline.

    // Main UI
    private final JPanel mainPanel;

    // Zookeeper View UI
    private final JSplitPane splitPane;

    private final ZooDirectorNavPanel zooDirectorNavPanel;
    private final ZooDirectorAddressPanel zooDirectorAddressPanel;

    private final JTabbedPane tabbedPane;
    private final ZooDirectorNodeEditPanel nodeEditPanel;
    private final ZooDirectorWatchPanel watchPanel;

    private final SwingWorker<Void, Void> connectionWorker;

    /**
     * Return the currently set connection string.
     *
     * @return connection string
     */
    public String getConnectionString() {
        return connectionString;
    }

    public ZookeeperSync getZookeeperSync() {
        return zookeeperSync;
    }

    /**
     * Panel used for editing specified zookeeper node
     *
     * @param connectionString      zookeeper connection string
     * @param connectionRetryPeriod time to sleep between retries
     */
    public ZooDirectorPanel(String connectionString, int connectionRetryPeriod) {
        this.connectionString = connectionString;
        this.connectionRetryPeriod = connectionRetryPeriod;

        this.setLayout(new BorderLayout());

        mainPanel = new JPanel(new BorderLayout());

        this.add(mainPanel, BorderLayout.CENTER);

        // Logging UI Setup
        ZooDirectorLogDialog zooDirectorLogDialog = new ZooDirectorLogDialog();
        this.add(zooDirectorLogDialog.getLastLogPanel(), BorderLayout.SOUTH);

        // Zookeeper View UI Setup
        zooDirectorAddressPanel = new ZooDirectorAddressPanel(this);
        zooDirectorNavPanel = new ZooDirectorNavPanel(this);

        tabbedPane = new JTabbedPane();
        nodeEditPanel = new ZooDirectorNodeEditPanel();
        tabbedPane.add(nodeEditPanel, "View/Edit");

        watchPanel = new ZooDirectorWatchPanel(this);
        tabbedPane.add(watchPanel, "Watches");

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, zooDirectorNavPanel, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(200);

        connectionWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                client = CuratorFrameworkFactory.newClient(
                        ZooDirectorPanel.this.connectionString,
                        new RetryOneTime(ZooDirectorPanel.this.connectionRetryPeriod)
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
                                        ". Attempts will be made to reestablish the connection", ZooDirectorPanel.this.connectionString);
                                break;
                            case RECONNECTED:
                                offline = false;
                                logger.info("connection to {} has been reestablished", ZooDirectorPanel.this.connectionString);
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
                        final boolean created = zooDirectorNavPanel.wasCreated(e.path);

                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                switch (e.type) {
                                    case add:
                                        zooDirectorNavPanel.addNodeToTree(e.path, created);
                                        break;
                                    case delete:
                                        zooDirectorNavPanel.removeNodeFromTree(e.path);
                                        break;
                                }
                            }
                        });
                    }
                });

                watchPanel.setZookeeperSync(zookeeperSync);
                nodeEditPanel.setZookeeperSync(zookeeperSync);
                logger.info("connecting to cluster {}", ZooDirectorPanel.this.connectionString);
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

    public boolean isOnline() {
        return !offline;
    }

    public boolean hasWatch(String path) {
        return watchPanel.hasWatch(path);
    }

    public void addWatch(String path) {
        watchPanel.addWatch(path);
    }

    public void removeWatch(String path) {
        watchPanel.removeWatch(path);
    }

    public void viewEditTreeNode(String path) {
        DefaultMutableTreeNode target = zooDirectorNavPanel.selectTreeNode(path);
        if (target == null) {
            logger.error("view/edit {} failed [path does not exist]", path);
        } else {
            nodeEditPanel.setZookeeperPath(path);
            zooDirectorAddressPanel.setPath(path);
            tabbedPane.setSelectedIndex(0);
            zooDirectorNavPanel.grabFocus();
        }
    }

    /**
     * Load tree from zookeeper and display panel.
     */
    private void load() {
        if (!offline) {
            logger.info("loading zookeeper nodes");
            mainPanel.removeAll();
            zooDirectorNavPanel.removeAll();
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
            mainPanel.add(zooDirectorAddressPanel, BorderLayout.NORTH);
            mainPanel.add(splitPane, BorderLayout.CENTER);
            refresh();
            zooDirectorNavPanel.grabFocus();
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
}