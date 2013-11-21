package com.kostbot.zoodirector;

import org.apache.zookeeper.common.PathUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ZooDirectorAddressPanel extends JPanel {
    final JTextField addressField;

    public ZooDirectorAddressPanel(final ZooDirectorPanel zooDirectorPanel) {
        super(new BorderLayout());

        addressField = new JTextField();
        JButton goToButton = new JButton("GO");

        this.add(addressField, BorderLayout.CENTER);
        this.add(goToButton, BorderLayout.EAST);

        addressField.setToolTipText("Enter the path you wish to go to");
        addressField.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {
                if (isPathValid() && (e.getKeyCode() == KeyEvent.VK_ENTER)) {
                    zooDirectorPanel.viewEditTreeNode(addressField.getText());
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                isPathValid();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                isPathValid();
            }
        });

        goToButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zooDirectorPanel.viewEditTreeNode(addressField.getText());
            }
        });
    }

    private boolean isPathValid() {
        try {
            PathUtils.validatePath(addressField.getText());
            addressField.setForeground(Color.black);
            return true;
        } catch (IllegalArgumentException e) {
            addressField.setForeground(Color.red);
            return false;
        }
    }

    public void setPath(String path) {
        addressField.setText(path);
        isPathValid();
    }
}
