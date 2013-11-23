package com.kostbot.zoodirector.ui.helpers;

import com.kostbot.zoodirector.zookeepersync.ZookeeperSync;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;

public class UIUtils {
    public static void highlightInvalidZookeeperPath(final JTextField textField, final boolean allowSubPaths) {
        textField.getDocument().addDocumentListener(new DocumentListener() {
            private void highlightText() {
                if (ZookeeperSync.isValidPath(textField.getText(), allowSubPaths)) {
                    textField.setForeground(Color.BLACK);
                } else {
                    textField.setForeground(Color.RED);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                highlightText();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                highlightText();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                highlightText();
            }
        });
    }
}
