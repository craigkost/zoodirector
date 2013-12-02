package com.kostbot.zoodirector.ui;

import com.kostbot.zoodirector.ui.helpers.UIUtils;
import com.kostbot.zoodirector.zookeepersync.ZookeeperSync;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ZooDirectorAddressPanel extends JPanel {
    final JTextField addressField;

    public ZooDirectorAddressPanel(final ZooDirectorPanel zooDirectorPanel) {
        super(new BorderLayout());

        addressField = new JTextField();
        JButton goToButton = new JButton("GO");

        this.add(addressField, BorderLayout.CENTER);
        this.add(goToButton, BorderLayout.EAST);

        addressField.setToolTipText("Enter the path you wish to go to");
        UIUtils.highlightIfConditionMetOnUpdate(addressField, new UIUtils.Condition() {
            @Override
            public boolean isMet() {
                return ZookeeperSync.isValidPath(addressField.getText());
            }
        });
        addressField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String path = addressField.getText();
                if (e.getKeyCode() == KeyEvent.VK_ENTER && ZookeeperSync.isValidPath(path)) {
                    zooDirectorPanel.viewEditTreeNode(path);
                }
            }
        });

        goToButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zooDirectorPanel.viewEditTreeNode(addressField.getText());
            }
        });
    }

    public void setPath(String path) {
        addressField.setText(path);
    }
}
