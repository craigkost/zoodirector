package com.kostbot.zoodirector.zookeepersync;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.common.PathUtils;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class used to synchronize all node created, deleted, child changed and updated events for a Zookeeper cluster. It
 * reduces all of these Zookeeper events into 3 simple cases node creation, node deletion and node update for all
 * nodes in a Zookeeper cluster.
 */
public class ZookeeperSync {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperSync.class);

    public static interface Listener {
        public void process(ZookeeperSync.Event e);
    }

    public static class Event {
        public final Type type;
        public final String path;

        private Event(Type type, String path) {
            this.type = type;
            this.path = path;
        }

        private static Event Add(String path) {
            return new Event(Type.add, path);
        }

        private static Event Update(String path) {
            return new Event(Type.update, path);
        }

        private static Event Delete(String path) {
            return new Event(Type.delete, path);
        }

        @Override
        public String toString() {
            return type + " " + path;
        }

        public static enum Type {
            add,
            update,
            delete
        }
    }

    /**
     * Get the parent path for the given path. Assumes path is a valid path format.
     *
     * @param path
     * @return parent path, null if path is root "/"
     */
    public static String getParent(String path) {
        if ("/".equals(path)) {
            return null;
        }

        int lastSegmentIndex = path.lastIndexOf("/");
        if (lastSegmentIndex == 0) {
            return "/";
        }
        return path.substring(0, lastSegmentIndex);
    }

    public static boolean isValidPath(String path, boolean allowSubPaths) {
        if (allowSubPaths && !path.startsWith("/") && !path.equals("")) {
            path = "/" + path;
        }
        try {
            PathUtils.validatePath(path);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isValidSubPath(String path) {
        return isValidPath(path, true);
    }

    public static boolean isValidPath(String path) {
        return isValidPath(path, false);
    }

    /**
     * Simple watcher used to map Zookeeper events to only 3 node event types: creates, updates, and deletes.
     */
    private class NodeWatcher implements CuratorWatcher {
        ZookeeperSync zookeeperSync;

        NodeWatcher(ZookeeperSync zookeeperSync) {
            this.zookeeperSync = zookeeperSync;
        }

        @Override
        public void process(WatchedEvent event) throws Exception {
            String path = event.getPath();

            switch (event.getType()) {
                case NodeDeleted:
                    zookeeperSync.handleNodeDeletedEvent(path);
                    break;
                case NodeCreated:
                    zookeeperSync.handleNodeCreatedEvent(path);
                    break;
                case NodeDataChanged:
                    // Note: updates are missed if they occur immediately after node creation because of the latency
                    // required for setting up the data watcher.
                    zookeeperSync.handleNodeDataChangedEvent(path);
                    break;
                case NodeChildrenChanged:
                    zookeeperSync.handleNodeChildrenChangedEvent(path);
                    break;
            }
        }
    }

    private final List<Listener> listeners; // Need to synchronize access

    private final Set<String> nodes; // Need to synchronize access
    private final NodeWatcher watcher;

    private final CuratorFramework client;

    public ZookeeperSync(CuratorFramework client) {
        this.client = client;

        watcher = new NodeWatcher(this);
        nodes = new HashSet<String>(100);
        listeners = new ArrayList<Listener>();
    }

    /**
     * Add listener for sync events.
     *
     * @param listener
     */
    public void addListener(Listener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    /**
     * Get the set of all current nodes.
     *
     * @return a read-only copy of the node set
     */
    public Set<String> getNodes() {
        synchronized (nodes) {
            return Collections.unmodifiableSet(nodes);
        }
    }

    /**
     * Handle NodeCreated event for the given path.
     *
     * @param path
     * @throws Exception
     */
    private void handleNodeCreatedEvent(String path) throws Exception {
        try {
            client.checkExists().usingWatcher(watcher).forPath(path);
            synchronized (nodes) {
                if (nodes.add(path)) {
                    notify(Event.Add(path));
                }
            }
        } catch (KeeperException.NoNodeException e) {
            logger.error("{} deleted before its time", path);
        }

        handleNodeChildrenChangedEvent(path);
    }

    /**
     * Handle NodeChildrenChanged event for the given path.
     *
     * @param path
     * @throws Exception
     */
    private void handleNodeChildrenChangedEvent(String path) throws Exception {
        try {
            for (String child : client.getChildren().usingWatcher(watcher).forPath(path)) {
                handleNodeCreatedEvent((path.equals("/") ? "/" : path + "/") + child);
            }
        } catch (KeeperException.NoNodeException e) {
            // node may have been deleted
        } catch (KeeperException.NoAuthException e) {
        	logger.error("Ignoring no auth: " + e); // No stack trace.
        }
    }

    /**
     * Handle NodeDataChanged event for the given path.
     *
     * @param path
     */
    private void handleNodeDataChangedEvent(String path) throws Exception {
        try {
            client.checkExists().usingWatcher(watcher).forPath(path);
        } catch (KeeperException.NoNodeException e) {
            // node may have been deleted
        }
        notify(Event.Update(path));
    }

    /**
     * Handle NodeDeleted event for the given path.
     *
     * @param path
     */
    private void handleNodeDeletedEvent(String path) {
        synchronized (nodes) {
            if (nodes.remove(path)) {
                notify(Event.Delete(path));
            }
        }
    }

    /**
     * Generic notification interface.
     *
     * @param event
     */
    private void notify(Event event) {
        synchronized (listeners) {
            logger.debug("notify [{}] {}", event.type, event.path);
            for (Listener listener : listeners) {
                listener.process(event);
            }
        }
    }

    /**
     * Create the given zookeeper path including all non-existent parent nodes.
     *
     * @param path
     * @param createMode
     * @return true if path was created, false otherwise
     * @throws Exception
     */
    public boolean create(String path, CreateMode createMode) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().withMode(createMode).forPath(path);
            return true;
        }
        return false;
    }

    /**
     * Create the given zookeeper path including all non-existent parent nodes.
     *
     * @param path
     * @return true if path was created, false otherwise
     * @throws Exception
     */
    public boolean create(String path) throws Exception {
        return create(path, CreateMode.PERSISTENT);
    }

    /**
     * Delete the given node, its descendants, and any node ancestors with only a single child.
     *
     * @param path
     * @throws Exception
     */
    public String prune(String path) throws Exception {
        if ("/".equals(path)) {
            throw new IllegalArgumentException("cannot prune root node");
        }

        if (client.checkExists().forPath(path) == null) {
            return null;
        }

        String parent;

        // Determine oldest lonely ancestor.
        while (!"/".equals(parent = ZookeeperSync.getParent(path)) && client.getChildren().forPath(parent).size() == 1) {
            path = parent;
        }

        delete(path);

        return parent;
    }

    /**
     * Delete the given node and all its descendants.
     *
     * @param path
     * @throws Exception
     */
    public void delete(String path) throws Exception {
        if ("/".equals(path)) {
            throw new IllegalArgumentException("cannot delete root node");
        }

        // Delete children
        trim(path);

        if (client.checkExists() != null) {
            client.delete().forPath(path);
        }
    }

    /**
     * Delete all of the node's children and their ancestors.
     *
     * @param path
     * @throws Exception
     */
    public void trim(String path) throws Exception {
        for (String child : client.getChildren().forPath(path)) {
            try {
                delete(("/".equals(path) ? "/" : path + "/") + child);
            } catch (KeeperException.BadArgumentsException e) {
                logger.error(e.getMessage());
            }
        }
    }

    /**
     * Get the Stat object of the given path.
     *
     * @param path
     * @return
     * @throws Exception
     */
    public Stat getStat(String path) throws Exception {
        return client.checkExists().forPath(path);
    }

    /**
     * Get data for the given path.
     *
     * @param path
     * @return
     * @throws Exception
     */
    public byte[] getData(String path) throws Exception {
        return client.getData().forPath(path);
    }

    /**
     * Set data for the given path.
     *
     * @param path
     * @param data
     * @throws Exception
     */
    public void setData(String path, int version, byte[] data) throws Exception {
        client.setData().withVersion(version).forPath(path, data);
    }

    /**
     * Begin watching the zookeeper cluster. Starts by loading the cluster from its root. This will trigger add events
     * for each node found while initializing the cluster sync.
     *
     * @throws Exception
     */
    public void watch() throws Exception {
        synchronized (nodes) {
            nodes.clear();
        }
        handleNodeCreatedEvent("/");
    }
}
