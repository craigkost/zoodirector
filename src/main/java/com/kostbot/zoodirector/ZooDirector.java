package com.kostbot.zoodirector;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ZooDirector extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirector.class);

    public static final Font FONT_MONOSPACED = new Font("Monospaced", Font.PLAIN, 11);
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private final Configuration configuration;
    private ZookeeperPanel zookeeperPanel;

    private ZooDirector(Configuration configuration) {
        super("ZooDirector");
        this.configuration = configuration;

        setupMenuBar();

        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (zookeeperPanel != null) {
                    zookeeperPanel.close();
                }
            }
        });

        this.setPreferredSize(new Dimension(800, 600));
        this.pack();
    }

    private class InfoDialog extends JDialog {
        InfoDialog(Frame owner, String title, String resourcePath) {
            super(owner, title);

            String text = "";
            try {
                text = Resources.toString(getClass().getResource(resourcePath), Charsets.UTF_8);
            } catch (IOException e) {
                logger.error("Failed to load Help/About content");
            }

            JLabel aboutLabel = new JLabel(text);
            aboutLabel.setBackground(Color.WHITE);
            aboutLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            add(aboutLabel);
            pack();
        }
    }

    private void connect(String connectionString) {
        getContentPane().removeAll();
        if (zookeeperPanel != null) {
            zookeeperPanel.close();
        }
        zookeeperPanel = new ZookeeperPanel(connectionString);
        getContentPane().add(zookeeperPanel);
        pack();
    }

    /**
     * Create menu bar
     */
    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu connectMenu = new JMenu("Connect");
        menuBar.add(connectMenu);

        JMenuItem quickConnect = new JMenuItem("Quick Connect");
        connectMenu.add(quickConnect);
        quickConnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String connectionString = (String) JOptionPane.showInputDialog(
                        null,
                        "Enter zookeeper connection string",
                        "Quick Connect",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        "localhost:2181");

                if (connectionString != null) {
                    connect(connectionString);
                }
            }
        });

        if (configuration != null) {
            String[] connectionStrings = this.configuration.getStringArray("connection");

            if (connectionStrings != null && connectionStrings.length > 0) {
                connectMenu.addSeparator();
                for (final String connectionString : connectionStrings) {
                    JMenuItem menuItem = new JMenuItem(connectionString);
                    menuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            connect(connectionString);
                        }
                    });
                    connectMenu.add(menuItem);
                }
            }
        }

        final JDialog helpDialog = new InfoDialog(this, "About", "/about.html");

        helpDialog.setLocationRelativeTo(null);
        helpDialog.setVisible(false);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutMenuItem = new JMenuItem("About");
        aboutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helpDialog.setVisible(true);
            }
        });
        helpMenu.add(aboutMenuItem);
        menuBar.add(helpMenu);

        this.setJMenuBar(menuBar);
    }

    /**
     * Construct and display GUI
     */
    private static void createAndShowGUI() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to set Nimbus LookAndFeel : " + e.getMessage());
        }

        String configurationFile = System.getenv("ZOODIRECTOR_CONFIG");

        if (configurationFile == null) {
            configurationFile = System.getProperty("user.home") + File.separator + ".zoodirector";
        }

        Configuration configuration = null;

        try {
            configuration = new PropertiesConfiguration(configurationFile);
        } catch (ConfigurationException e) {
            logger.error("Failed to load configuration file: {}", configurationFile);
        }

        ZooDirector zooDirector = new ZooDirector(configuration);
        zooDirector.setLocationRelativeTo(null);
        zooDirector.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}