package com.kostbot.zoodirector;

import com.google.common.base.Strings;
import com.kostbot.zoodirector.workers.LoadDataWorker;
import com.kostbot.zoodirector.workers.SaveDataWorker;
import org.apache.zookeeper.data.Stat;
import org.joda.time.DateTime;

import javax.swing.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Panel used for viewing and editing zookeeper nodes.
 */
public class ZookeeperNodeEditPanel extends JPanel {
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
    private final JTextArea dataTextArea;

    private final UndoManager undoManager;

    private final JButton saveButton;
    private final JButton clearButton;
    private final JButton reloadButton;

    ZookeeperNodeEditPanel() {
        super();

        undoManager = new UndoManager();

        this.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.weightx = 0.5;
        c.insets.top = 5;
        c.insets.left = 5;
        c.insets.right = 5;
        c.insets.bottom = 2;

        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;

        pathLabel = new JLabel(PATH);
        this.add(pathLabel, c);

        c.gridy += 1;
        c.insets.top = 2;
        c.insets.bottom = 2;
        pathTextField = new JTextField(50);
        pathTextField.setEditable(false);
        pathTextField.setFont(ZooDirector.FONT_MONOSPACED);
        this.add(pathTextField, c);

        c.gridwidth = 1;
        c.gridy += 1;
        c.insets.top = 5;
        this.add(new JLabel("Created"), c);
        this.add(new JLabel("Modified"), c);

        c.gridy += 1;
        c.insets.top = 0;
        cTimeTextField = new JTextField(50);
        cTimeTextField.setEditable(false);
        cTimeTextField.setFont(ZooDirector.FONT_MONOSPACED);
        this.add(cTimeTextField, c);

        mTimeTextField = new JTextField(50);
        mTimeTextField.setEditable(false);
        mTimeTextField.setFont(ZooDirector.FONT_MONOSPACED);
        this.add(mTimeTextField, c);

        c.gridwidth = 2;
        c.gridy += 1;
        c.insets.top = 5;
        this.add(new JLabel("Version"), c);

        c.gridy += 1;
        c.insets.top = 0;
        versionTextField = new JTextField(50);
        versionTextField.setEditable(false);
        versionTextField.setToolTipText("modification count");
        versionTextField.setFont(ZooDirector.FONT_MONOSPACED);
        this.add(versionTextField, c);

        c.gridy += 1;
        c.gridwidth = 1;
        c.insets.top = 5;
        this.add(new JLabel("Data"), c);

        c.gridx = 0;
        c.gridy += 1;
        c.gridwidth = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets.top = 0;
        dataTextArea = new JTextArea(10, 50);
        dataTextArea.setFont(ZooDirector.FONT_MONOSPACED);
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
        dataTextArea.getDocument().addUndoableEditListener(undoManager);
        this.add(new JScrollPane(dataTextArea), c);

        c.fill = GridBagConstraints.NONE;
        c.weighty = 0;
        c.gridy += 1;
        c.insets.top = 5;
        c.insets.bottom = 5;

        JPanel buttonPanel = new JPanel(new FlowLayout());
        this.add(buttonPanel, c);

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
        setZookeeperPath(null, true);
    }

    /**
     * Check if the nodes data has been updated. Sets the dataStatusLabel text accordingly.
     *
     * @return true is data has been updated, false otherwise
     */
    private boolean isDataUpdated() {
        String currentData = dataTextArea.getText();
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
                    if (ZookeeperNodeEditPanel.this.path != null &&
                            ZookeeperNodeEditPanel.this.path.equals(path)) {
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
            this.swingWorker.cancel(true);
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
                    ZookeeperNodeEditPanel.this.undoManager.discardAllEdits();
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
            cTimeTextField.setText("");
            mTimeTextField.setText("");
            pathTextField.setText("");
            dataTextArea.setText("");

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
            cTimeTextField.setText(new DateTime(stat.getCtime()).toString(ZooDirector.DATE_FORMAT));
            mTimeTextField.setText(new DateTime(stat.getMtime()).toString(ZooDirector.DATE_FORMAT));

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
