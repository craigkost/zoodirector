package com.kostbot.zoodirector;

import com.kostbot.zoodirector.config.ZooDirectorConfig;
import com.kostbot.zoodirector.ui.ZooDirectorFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;

public class ZooDirector {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirector.class);

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
            configFilePath = System.getProperty("user.home") + File.separator + "zoodirector.xml";
        }

        ZooDirectorFrame zooDirector = new ZooDirectorFrame(new ZooDirectorConfig(configFilePath));
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