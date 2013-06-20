package com.kostbot.zoodirector;

import com.google.common.base.Strings;
import com.kostbot.zoodirector.helpers.DynamicTable;
import org.jdesktop.swingx.JXTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Craig Kost
 */
public class ZooDirectorConfigEditor extends JDialog {

    private final ZooDirectorConfig config;

    private final DefaultTableModel aliasTableModel;
    private final JXTable aliasTable;

    private class AliasPopupMenu extends JPopupMenu {
        private final JMenuItem addAliasMenuItem;
        private final JMenuItem removeAliasMenuItem;

        AliasPopupMenu() {
            super();

            removeAliasMenuItem = new JMenuItem("remove");
            removeAliasMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    DynamicTable.removeSelectedRows(aliasTable);
                }
            });
            add(removeAliasMenuItem);

            addAliasMenuItem = new JMenuItem("add");
            addAliasMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    addAlias();
                }
            });
            add(addAliasMenuItem);
        }

        private void enableDelete(boolean enable) {
            removeAliasMenuItem.setEnabled(enable);
        }
    }

    public ZooDirectorConfigEditor(JFrame parent, ZooDirectorConfig config, final String connectionString) {
        super(parent, "Configuration", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        this.config = config;

        JPanel mainPanel = new JPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.weightx = 0.5;
        c.insets.top = 5;
        c.insets.left = 5;
        c.insets.right = 5;
        c.insets.bottom = 2;

        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy += 1;
        mainPanel.add(new JLabel("Aliases"), c);

        c.gridy += 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        mainPanel.add(new JLabel("<html><i>any alias with empty name or connection string will not be saved</i></html>"), c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 2;
        c.gridx = 0;
        c.gridy += 1;
        aliasTableModel = new DefaultTableModel(new String[]{"alias", "connection string"}, 0);
        aliasTable = new JXTable(aliasTableModel);
        aliasTable.setHorizontalScrollEnabled(true);
        aliasTable.setAutoStartEditOnKeyStroke(true);
        aliasTable.setToolTipText("double click to edit");

        final AliasPopupMenu tableMenu = new AliasPopupMenu();

        Map<String, String> aliases = config.getConnectionAliases();
        for (String alias : aliases.keySet()) {
            aliasTableModel.addRow(new String[]{alias, aliases.get(alias)});
        }
        aliasTable.setPreferredScrollableViewportSize(new Dimension(400, 200));
        aliasTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    int clickedRow = aliasTable.rowAtPoint(e.getPoint());

                    // Show context menu if clicking selected row
                    tableMenu.enableDelete(aliasTable.isRowSelected(clickedRow));
                    tableMenu.show(aliasTable, e.getX(), e.getY());
                }
            }
        });

        mainPanel.add(new JScrollPane(aliasTable), c);

        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_END;
        c.gridy += 1;

        JPanel addButtonPanel = new JPanel();

        JButton addCurrentButton = new JButton("Add Current");
        addButtonPanel.add(addCurrentButton);

        if (Strings.isNullOrEmpty(connectionString)) {
            addCurrentButton.setEnabled(false);
        } else {
            addCurrentButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    addAlias(new String[]{connectionString, connectionString});
                }
            });
        }

        JButton addAliasButton = new JButton("Add");
        addAliasButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addAlias();
            }
        });
        addButtonPanel.add(addAliasButton);

        mainPanel.add(addButtonPanel, c);

        c.anchor = GridBagConstraints.LINE_START;
        c.gridy += 1;
        c.insets.top = 5;
        c.insets.bottom = 5;

        JPanel buttonPanel = new JPanel();

        JButton saveButton = new JButton("OK");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
                dispose();
            }
        });
        buttonPanel.add(saveButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, c);

        add(mainPanel);
        pack();
    }

    private void addAlias() {
        addAlias(new String[]{"", "<double click to edit>"});
    }

    private void addAlias(String[] aliasEntry) {
        aliasTableModel.addRow(aliasEntry);
    }

    private void save() {
        Map<String, String> aliases = new HashMap<String, String>(aliasTableModel.getRowCount());
        for (int i = 0; i < aliasTableModel.getRowCount(); ++i) {
            String name = (String) aliasTableModel.getValueAt(i, 0);
            String value = (String) aliasTableModel.getValueAt(i, 1);

            // Skip bad rows
            if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(value)) {
                continue;
            }

            aliases.put(name, value);
        }

        if (!aliases.equals(config.getConnectionAliases())) {
            config.setConnectionAliases(aliases);
        }
    }
}
