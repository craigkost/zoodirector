package com.kostbot.zoodirector;

import com.google.common.base.Strings;
import com.kostbot.zoodirector.helpers.DynamicTable;
import com.kostbot.zoodirector.workers.LoadDataWorker;
import org.apache.zookeeper.data.Stat;
import org.jdesktop.swingx.JXTable;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
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

    private ZookeeperSync zookeeperSync;

    private final JTextField pathTextField;
    private final DefaultTableModel patternTableModel;
    private final JXTable patternWatchTable;

    private final Set<String> watches;
    private final DefaultTableModel tableModel;
    private final JXTable watchTable;

    private int clickedRow = -1;

    public ZookeeperWatchPanel(final ZookeeperPanel parent) {
        watches = new HashSet<String>(10);

        setLayout(new BorderLayout());

        JPanel patternWatchPanel = new JPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = c.gridy = 0;
        c.weightx = 0.5;
        c.insets.top = 5;
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
        patternWatchPanel.add(pathTextField, c);

        c.gridx += 1;
        c.weightx = 0;
        JButton addButton = new JButton("Add Pattern");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addPatternWatch();
            }
        });
        patternWatchPanel.add(addButton, c);

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

        final JPopupMenu watchPatternTableMenu = new JPopupMenu();

        JMenuItem removePatternWatchMenuItem = new JMenuItem("remove watch pattern");
        removePatternWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                synchronized (patternTableModel) {
                    DynamicTable.removeSelectedRows(patternWatchTable);
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

        patternWatchPanel.add(new JScrollPane(patternWatchTable), c);

        tableModel = new DefaultTableModel(new String[]{"path", "ephemeral", "created", "modified", "version", "data"}, 0);

        watchTable = new JXTable(tableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        watchTable.setFont(ZooDirector.FONT_MONOSPACED);
        watchTable.setHorizontalScrollEnabled(true);

        final JPopupMenu tableMenu = new JPopupMenu();

        JMenuItem goToWatchMenuItem = new JMenuItem("go to ...");
        goToWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String path = getPath(clickedRow);
                parent.selectTreeNode(path);
            }
        });
        tableMenu.add(goToWatchMenuItem);

        JMenuItem viewEditWatchMenuItem = new JMenuItem("view/edit");
        viewEditWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String path = getPath(clickedRow);
                parent.viewEditTreeNode(path);
            }
        });
        tableMenu.add(viewEditWatchMenuItem);

        tableMenu.addSeparator();

        JMenuItem removeWatchMenuItem = new JMenuItem("remove watch");
        removeWatchMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DynamicTable.removeSelectedRows(watchTable, new DynamicTable.Callback() {
                    @Override
                    public void execute(int row) {
                        watches.remove(tableModel.getValueAt(row, 0));
                    }
                });
            }
        });

        tableMenu.add(removeWatchMenuItem);

        watchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    // Show context menu if clicking selected row
                    clickedRow = watchTable.rowAtPoint(e.getPoint());
                    if (watchTable.isRowSelected(clickedRow)) {
                        tableMenu.show(watchTable, e.getX(), e.getY());
                    }
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, patternWatchPanel, new JScrollPane(watchTable));
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(200);
        add(splitPane, BorderLayout.CENTER);
    }

    synchronized private void setData(int row, Stat stat, byte[] data) {
        tableModel.setValueAt(stat == null ? null : (stat.getEphemeralOwner() != 0), row, 1);
        tableModel.setValueAt(stat == null ? null : new LocalDateTime(stat.getCtime()), row, 2);
        tableModel.setValueAt(stat == null ? null : new LocalDateTime(stat.getMtime()), row, 3);
        tableModel.setValueAt(stat == null ? null : stat.getVersion(), row, 4);
        tableModel.setValueAt(data == null ? null : new String(data), row, 5);
        tableModel.fireTableRowsUpdated(row, row);
    }

    private String getPath(int row) {
        return (String) tableModel.getValueAt(watchTable.convertRowIndexToModel(row), 0);
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
            final int row = getRow(path);

            if (row < 0) {
                return;
            }

            if (deleted) {
                logger.info("[watch] {} deleted", path);
                setData(row, null, null);
            } else {
                logger.info("[watch] {} updated", path);
                new LoadDataWorker(zookeeperSync, path, new LoadDataWorker.Callback() {
                    @Override
                    public void execute(String path, final Stat stat, final byte[] data) {
                        if (stat == null) {
                            logger.error("[watch] {} update failed", path);
                        }
                        setData(row, stat, data);
                    }
                }).execute();
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

    synchronized public boolean removeWatch(String path) {
        if (watches.remove(path)) {
            tableModel.removeRow(getRow(path));
            logger.debug("{} watch removed", path);
            return true;
        }
        return false;
    }

    synchronized public boolean hasWatch(String path) {
        return watches.contains(path);
    }

    synchronized public void addWatch(String path) {
        if (!watches.contains(path)) {
            logger.debug("{} watch added", path);
            watches.add(path);
            tableModel.addRow(new Object[]{path, null, null, null, null, null});
            updateData(path, false);
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
            logger.debug("{} watch pattern added", watchPattern);
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

    public void setZookeeperSync(ZookeeperSync zookeeperSync) {
        this.zookeeperSync = zookeeperSync;
        zookeeperSync.addListener(new ZookeeperSync.Listener() {
            @Override
            public void process(ZookeeperSync.Event e) {
                updateData(e.path, e.type == ZookeeperSync.Event.Type.delete);
            }
        });
    }
}
