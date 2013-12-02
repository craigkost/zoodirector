package com.kostbot.zoodirector.ui;

import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.Date;

import static org.mockito.Mockito.*;

public class ZooDirectorAddressPanelTest {
    public void simulateKey(Component c, KeyEvent e) throws Exception {
        Field f = AWTEvent.class.getDeclaredField("focusManagerIsDispatching");
        f.setAccessible(true);
        f.set(e, Boolean.TRUE);
        c.dispatchEvent(e);
    }

    @Test
    public void testSetPath() throws Exception {
        ZooDirectorAddressPanel addressPanel = new ZooDirectorAddressPanel(null);

        String validPath = "/valid";
        addressPanel.setPath(validPath);
        Assert.assertEquals("path should be exactly as set", validPath, addressPanel.addressField.getText());
        Assert.assertEquals(Color.BLACK, addressPanel.addressField.getForeground());

        String inValidPath = "/valid/";
        addressPanel.setPath(inValidPath);
        Assert.assertEquals("path should be exactly as set", inValidPath, addressPanel.addressField.getText());
        Assert.assertEquals(Color.RED, addressPanel.addressField.getForeground());
    }

    @Test
    public void testGoToOnEnterKey() throws Exception {
        ZooDirectorPanel zooDirectorPanel = mock(ZooDirectorPanel.class);

        ZooDirectorAddressPanel addressPanel = new ZooDirectorAddressPanel(zooDirectorPanel);

        String validPath = "/valid";
        addressPanel.setPath(validPath);
        simulateKey(addressPanel.addressField, new KeyEvent(addressPanel, KeyEvent.KEY_RELEASED, new Date().getTime(), 0, KeyEvent.VK_ENTER, ' '));
        verify(zooDirectorPanel, times(1)).viewEditTreeNode(validPath);

        String invalidPath = "/valid/";
        addressPanel.setPath(invalidPath);
        simulateKey(addressPanel.addressField, new KeyEvent(addressPanel, KeyEvent.KEY_RELEASED, new Date().getTime(), 0, KeyEvent.VK_ENTER, ' '));
        verify(zooDirectorPanel, times(1)).viewEditTreeNode(validPath);

        verifyNoMoreInteractions(zooDirectorPanel);
    }
}
