package com.kostbot.zoodirector.ui.helpers;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class UIUtilsTest {
    @Test
    public void testHighlightIfConditionMet() {
        UIUtils.Condition condition = Mockito.mock(UIUtils.Condition.class);

        JTextField textField = new JTextField();
        textField.setForeground(Color.BLUE);

        Mockito.when(condition.isMet()).thenReturn(true);
        UIUtils.highlightIfConditionMet(textField, condition);
        Assert.assertEquals(UIUtils.COLOR_CONDITION_MET, textField.getForeground());

        Mockito.when(condition.isMet()).thenReturn(false);
        UIUtils.highlightIfConditionMet(textField, condition);
        Assert.assertEquals(UIUtils.COLOR_CONDITION_NOT_MET, textField.getForeground());

        Mockito.verify(condition, Mockito.times(2)).isMet();
    }

    @Test
    public void testHighlightOnConditionChange() throws Exception {
        UIUtils.Condition condition = Mockito.mock(UIUtils.Condition.class);

        JTextField textField = new JTextField();
        UIUtils.highlightIfConditionMetOnUpdate(textField, condition);

        Robot robot = new Robot();
        textField.grabFocus();

        int VISIBLE_KEY = KeyEvent.VK_A;
        robot.keyPress(VISIBLE_KEY);
        robot.keyRelease(VISIBLE_KEY);

        Mockito.verify(condition, Mockito.times(1)).isMet();

        int CONTROL_KEY = KeyEvent.VK_ENTER;
        robot.keyPress(CONTROL_KEY);
        robot.keyRelease(CONTROL_KEY);

        Mockito.verifyNoMoreInteractions(condition);
    }
}
