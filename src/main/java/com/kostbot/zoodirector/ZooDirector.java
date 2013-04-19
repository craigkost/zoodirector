package com.kostbot.zoodirector;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class ZooDirector extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirector.class);

    public static final Font FONT_MONOSPACED = new Font("Monospaced", Font.PLAIN, 11);
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private final ZooDirectorConfig config;
    private ZookeeperPanel zookeeperPanel;

    private ZooDirector(ZooDirectorConfig config) {
        super("zoodirector");

        this.config = config;

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

        this.setPreferredSize(new Dimension(config.getWindowWidth(), config.getWindowHeight()));
        this.pack();
    }

    private class HtmlInfoDialog extends JDialog {
        HtmlInfoDialog(Frame owner, String title, String htmlResourcePath, int width, int height) {
            super(owner, title);

            String text = "";
            try {
                text = Resources.toString(getClass().getResource(htmlResourcePath), Charsets.UTF_8);
            } catch (IOException e) {
                logger.error("failed to load {} resource {} content", title, htmlResourcePath);
            }

            JEditorPane content = new JEditorPane();
            content.setContentType("text/html");
            content.setEditable(false);
            content.setText(text);
            content.addHyperlinkListener(new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (Exception e1) {
                            logger.error("failed opening link " + e.getURL(), e1);
                        }
                    }
                }
            });

            // Ensure initial display show content from the top
            content.setCaretPosition(0);
            content.setBorder(BorderFactory.createEmptyBorder(5, 20, 10, 20));
            JScrollPane scrollPane = new JScrollPane(content);
            add(scrollPane);
            setPreferredSize(new Dimension(width, height));
            pack();
        }
    }

    /**
     * Establish a new connection to a zookeeper cluster.
     *
     * @param connectionString
     * @param connectionRetryPeriod
     */
    private void connect(String connectionString, int connectionRetryPeriod) {
        getContentPane().removeAll();
        if (zookeeperPanel != null) {
            zookeeperPanel.close();
        }
        zookeeperPanel = new ZookeeperPanel(connectionString, connectionRetryPeriod);
        getContentPane().add(zookeeperPanel);
    }

    /**
     * Create menu bar
     */
    private void setupMenuBar() {
        final JMenuBar menuBar = new JMenuBar();

        JMenu connectMenu = new JMenu("Connect");
        connectMenu.setMnemonic(KeyEvent.VK_C);
        menuBar.add(connectMenu);

        JMenuItem quickConnect = new JMenuItem("Quick Connect");
        connectMenu.add(quickConnect);
        quickConnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String connectionString = (String) JOptionPane.showInputDialog(
                        SwingUtilities.getRoot(menuBar),
                        "Enter zookeeper connection string",
                        "Quick Connect",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        "localhost:2181");

                if (connectionString != null) {
                    connect(connectionString, config.getConnectionRetryPeriod());
                }
            }
        });
        quickConnect.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        quickConnect.setMnemonic(KeyEvent.VK_Q);

        String[] connectionStrings = config.getConnectionStrings();

        if (connectionStrings != null && connectionStrings.length > 0) {
            connectMenu.addSeparator();
            for (final String connectionString : connectionStrings) {
                JMenuItem menuItem = new JMenuItem(connectionString);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        connect(connectionString, config.getConnectionRetryPeriod());
                    }
                });
                connectMenu.add(menuItem);
            }
        }

        final JDialog aboutDialog = new HtmlInfoDialog(this, "About", "/about.html", 400, 225);

        aboutDialog.setLocationRelativeTo(null);
        aboutDialog.setVisible(false);

        final JDialog commandHelpDialog = new HtmlInfoDialog(this, "Command Usage", "/command-usage.html", 600, 400);

        commandHelpDialog.setLocationRelativeTo(null);
        commandHelpDialog.setVisible(false);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutMenuItem = new JMenuItem("About");
        aboutMenuItem.setMnemonic(KeyEvent.VK_A);
        aboutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                aboutDialog.setVisible(true);
            }
        });
        helpMenu.add(aboutMenuItem);

        JMenuItem commandUsageMenuItem = new JMenuItem("Commands");
        commandUsageMenuItem.setMnemonic(KeyEvent.VK_C);
        commandUsageMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commandHelpDialog.setVisible(true);
            }
        });
        helpMenu.add(commandUsageMenuItem);

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

        String configFilePath = System.getenv("ZOODIRECTOR_CONFIG");

        if (configFilePath == null) {
            configFilePath = System.getProperty("user.home") + File.separator + ".zoodirector";
        }

        ZooDirector zooDirector = new ZooDirector(new ZooDirectorConfig(configFilePath));
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