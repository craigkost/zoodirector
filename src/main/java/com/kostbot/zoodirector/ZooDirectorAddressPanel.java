package com.kostbot.zoodirector;

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
        addressField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    zooDirectorPanel.viewEditTreeNode(addressField.getText());
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
