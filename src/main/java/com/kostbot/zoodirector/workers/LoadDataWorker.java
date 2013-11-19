package com.kostbot.zoodirector.workers;

import com.kostbot.zoodirector.ZookeeperSync;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class LoadDataWorker extends SwingWorker<Void, Void> {
    private static final Logger logger = LoggerFactory.getLogger(LoadDataWorker.class);

    private final ZookeeperSync zookeeperSync;

    private final Callback callback;
    private final String path;

    private Stat stat;
    private byte[] data;

    public interface Callback {
        void onComplete(String path, Stat stat, byte[] data);
    }

    /**
     * Create a LoadDataWorker for fetching data/stat from zookeeper. On completion callback.execute() is called on the
     * EDT. If the worker is cancelled the callback will not be executed.
     *
     * @param zookeeperSync
     * @param path
     * @param callback
     */
    public LoadDataWorker(ZookeeperSync zookeeperSync, String path, Callback callback) {
        this.zookeeperSync = zookeeperSync;
        this.path = path;
        this.callback = callback;
    }

    @Override
    protected Void doInBackground() {
        stat = null;
        data = null;

        if (path == null) {
            return null;
        }

        logger.debug("load {} requested", path);

        try {
            stat = zookeeperSync.getStat(path);
            data = zookeeperSync.getData(path);
        } catch (Exception e) {
            logger.error("load {} failed [{}]", path, e.getMessage());
            stat = null;
            data = null;
        }
        return null;
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            logger.debug("load {} cancelled", path);
        } else {
            logger.debug("load {} complete", path);
            if (callback != null) {
                callback.onComplete(path, stat, data);
            }
        }
    }
}