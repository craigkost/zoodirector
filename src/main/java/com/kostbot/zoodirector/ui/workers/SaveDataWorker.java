package com.kostbot.zoodirector.ui.workers;

import com.kostbot.zoodirector.zookeepersync.ZookeeperSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class SaveDataWorker extends SwingWorker<Void, Void> {
    private static final Logger logger = LoggerFactory.getLogger(SaveDataWorker.class);

    private final ZookeeperSync zookeeperSync;
    private final String path;
    private final int version;
    private final byte[] data;
    private final Callback callback;

    private boolean success;

    /**
     * Create a SaveDataWorker for saving data to zookeeper. On completion callback.execute() is called on the EDT. If
     * the worker is cancelled the callback will not be executed.
     *
     * @param zookeeperSync
     * @param path
     * @param data
     * @param callback
     */
    public SaveDataWorker(ZookeeperSync zookeeperSync, String path, int version, byte[] data, Callback callback) {
        this.zookeeperSync = zookeeperSync;
        this.path = path;
        this.version = version;
        this.data = data;
        this.callback = callback;
    }

    public interface Callback {
        void onComplete(String path);

        void onFailure(String path);
    }

    @Override
    protected Void doInBackground() {
        logger.debug("save {} requested", path);

        try {
            zookeeperSync.setData(path, version, data);
            success = true;
        } catch (Exception e) {
            logger.error("save {} failed [{}]", path, e.getMessage());
        }
        return null;
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            logger.debug("save {} cancelled", path);
        } else {
            logger.debug("save {} complete", path);
            if (callback != null) {
                if (success) {
                    callback.onComplete(path);
                } else {
                    callback.onFailure(path);
                }
            }
        }
    }
}