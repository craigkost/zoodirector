package com.kostbot.zoodirector.ui;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ZooDirectorLogDialog extends JDialog {
    final JTextArea logTextArea;
    final JTextField lastLogTextField;
    final JPanel lastLogPanel;

    public ZooDirectorLogDialog() {
        super(new JFrame(), "Logs");

        JButton lastLogButton = new JButton(UIManager.getIcon("FileChooser.listViewIcon"));
        lastLogButton.setToolTipText("click to view/hide log viewer");

        lastLogPanel = new JPanel(new BorderLayout());
        lastLogPanel.add(lastLogButton, BorderLayout.WEST);

        this.add(lastLogPanel, BorderLayout.SOUTH);

        logTextArea = new JTextArea(25, 100);
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(false);
        logTextArea.setFont(ZooDirectorFrame.FONT_MONOSPACED);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);

        this.add(logScrollPane);
        this.pack();
        this.setLocationRelativeTo(SwingUtilities.getRoot(this));

        lastLogTextField = new JTextField();
        lastLogTextField.setEditable(false);
        lastLogTextField.setHorizontalAlignment(JLabel.LEFT);
        lastLogTextField.setFont(ZooDirectorFrame.FONT_MONOSPACED);
        lastLogPanel.add(lastLogTextField, BorderLayout.CENTER);

        lastLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ZooDirectorLogDialog.this.setVisible(!ZooDirectorLogDialog.this.isVisible());
            }
        });

        org.apache.log4j.Logger.getRootLogger().addAppender(new AppenderSkeleton() {
            @Override
            protected void append(LoggingEvent loggingEvent) {
                final Layout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p : %m%n");
                String line = layout.format(loggingEvent);
                logTextArea.append(line);
                lastLogTextField.setText(line);
            }

            @Override
            public void close() {
            }

            @Override
            public boolean requiresLayout() {
                return false;
            }
        });
    }

    public JPanel getLastLogPanel() {
        return lastLogPanel;
    }
}
