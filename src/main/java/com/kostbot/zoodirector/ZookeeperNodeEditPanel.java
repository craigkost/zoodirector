package com.kostbot.zoodirector;

import com.netflix.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Panel used for viewing and editing zookeeper nodes.
 *
 * @author Craig Kost
 */
public class ZookeeperNodeEditPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperNodeEditPanel.class);

    private static final String PATH = "Path";
    private static final String PATH_EPHEMERAL = "Path (Ephemeral)";

    private final CuratorFramework client;

    private String path;

    private final JLabel pathLabel;
    private final JTextField pathTextField;
    private final JTextField cTimeTextField;
    private final JTextField mTimeTextField;
    private final JTextField versionTextField;
    private final JTextArea dataTextArea;

    private final JButton saveButton;
    private final JButton clearButton;
    private final JButton reloadButton;

    private String initData; // Used for detecting data edit changes

    ZookeeperNodeEditPanel(final CuratorFramework client) {
        super();

        this.client = client;

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
        dataTextArea = new JTextArea(25, 50);
        dataTextArea.setFont(ZooDirector.FONT_MONOSPACED);
        dataTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                isDataUpdated();
            }
        });
        this.add(dataTextArea, c);

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
                try {
                    initData = new String(client.getData().forPath(path));
                    dataTextArea.setText(initData);
                    isDataUpdated();
                } catch (Exception e1) {
                    logger.error(e1.getMessage());
                }
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
                if (isDataUpdated()) {
                    try {
                        client.setData().forPath(path, dataTextArea.getText().getBytes());
                        initData = dataTextArea.getText();
                        setZookeeperPath(path);
                    } catch (Exception e1) {
                        logger.error(e1.getMessage());
                    }
                }
            }
        });
        buttonPanel.add(saveButton);
        setZookeeperPath(null);
    }

    /**
     * Check if the nodes data has been updated. Sets the dataStatusLabel text accordingly.
     *
     * @return true is data has been updated, false otherwise
     */
    private boolean isDataUpdated() {
        if (initData != null) {
            if (!initData.equals(dataTextArea.getText())) {
                saveButton.setEnabled(true);
                return true;
            }
        }
        saveButton.setEnabled(false);
        return false;
    }

    /**
     * Clear edit panel fields.
     */
    private void clear() {
        versionTextField.setText("");
        cTimeTextField.setText("");
        mTimeTextField.setText("");
        pathTextField.setText("");
        dataTextArea.setText("");
    }

    /**
     * Update the edit panel with values for the given zookeeper path.
     *
     * @param path node path to edit
     */
    public void setZookeeperPath(String path) {
        this.path = path;
        if (path == null) {
            clear();
            initData = null;
            pathLabel.setText(PATH);
            dataTextArea.setEnabled(false);
            reloadButton.setEnabled(false);
            clearButton.setEnabled(false);
            saveButton.setEnabled(false);
        } else {
            dataTextArea.setEnabled(true);
            reloadButton.setEnabled(true);
            pathTextField.setText(path);
            try {
                Stat stat = client.checkExists().forPath(path);
                pathLabel.setText(stat.getEphemeralOwner() == 0 ? PATH : PATH_EPHEMERAL);
                versionTextField.setText(Integer.toString(stat.getVersion()));
                cTimeTextField.setText(new DateTime(stat.getCtime()).toString(ZooDirector.DATE_FORMAT));
                mTimeTextField.setText(new DateTime(stat.getMtime()).toString(ZooDirector.DATE_FORMAT));
                String data = new String(client.getData().forPath(path));
                dataTextArea.setText(data);
                initData = data;
            } catch (Exception e) {
                // TODO remove node from tree on KeeperException.NoNodeException
                logger.error("edit {} [{}]", path, e.getMessage());
                clear();
            }
            dataTextArea.setEditable(true);
            clearButton.setEnabled(true);
            saveButton.setEnabled(true);
        }
        isDataUpdated();
    }
}
