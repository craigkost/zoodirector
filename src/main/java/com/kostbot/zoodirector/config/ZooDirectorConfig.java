package com.kostbot.zoodirector.config;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ZooDirectorConfig {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirectorConfig.class);

    // Window
    private static final String WINDOW = "window";
    private static final String WINDOW_WIDTH = WINDOW + ".width";
    private static final String WINDOW_HEIGHT = WINDOW + ".height";

    protected static final int DEFAULT_WINDOW_WIDTH = 800;
    protected static final int DEFAULT_WINDOW_HEIGHT = 600;

    // Connection
    private static final String CONNECTIONS_BASE = "connections";

    // Global connection
    protected static final int DEFAULT_CONNECTIONS_RETRY_PERIOD = 5000;
    private static final String CONNECTIONS_RETRY_PERIOD = CONNECTIONS_BASE + ".retryPeriod";

    // Individual connection
    private static final String CONNECTIONS = CONNECTIONS_BASE + ".connection";
    private static final String CONNECTION_NAME = "name";
    private static final String CONNECTION_VALUE = "value";

    private XMLConfiguration config;

    /**
     * Create a ZooDirector configuration object. If the config file does not exist it will be created (if possible) and
     * all configuration changes are auto synced to this file.
     *
     * @param configFilePath
     */
    public ZooDirectorConfig(String configFilePath) {
        File configFile = new File(configFilePath);

        if (!configFile.exists()) {
            config = new XMLConfiguration();
            config.setFile(configFile);
            config.setAutoSave(true);
            logger.info("changes to configuration will be saved to: {}", configFile);
        } else {
            try {
                config = new XMLConfiguration(configFile);
                config.setAutoSave(true);
                logger.info("loaded configuration file: {}", configFile);
            } catch (ConfigurationException e) {
                logger.error("failed to load configuration file: {}. any changes to settings will not be persisted", configFile);
                config = new XMLConfiguration();
            }
        }
    }

    /**
     * Get a map of the existing connection aliases. The keys are the alias names and values are the associated
     * zookeeper connection strings.
     *
     * @return a map of connection aliases
     */
    public Map<String, String> getConnectionAliases() {
        Map<String, String> connectionStrings = new TreeMap<String, String>();

        try {
            List<HierarchicalConfiguration> aliases = config.configurationsAt(CONNECTIONS);
            if (aliases != null) {
                for (HierarchicalConfiguration alias : aliases) {
                    String name = alias.getString(CONNECTION_NAME);
                    String value = alias.getString(CONNECTION_VALUE, name);
                    connectionStrings.put(name, value);
                }
            }
        } catch (ConversionException e) {
            logger.error("failed to load connection list [{}]", e.getMessage());
        }

        return connectionStrings;
    }

    /**
     * Add a connection alias
     *
     * @param name  name of alias
     * @param value zookeeper connection string
     */
    public void addConnectionAlias(String name, String value) {
        Map<String, String> aliases = getConnectionAliases();
        aliases.put(name, value);
        setConnectionAliases(aliases);
    }

    /**
     * Clobber the existing connection aliases with the provided set.
     *
     * @param aliases
     */
    public void setConnectionAliases(Map<String, String> aliases) {
        config.clearTree(CONNECTIONS);
        for (String name : aliases.keySet()) {
            config.addProperty(CONNECTIONS + "(-1)." + CONNECTION_NAME, name);
            config.addProperty(CONNECTIONS + "." + CONNECTION_VALUE, aliases.get(name));
        }
    }

    /**
     * Helper method for retrieving integer value properties and falling back to defaultValue if not found or type
     * conversion error occurs
     *
     * @param propertyName property to retrieve from configuration file
     * @param defaultValue default value to fall back on on missing property or conversion error
     * @return property value if it exist and is an integer, defaultValue otherwise
     */
    private int getIntProperty(String propertyName, int defaultValue) {
        try {
            return config.getInt(propertyName, defaultValue);
        } catch (ConversionException e) {
            logger.error("failed to load property : {} [{}]", propertyName, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Get the customized window width
     *
     * @return window width property WINDOW_WIDTH if found and is an integer value, else returns default value
     */
    public int getWindowWidth() {
        return getIntProperty(WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH);
    }

    /**
     * Set the customized window width
     *
     * @param width
     */
    public void setWindowWidth(int width) {
        config.setProperty(WINDOW_WIDTH, width);
    }

    /**
     * Get the customized window height
     *
     * @return window height property "window.height" if found and is an integer value, else returns default value
     */
    public int getWindowHeight() {
        return getIntProperty(WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Set the customized window height
     *
     * @param height
     */
    public void setWindowHeight(int height) {
        config.setProperty(WINDOW_HEIGHT, height);
    }

    /**
     * Get the connection retry period. Used for determining when a connection request should be retried.
     *
     * @return
     */
    public int getConnectionRetryPeriod() {
        return getIntProperty(CONNECTIONS_RETRY_PERIOD, DEFAULT_CONNECTIONS_RETRY_PERIOD);
    }

    /**
     * Set the connection retry period
     *
     * @param connectionRetryPeriod
     */
    public void setConnectionRetryPeriod(int connectionRetryPeriod) {
        config.setProperty(CONNECTIONS_RETRY_PERIOD, connectionRetryPeriod);
    }
}
