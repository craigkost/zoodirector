package com.kostbot.zoodirector;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.RetryOneTime;
import com.netflix.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

/**
 * @author Craig Kost
 */
public class ZookeeperTestBase {
    CuratorFramework client;
    protected TestingServer server;

    @Before
    public void before() throws Exception {
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1000));
        client.start();
    }

    @After
    public void after() {
        client.close();
        try {
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
