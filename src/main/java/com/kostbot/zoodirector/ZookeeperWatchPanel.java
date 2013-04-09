package com.kostbot.zoodirector;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.data.Stat;
import org.jdesktop.swingx.JXTable;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Craig Kost
 */
public class ZookeeperWatchPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperWatchPanel.class);

    private CuratorFramework client;
    private CuratorWatcher watcher;

    private final JTextField pathTextField;
    private final DefaultTableModel tableModel;
    private final Set<String> watchMap;
    private final JXTable watchTable;

    public ZookeeperWatchPanel(CuratorFramework client) {
        this.client = client;

        watcher = new CuratorWatcher() {
            @Override
            public void process(WatchedEvent event) throws Exception {
                String path = event.getPath();

                switch (event.getType()) {
                    case NodeDataChanged:
                        updateData(path, false);
                        break;
                    case NodeDeleted:
                        updateData(path, true);
                        break;
                }
            }
        };

        watchMap = new HashSet<String>(10);

        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = c.gridy = 0;
        c.weightx = 0.5;
        c.insets.top = 5;
        c.insets.left = 5;
        c.insets.right = 5;
        c.insets.bottom = 2;
        c.fill = GridBagConstraints.HORIZONTAL;

        pathTextField = new JTextField();
        add(pathTextField, c);

        c.gridx += 1;
        c.weightx = 0;
        JButton addButton = new JButton("Add");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final Pattern BAD_PATH = Pattern.compile("(.*/\\s*/.*|/$)");
                String path = pathTextField.getText();
                if (!BAD_PATH.matcher(path).find()) {
                    addWatch(path);
                }
            }
        });
        add(addButton, c);

        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 2;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.gridx = 0;
        c.gridy += 1;
        tableModel = new DefaultTableModel(new String[]{"path", "ephemeral", "created", "modified", "version", "data"}, 0);

        watchTable = new JXTable(tableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        watchTable.setFont(ZooDirector.FONT_MONOSPACED);
        watchTable.setHorizontalScrollEnabled(true);
        add(new JScrollPane(watchTable), c);
    }

    private void setData(int row, Stat stat, byte[] data) {
        tableModel.setValueAt(stat == null ? null : (stat.getEphemeralOwner() != 0), row, 1);
        tableModel.setValueAt(stat == null ? null : new LocalDateTime(stat.getCtime()), row, 2);
        tableModel.setValueAt(stat == null ? null : new LocalDateTime(stat.getMtime()), row, 3);
        tableModel.setValueAt(stat == null ? null : stat.getVersion(), row, 4);
        tableModel.setValueAt(data == null ? null : new String(data), row, 5);
        watchTable.packAll();
    }

    private int getRow(String path) {
        for (int i = 0; i < tableModel.getRowCount(); ++i) {
            if (tableModel.getValueAt(i, 0).equals(path)) {
                return i;
            }
        }

        // Should never get here
        logger.error("could not determine row for {}", path);
        return -1;
    }

    synchronized private void updateData(String path, boolean deleted) {
        if (watchMap.contains(path)) {
            int row = getRow(path);

            if (row < 0) {
                return;
            }

            if (deleted) {
                logger.info("node deleted {}", path);
                setData(row, null, null);
            } else {
                logger.info("node updated {}", path);
                try {
                    Stat stat = client.checkExists().usingWatcher(watcher).forPath(path);
                    byte[] data = null;

                    if (stat != null) {
                        data = client.getData().usingWatcher(watcher).forPath(path);
                    }
                    setData(row, stat, data);
                } catch (Exception e) {
                    logger.error("failed to updated {} data [{}]", path, e.getMessage());
                }
            }
        }
    }

    private void addRow(String path, Stat stat, byte[] data) {
        logger.info("added watch for {}", path);
        tableModel.addRow(new Object[]{
                path,
                stat == null ? null : (stat.getEphemeralOwner() != 0),
                stat == null ? null : new LocalDateTime(stat.getCtime()),
                stat == null ? null : new LocalDateTime(stat.getMtime()),
                stat == null ? null : stat.getVersion(),
                data == null ? null : new String(data)
        });
        watchTable.packAll();
    }

    synchronized public void removeWatch(String path) {
        if (watchMap.contains(path)) {
            int row = getRow(path);

            if (row < 0) {
                return;
            }

            logger.info("removed watch for {}", path);
            tableModel.removeRow(row);
            watchMap.remove(path);
        }
    }

    synchronized public boolean hasWatch(String path) {
        return watchMap.contains(path);
    }

    synchronized public void addWatch(String path) {
        if (!watchMap.contains(path)) {
            try {
                Stat stat = client.checkExists().usingWatcher(watcher).forPath(path);

                if (stat == null) {
                    logger.error("watch on non-existent path {} not supported", path);
                    return;
                }

                byte[] data = client.getData().usingWatcher(watcher).forPath(path);
                addRow(path, stat, data);
            } catch (Exception e) {
                logger.error("failed to add {} watch {}", path, e.getMessage());
            }
            watchMap.add(path);
        }
    }
}
