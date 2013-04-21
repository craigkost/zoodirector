package com.kostbot.zoodirector;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Craig Kost
 */
public class ZooDirectorConfigTest {
    private String configFilePath;

    @Before
    public void before() throws IOException {
        configFilePath = System.getProperty("java.io.tmpdir") + ".zoodirector-" + RandomStringUtils.randomAlphanumeric(15) + ".xml";

        new File(configFilePath).deleteOnExit();
    }

    private void ensureConfigFileExists(boolean exists) {
        Assert.assertEquals("configuration file should not be created until a set", exists, new File(configFilePath).exists());
    }

    @Test
    public void testGetConnectionAliases() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        // Check default
        Map<String, String> aliases = zooDirectorConfig.getConnectionAliases();
        Assert.assertNotNull(aliases);
        Assert.assertEquals(0, aliases.size());

        ensureConfigFileExists(false);
    }

    @Test
    public void testAddConnectionAliases() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        String name = "alias1";
        String value = "localhost";

        Map<String, String> aliases = new HashMap<String, String>(1);
        aliases.put(name, value);

        zooDirectorConfig.addConnectionAlias(name, value);

        // Check value is set
        Assert.assertEquals(aliases, zooDirectorConfig.getConnectionAliases());

        // Check persistence
        Assert.assertEquals(aliases, new ZooDirectorConfig(configFilePath).getConnectionAliases());

        value = "localhost:2181";
        aliases.put(name, value);

        zooDirectorConfig.addConnectionAlias(name, value);

        // Check value is updated
        Assert.assertEquals(aliases, zooDirectorConfig.getConnectionAliases());

        // Check persistence
        Assert.assertEquals(aliases, new ZooDirectorConfig(configFilePath).getConnectionAliases());

        ensureConfigFileExists(true);
    }

    @Test
    public void testSetConnectionAliases() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        Map<String, String> aliases = new HashMap<String, String>(2);
        aliases.put("alias1", "localhost:2181");
        aliases.put("alias2", "localhost:2181");

        zooDirectorConfig.setConnectionAliases(aliases);

        // Check value is set
        Assert.assertEquals(aliases, zooDirectorConfig.getConnectionAliases());

        // Check persistence
        Assert.assertEquals(aliases, new ZooDirectorConfig(configFilePath).getConnectionAliases());

        aliases.put("alias1", "localhost:2182");
        aliases.remove("alias2");

        zooDirectorConfig.setConnectionAliases(aliases);

        // Check value is updated, and alias is removed
        Assert.assertEquals(aliases, zooDirectorConfig.getConnectionAliases());

        // Check persistence
        Assert.assertEquals(aliases, new ZooDirectorConfig(configFilePath).getConnectionAliases());

        ensureConfigFileExists(true);
    }

    @Test
    public void testGetWindowWidth() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        // Check default
        Assert.assertEquals(ZooDirectorConfig.DEFAULT_WINDOW_WIDTH, zooDirectorConfig.getWindowWidth());

        ensureConfigFileExists(false);
    }

    @Test
    public void testSetWindowWidth() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        int windowWidth = 1337;

        zooDirectorConfig.setWindowWidth(windowWidth);

        // Check value is set
        Assert.assertEquals(windowWidth, zooDirectorConfig.getWindowWidth());

        // Check persistence
        Assert.assertEquals(windowWidth, new ZooDirectorConfig(configFilePath).getWindowWidth());

        ensureConfigFileExists(true);
    }

    @Test
    public void testGetWindowHeight() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        // Check default
        Assert.assertEquals(ZooDirectorConfig.DEFAULT_WINDOW_HEIGHT, zooDirectorConfig.getWindowHeight());

        ensureConfigFileExists(false);
    }

    @Test
    public void testSetWindowHeight() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        int windowHeight = 1337;

        zooDirectorConfig.setWindowHeight(windowHeight);

        // Check value is set
        Assert.assertEquals(windowHeight, zooDirectorConfig.getWindowHeight());

        // Check persistence
        Assert.assertEquals(windowHeight, new ZooDirectorConfig(configFilePath).getWindowHeight());

        ensureConfigFileExists(true);
    }

    @Test
    public void testGetConnectionRetryPeriod() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        // Check default
        Assert.assertEquals(ZooDirectorConfig.DEFAULT_CONNECTIONS_RETRY_PERIOD, zooDirectorConfig.getConnectionRetryPeriod());

        ensureConfigFileExists(false);
    }

    @Test
    public void testSetConnectionRetryPeriod() throws Exception {
        ZooDirectorConfig zooDirectorConfig = new ZooDirectorConfig(configFilePath);

        int connectionRetryPeriod = 1337;

        zooDirectorConfig.setConnectionRetryPeriod(connectionRetryPeriod);

        // Check value is set
        Assert.assertEquals(connectionRetryPeriod, zooDirectorConfig.getConnectionRetryPeriod());

        // Check persistence
        Assert.assertEquals(connectionRetryPeriod, new ZooDirectorConfig(configFilePath).getConnectionRetryPeriod());

        ensureConfigFileExists(true);
    }
}
