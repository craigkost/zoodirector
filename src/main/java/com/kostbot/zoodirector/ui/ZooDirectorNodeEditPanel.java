package com.kostbot.zoodirector.ui;

import com.google.common.base.Strings;
import com.kostbot.zoodirector.ui.helpers.UIUtils;
import com.kostbot.zoodirector.ui.workers.LoadDataWorker;
import com.kostbot.zoodirector.ui.workers.SaveDataWorker;
import com.kostbot.zoodirector.zookeepersync.ZookeeperSync;
import org.apache.zookeeper.data.Stat;
import org.joda.time.DateTime;

import javax.swing.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Panel used for viewing and editing zookeeper nodes.
 */
public class ZooDirectorNodeEditPanel extends JPanel {
    private static final String PATH = "Path";
    private static final String PATH_EPHEMERAL = "Path (Ephemeral)";

    private ZookeeperSync zookeeperSync;

    private SwingWorker<Void, Void> swingWorker;

    private volatile String path;
    private volatile String initData; // Used for detecting data edit changes

    private final JLabel pathLabel;
    private final JTextField pathTextField;
    private final JTextField cTimeTextField;
    private final JTextField mTimeTextField;
    private final JTextField versionTextField;
    private final JTextField ephemeralOwnerTextField;
    private final JTextArea dataTextArea;
    private final JLabel dataSizeLabel;

    private final UndoManager undoManager;

    private final JButton saveButton;
    private final JButton clearButton;
    private final JButton reloadButton;

    class GridBagPanelBuilder {
        JPanel panel;
        GridBagConstraints c;

        GridBagPanelBuilder(JPanel panel) {
            this.panel = panel;
            panel.setLayout(new GridBagLayout());
            c = new GridBagConstraints();
            c.anchor = GridBagConstraints.FIRST_LINE_START;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 0.5;
            c.weighty = 0;
            c.gridx = 0;
            c.gridy = 0;
            c.insets = new Insets(2, 5, 0, 5);
        }

        void setWeightY(double weighty) {
            c.weighty = weighty;
        }

        void setFill(int fill) {
            c.fill = fill;
        }

        void addComponents(int width, JComponent... components) {
            c.gridwidth = width;
            for (JComponent component : components) {
                panel.add(component, c);
                c.gridx += 1;
            }
            c.gridx = 0;
            c.gridy += 1;
        }
    }

    JTextField createNoEditTextField(String tooltip) {
        JTextField textField = new JTextField(50);
        textField.setEditable(false);
        textField.setFont(ZooDirectorFrame.FONT_MONOSPACED);
        textField.setToolTipText(tooltip);
        return textField;
    }

