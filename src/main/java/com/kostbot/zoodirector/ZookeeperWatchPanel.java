package com.kostbot.zoodirector;

import com.google.common.base.Strings;
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
import java.util.regex.PatternSyntaxException;

/**
 * @author Craig Kost
 */
public class ZookeeperWatchPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperWatchPanel.class);

    private final ZookeeperSync zookeeperSync;

    private final JTextField pathTextField;
    private final DefaultTableModel patternTableModel;
    private final JXTable patternWatchTable;

    private final Set<String> watches;
    private final DefaultTableModel tableModel;
    private final JXTable watchTable;

    public ZookeeperWatchPanel(ZookeeperSync zookeeperSync) {
        this.zookeeperSync = zookeeperSync;

        zookeeperSync.addListener(new ZookeeperSync.Listener() {
            @Override
            public void process(ZookeeperSync.Event e) {
                updateData(e.path, e.type == ZookeeperSync.Event.Type.delete);
            }
        });

        watches = new HashSet<String>(10);

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
        pathTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    addPatternWatch();
                }
            }
        });
        add(pathTextField, c);

        c.gridx += 1;
        c.weightx = 0;
        JButton addButton = new JButton("Add Pattern");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addPatternWatch();
            }
        });
        add(addButton, c);

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy += 1;
        c.gridwidth = 2;
        c.weighty = 0.25;

        patternTableModel = new DefaultTableModel(new String[]{"pattern"}, 0);

        patternWatchTable = new JXTable(patternTableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        patternWatchTable.setFont(ZooDirector.FONT_MONOSPACED);
        patternWatchTable.setHorizontalScrollEnabled(true);
        add(new JScrollPane(patternWatchTable), c);

        final JPopupMenu watchPatternTableMenu = new JPopupMenu();

        JMenuItem removePatternWatchMenuItem = new JMenuItem("Remove");
        removePatternWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                synchronized (patternTableModel) {
                    if (patternWatchTable.getSelectedRowCount() > 0) {
                        int[] rows = patternWatchTable.getSelectedRows();
                        for (int i = rows.length - 1; i >= 0; --i) {
                            patternTableModel.removeRow(rows[i]);
                        }
                    }
                }
            }
        });

        watchPatternTableMenu.add(removePatternWatchMenuItem);

        patternWatchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    // Show context menu if clicking selected row
                    if (patternWatchTable.isRowSelected(patternWatchTable.rowAtPoint(e.getPoint()))) {
                        watchPatternTableMenu.show(patternWatchTable, e.getX(), e.getY());
                    }
                }
            }
        });

        c.gridwidth = 2;
        c.weightx = 1.0;
        c.weighty = 0.75;
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

        final JPopupMenu tableMenu = new JPopupMenu();

        JMenuItem removeWatchMenuItem = new JMenuItem("Remove");
        removeWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (watchTable.getSelectedRowCount() > 0) {
                    int[] rows = watchTable.getSelectedRows();
                    for (int i = rows.length - 1; i >= 0; --i) {
                        removeWatch(rows[i]);
                    }
                }
            }
        });

        tableMenu.add(removeWatchMenuItem);

        watchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    // Show context menu if clicking selected row
                    if (watchTable.isRowSelected(watchTable.rowAtPoint(e.getPoint()))) {
                        tableMenu.show(watchTable, e.getX(), e.getY());
                    }
                }
            }
        });
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
        if (watches.contains(path)) {
            int row = getRow(path);

            if (row < 0) {
                return;
            }

            if (deleted) {
                logger.info("[watch] {} deleted", path);
                setData(row, null, null);
            } else {
                logger.info("[watch] {} updated", path);
                try {
                    Stat stat = zookeeperSync.getStat(path);
                    byte[] data = null;

                    if (stat != null) {
                        data = zookeeperSync.getData(path);
                    }
                    setData(row, stat, data);
                } catch (Exception e) {
                    logger.error("[watch] {} update failed [{}]", path, e.getMessage());
                }
            }
        } else if (!deleted) {
            synchronized (patternTableModel) {
                for (int i = 0; i < patternTableModel.getRowCount(); ++i) {
                    Pattern pattern = (Pattern) patternTableModel.getValueAt(i, 0);
                    if (pattern.matcher(path).matches()) {
                        addWatch(path);
                    }
                }
            }
        }
    }

    synchronized private void removeWatch(String path, int row) {
        logger.info("{} watch removed", path);
        tableModel.removeRow(row);
        watches.remove(path);
    }

    private void removeWatch(int row) {
        String path = (String) tableModel.getValueAt(row, 0);
        removeWatch(path, row);
    }

    public void removeWatch(String path) {
        if (watches.contains(path)) {
            int row = getRow(path);

            if (row < 0) {
                return;
            }

            removeWatch(path, row);
        }
    }

    synchronized public boolean hasWatch(String path) {
        return watches.contains(path);
    }

    synchronized public void addWatch(String path) {
        if (!watches.contains(path)) {
            logger.info("{} watch added", path);
            watches.add(path);
            tableModel.addRow(new Object[]{path, null, null, null, null, null});
            try {
                updateData(path, zookeeperSync.getStat(path) == null);
            } catch (Exception e) {
                logger.error("[watch] {} add failed [{}]", path, e);
            }
        }
    }

    private void addPatternWatch() {
        String watchPattern = pathTextField.getText();
        if (Strings.isNullOrEmpty(watchPattern)) {
            return;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(watchPattern);
        } catch (PatternSyntaxException e) {
            logger.error("bad wild card pattern [{}]", e.getMessage());
            return;
        }

        synchronized (patternTableModel) {
            // Scan to see if pattern already exists
            for (int i = 0; i < patternTableModel.getRowCount(); ++i) {
                if (((Pattern) patternTableModel.getValueAt(i, 0)).pattern().equals(watchPattern)) {
                    logger.debug("watch pattern {} already exists", watchPattern);
                    return;
                }
            }
            patternTableModel.addRow(new Object[]{pattern});
            patternWatchTable.packAll();
            for (String node : zookeeperSync.getNodes()) {
                if (pattern.matcher(node).matches()) {
                    addWatch(node);
                }
            }
        }
        pathTextField.setText("");
    }
}
