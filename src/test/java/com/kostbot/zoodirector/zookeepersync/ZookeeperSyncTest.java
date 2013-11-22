package com.kostbot.zoodirector.zookeepersync;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ZookeeperSyncTest extends ZookeeperTestBase {

    private void assertEvent(ZookeeperSync.Event receivedEvent, ZookeeperSync.Event.Type type, String path) {
        Assert.assertEquals("should be a(n) [" + type + "] event", receivedEvent.type, type);
        Assert.assertEquals("should be for path [" + path + "]", receivedEvent.path, path);
    }

    @Test
    public void testGetParent() {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        Assert.assertNull(zookeeperSync.getParent("/"));
        Assert.assertEquals("/", zookeeperSync.getParent("/c"));
        Assert.assertEquals("/p", zookeeperSync.getParent("/p/c"));
        Assert.assertEquals("/g/p", zookeeperSync.getParent("/g/p/c"));
    }

    @Test
    public void testCreate() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        String path = "/test/all/parent/paths/are/created";

        zookeeperSync.create(path);

        Assert.assertNotNull("all parent paths should be created", client.checkExists().forPath(path));
    }

    @Test
    public void testSetData() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        String path = "/test/all/parent/paths/are/created";
        String data = "data!";
        String data2 = "data2!";

        zookeeperSync.create(path);
        zookeeperSync.setData(path, 0, data.getBytes());

        Assert.assertEquals(data, new String(client.getData().forPath(path)));

        zookeeperSync.setData(path, 1, data2.getBytes());
        Assert.assertEquals(data2, new String(client.getData().forPath(path)));
    }

    @Test
    public void testGetData() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        String path = "/test/all/parent/paths/are/created";
        String data = "data!";

        zookeeperSync.create(path);
        zookeeperSync.setData(path, 0, data.getBytes());

        Assert.assertEquals(data, new String(zookeeperSync.getData(path)));
    }

    @Test
    public void testGetStat() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        String path = "/test";

        zookeeperSync.create(path);

        Assert.assertNotNull(zookeeperSync.getStat(path));
    }

    @Test
    public void testDelete() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);
        String path = "/test";

        zookeeperSync.create(path + "/all/parent/paths/are/created");
        zookeeperSync.delete(path);

        Assert.assertNull("all children should be deleted", client.checkExists().forPath(path));
    }

    @Test
    public void testTrim() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);
        String path = "/test";

        zookeeperSync.create(path + "/1");
        zookeeperSync.create(path + "/2");
        zookeeperSync.create(path + "/3");
        zookeeperSync.create(path + "/4");
        zookeeperSync.create(path + "/5");

        Assert.assertEquals("children should exist", 5, client.getChildren().forPath(path).size());
        zookeeperSync.trim(path);
        Assert.assertEquals("children should be deleted", 0, client.getChildren().forPath(path).size());
    }

    @Test
    public void testPrune() throws Exception {
        ZookeeperSync zookeeperSync = new ZookeeperSync(client);
        String base = "/base";
        String path = base + "/test";

        zookeeperSync.create(base + "/1");
        zookeeperSync.create(path + "/1/2/3");

        Assert.assertEquals(base, zookeeperSync.prune(path));
        Assert.assertNotNull(client.checkExists().forPath(base));
        Assert.assertNull(client.checkExists().forPath(path));
    }

    @Test
    public void testAddEventsOnLoad() throws Exception {

        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        final List<ZookeeperSync.Event> receivedEventList = new ArrayList<ZookeeperSync.Event>(3);

        // Register lister before watch to receive loading add events.
        zookeeperSync.addListener(new ZookeeperSync.Listener() {
            @Override
            public void process(ZookeeperSync.Event e) {
                receivedEventList.add(e);
            }
        });

        zookeeperSync.watch();

        // Wait a little while to ensure we receive events.
        ConditionRetry.checkCondition(new ConditionRetry.Condition() {
            @Override
            public boolean check() {
                return receivedEventList.size() == 3;
            }
        });

        // Includes the add events for /, /zookeeper, /zookeeper/quota
        Assert.assertEquals("listener should receive events", 3, receivedEventList.size());

        assertEvent(receivedEventList.get(0), ZookeeperSync.Event.Type.add, "/");
        assertEvent(receivedEventList.get(1), ZookeeperSync.Event.Type.add, "/zookeeper");
        assertEvent(receivedEventList.get(2), ZookeeperSync.Event.Type.add, "/zookeeper/quota");

        Set<String> nodes = zookeeperSync.getNodes();
        Assert.assertEquals("sync should contain initial nodes", 3, nodes.size());
        Assert.assertTrue(nodes.contains("/"));
        Assert.assertTrue(nodes.contains("/zookeeper"));
        Assert.assertTrue(nodes.contains("/zookeeper/quota"));
    }

    @Test
    public void testAddEvent() throws Exception {

        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        final List<ZookeeperSync.Event> receivedEventList = new ArrayList<ZookeeperSync.Event>(3);

        zookeeperSync.watch();

        zookeeperSync.addListener(new ZookeeperSync.Listener() {
            @Override
            public void process(ZookeeperSync.Event e) {
                receivedEventList.add(e);
            }
        });

        zookeeperSync.create("/test/all/parent/events/received");

        // Need to ensure nodes are created before setting a listener.
        ConditionRetry.checkCondition(new ConditionRetry.Condition() {
            @Override
            public boolean check() {
                return receivedEventList.size() == 5;
            }
        });

        Assert.assertEquals("listener should receive events", 5, receivedEventList.size());

        assertEvent(receivedEventList.get(0), ZookeeperSync.Event.Type.add, "/test");
        assertEvent(receivedEventList.get(1), ZookeeperSync.Event.Type.add, "/test/all");
        assertEvent(receivedEventList.get(2), ZookeeperSync.Event.Type.add, "/test/all/parent");
        assertEvent(receivedEventList.get(3), ZookeeperSync.Event.Type.add, "/test/all/parent/events");
        assertEvent(receivedEventList.get(4), ZookeeperSync.Event.Type.add, "/test/all/parent/events/received");

        Set<String> nodes = zookeeperSync.getNodes();
        Assert.assertEquals("sync should contain initial nodes and all added nodes", 8, nodes.size());
        Assert.assertTrue(nodes.contains("/test"));
        Assert.assertTrue(nodes.contains("/test/all"));
        Assert.assertTrue(nodes.contains("/test/all/parent"));
        Assert.assertTrue(nodes.contains("/test/all/parent/events"));
        Assert.assertTrue(nodes.contains("/test/all/parent/events/received"));
    }

    @Test
    public void testUpdateEvent() throws Exception {

        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        final List<ZookeeperSync.Event> receivedEventList = new ArrayList<ZookeeperSync.Event>(3);

        zookeeperSync.watch();

        zookeeperSync.addListener(new ZookeeperSync.Listener() {
            @Override
            public void process(ZookeeperSync.Event e) {
                receivedEventList.add(e);
            }
        });

        zookeeperSync.create("/test/all/parent/events/received");

        // Need to ensure nodes are created before setting a listener.
        ConditionRetry.checkCondition(new ConditionRetry.Condition() {
            @Override
            public boolean check() {
                return receivedEventList.size() == 5;
            }
        });

        // Reset received event list.
        receivedEventList.clear();

        // TODO there may be a problem with extremely fast same node update notifications being lost
        zookeeperSync.setData("/test/all/parent/events", 0, "updated".getBytes());
        zookeeperSync.setData("/test/all/parent/events", 1, "updated again!".getBytes());

        // Wait a little while to ensure we receive events.
        ConditionRetry.checkCondition(new ConditionRetry.Condition() {
            @Override
            public boolean check() {
                return receivedEventList.size() == 2;
            }
        });

        Assert.assertEquals("listener should receive events", 2, receivedEventList.size());
        assertEvent(receivedEventList.get(0), ZookeeperSync.Event.Type.update, "/test/all/parent/events");
        assertEvent(receivedEventList.get(1), ZookeeperSync.Event.Type.update, "/test/all/parent/events");
    }

    @Test
    public void testDeleteEvent() throws Exception {

        ZookeeperSync zookeeperSync = new ZookeeperSync(client);

        final List<ZookeeperSync.Event> receivedEventList = new ArrayList<ZookeeperSync.Event>(3);

        zookeeperSync.watch();

        zookeeperSync.addListener(new ZookeeperSync.Listener() {
            @Override
            public void process(ZookeeperSync.Event e) {
                receivedEventList.add(e);
            }
        });

        zookeeperSync.create("/test/all/parent/events/received");

        // Need to ensure nodes are created before setting a listener.
        ConditionRetry.checkCondition(new ConditionRetry.Condition() {
            @Override
            public boolean check() {
                return receivedEventList.size() == 5;
            }
        });

        // Reset received event list.
        receivedEventList.clear();

        zookeeperSync.delete("/test");

        // Wait a little while to ensure we receive events.
        ConditionRetry.checkCondition(new ConditionRetry.Condition() {
            @Override
            public boolean check() {
                return receivedEventList.size() == 5;
            }
        });

        Assert.assertEquals("listener should receive events", 5, receivedEventList.size());
        assertEvent(receivedEventList.get(0), ZookeeperSync.Event.Type.delete, "/test/all/parent/events/received");
        assertEvent(receivedEventList.get(1), ZookeeperSync.Event.Type.delete, "/test/all/parent/events");
        assertEvent(receivedEventList.get(2), ZookeeperSync.Event.Type.delete, "/test/all/parent");
        assertEvent(receivedEventList.get(3), ZookeeperSync.Event.Type.delete, "/test/all");
        assertEvent(receivedEventList.get(4), ZookeeperSync.Event.Type.delete, "/test");

        Assert.assertEquals("sync should only contain initial nodes", 3, zookeeperSync.getNodes().size());
    }
}