    ZooDirectorNodeEditPanel() {
        super();

        GridBagPanelBuilder gridBagPanelBuilder = new GridBagPanelBuilder(this);

        pathLabel = new JLabel(PATH);
        gridBagPanelBuilder.addComponents(2, pathLabel);
        pathTextField = createNoEditTextField("Zookeeper Path");
        gridBagPanelBuilder.addComponents(2, pathTextField);

        gridBagPanelBuilder.addComponents(1, new JLabel("Created"), new JLabel("Modified"));
        cTimeTextField = createNoEditTextField("Path Creation Time");
        mTimeTextField = createNoEditTextField("Path Modification Time");
        gridBagPanelBuilder.addComponents(1, cTimeTextField, mTimeTextField);

        gridBagPanelBuilder.addComponents(1, new JLabel("Version"), new JLabel("Owner ID"));
        versionTextField = createNoEditTextField("modification count");
        ephemeralOwnerTextField = createNoEditTextField("ephemeral owner id (0 if persistent)");
        gridBagPanelBuilder.addComponents(1, versionTextField, ephemeralOwnerTextField);

        undoManager = new UndoManager();

        dataTextArea = new JTextArea(10, 50);
        dataTextArea.getDocument().addUndoableEditListener(undoManager);
        dataTextArea.setFont(ZooDirectorFrame.FONT_MONOSPACED);
        dataTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                isDataUpdated();

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_F5:
                        reload();
                        break;
                    case KeyEvent.VK_S:
                        // Ctrl + S
                        if (e.isControlDown()) {
                            save();
                        }
                        break;
                    case KeyEvent.VK_Z:
                        // Ctrl + Z
                        if (e.isControlDown()) {
                            if (undoManager.canUndo()) {
                                undoManager.undo();
                            }
                        }
                        break;
                    case KeyEvent.VK_R:
                        // Ctrl + R
                        if (e.isControlDown()) {
                            if (undoManager.canRedo()) {
                                undoManager.redo();
                            }
                        }
                        break;

                }
            }
        });

        gridBagPanelBuilder.addComponents(2, new JLabel("Data"));
        gridBagPanelBuilder.setWeightY(1.0);
        gridBagPanelBuilder.setFill(GridBagConstraints.BOTH);
        gridBagPanelBuilder.addComponents(2, new JScrollPane(dataTextArea));

        gridBagPanelBuilder.setWeightY(0.0);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel buttonPanel = new JPanel(new FlowLayout());

        // Reload
        reloadButton = new JButton("Reload");
        reloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reload();
            }
        });
        buttonPanel.add(reloadButton);

        // Clear
        clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataTextArea.setText("");
                isDataUpdated();
            }
        });
        buttonPanel.add(clearButton);

        // Save
        saveButton = new JButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        buttonPanel.add(saveButton);

        bottomPanel.add(buttonPanel, BorderLayout.WEST);

        dataSizeLabel = new JLabel("");

        JPanel dataSizePanel = new JPanel();
        dataSizePanel.add(dataSizeLabel);

        bottomPanel.add(dataSizePanel, BorderLayout.EAST);

        gridBagPanelBuilder.setFill(GridBagConstraints.HORIZONTAL);
        gridBagPanelBuilder.addComponents(2, bottomPanel);
    }

    /**
     * Check if the nodes data has been updated. Sets the dataStatusLabel text accordingly.
     *
     * @return true is data has been updated, false otherwise
     */
    private boolean isDataUpdated() {
        String currentData = dataTextArea.getText();

        dataSizeLabel.setText(UIUtils.humanReadableByteCount(currentData.getBytes().length));

        clearButton.setEnabled(!Strings.isNullOrEmpty(currentData));
        if (initData != null) {
            if (!initData.equals(currentData)) {
                saveButton.setEnabled(true);
                return true;
            }
        }
        saveButton.setEnabled(false);
        return false;
    }

    /**
     * If the data has been updated since last fetch data will be set in zookeeper.
     */
    private void save() {
        if (isDataUpdated()) {
            executeSwingWorker(new SaveDataWorker(zookeeperSync, path, Integer.parseInt(versionTextField.getText()), dataTextArea.getText().getBytes(), new SaveDataWorker.Callback() {
                @Override
                public void onComplete(String path) {
                    if (ZooDirectorNodeEditPanel.this.path != null &&
                            ZooDirectorNodeEditPanel.this.path.equals(path)) {
                        reload();
                    }
                }

                @Override
                public void onFailure(String path) {
                    // TODO popup error? with force save option
                }
            }));
        }
    }

    /**
     * Helper method for cancelling current swingWorker if it exists and execute provided swingWorker if it exists.
     *
     * @param swingWorker
     */
    synchronized private void executeSwingWorker(SwingWorker<Void, Void> swingWorker) {
        if (this.swingWorker != null) {
            this.swingWorker.cancel(false);
        }
        this.swingWorker = swingWorker;
        if (swingWorker != null) {
            swingWorker.execute();
        }
    }

    /**
     * Reload the edit panel content from zookeeper.
     */
    private void reload() {
        setZookeeperPath(this.path, false);
    }

    /**
     * If path is different than current path updates the edit panel with values for the given zookeeper path and clears
     * the undo history.
     *
     * @param path node path to edit
     * @see #setZookeeperPath(String, boolean)
     */
    public void setZookeeperPath(String path) {
        if (this.path == null || !this.path.equals(path)) {
            setZookeeperPath(path, true);
        }
    }

    /**
     * Update the edit panel with values for the given zookeeper path.
     *
     * @param path             node path to edit
     * @param clearUndoManager clear undo events if setting new path
     */
    private void setZookeeperPath(String path, final boolean clearUndoManager) {
        this.path = path;
        executeSwingWorker(new LoadDataWorker(zookeeperSync, path, new LoadDataWorker.Callback() {
            @Override
            public void onComplete(String path, Stat stat, byte[] data) {
                setData(path, stat, data);
                if (clearUndoManager) {
                    ZooDirectorNodeEditPanel.this.undoManager.discardAllEdits();
                }
            }
        }));
    }

    /**
     * Helper for setting panel text fields and enabling/disabling buttons accordingly.
     * <p/>
     * EDT thread safe
     *
     * @param path
     * @param stat
     * @param data
     */
    private void setData(String path, Stat stat, byte[] data) {
        if (stat == null) {
            versionTextField.setText("");
            ephemeralOwnerTextField.setText("");
            cTimeTextField.setText("");
            mTimeTextField.setText("");
            pathTextField.setText("");
            dataTextArea.setText("");
            dataSizeLabel.setText("");

            initData = null;

            pathLabel.setText(PATH);
            pathTextField.setText(path == null ? "" : path);

            dataTextArea.setEnabled(false);

            reloadButton.setEnabled(false);
            clearButton.setEnabled(false);
            saveButton.setEnabled(false);
        } else {
            dataTextArea.setEnabled(true);
            dataTextArea.setEditable(true);
            reloadButton.setEnabled(true);

            pathLabel.setText(stat.getEphemeralOwner() == 0 ? PATH : PATH_EPHEMERAL);
            pathTextField.setText(path);

            versionTextField.setText(Integer.toString(stat.getVersion()));
            ephemeralOwnerTextField.setText(Long.toString(stat.getEphemeralOwner()));
            cTimeTextField.setText(new DateTime(stat.getCtime()).toString(ZooDirectorFrame.DATE_FORMAT));
            mTimeTextField.setText(new DateTime(stat.getMtime()).toString(ZooDirectorFrame.DATE_FORMAT));

            initData = data == null ? "" : new String(data);
            dataTextArea.setText(initData);

            isDataUpdated();
        }
    }

    /**
     * Disable editing of zookeeper node.
     */
    public void setOffline() {
        executeSwingWorker(null);
        dataTextArea.setEditable(false);
        dataTextArea.setEnabled(false);
        clearButton.setEnabled(false);
        saveButton.setEnabled(false);
        reloadButton.setEnabled(false);
    }

    public void setZookeeperSync(ZookeeperSync zookeeperSync) {
        this.zookeeperSync = zookeeperSync;
    }
}
