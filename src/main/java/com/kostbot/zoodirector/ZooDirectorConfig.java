package com.kostbot.zoodirector;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Craig Kost
 */
public class ZooDirectorConfig {
    private static final Logger logger = LoggerFactory.getLogger(ZooDirectorConfig.class);

    private final Configuration config;

    public ZooDirectorConfig(String configFilePath) throws ConfigurationException {
        config = new PropertiesConfiguration(configFilePath);
    }

    public String[] getConnectionStrings() {
        String[] connectionStrings = null;
        if (config != null) {
            try {
                connectionStrings = config.getStringArray("connection");
            } catch (ConversionException e) {
                logger.error("failed to load connection list [{}]", e.getMessage());
            }
        }

        return connectionStrings;
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
     * @return window width property "window.width" if found and is an integer value, else returns default value
     */
    public int getWindowWidth() {
        return getIntProperty("window.width", 800);
    }

    /**
     * Get the customized window height
     *
     * @return window height property "window.height" if found and is an integer value, else returns default value
     */
    public int getWindowHeight() {
        return getIntProperty("window.height", 600);
    }

    public int getConnectionRetryPeriod() {
        return getIntProperty("connection.retryPeriod", 5000);
    }
}
