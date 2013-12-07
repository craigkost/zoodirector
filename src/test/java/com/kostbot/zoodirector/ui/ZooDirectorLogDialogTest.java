package com.kostbot.zoodirector.ui;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooDirectorLogDialogTest {
    @Test
    public void testLogMessageIsDisplayed() throws Exception {
        ZooDirectorLogDialog logDialog = new ZooDirectorLogDialog();

        Logger testLogger = LoggerFactory.getLogger(ZooDirectorLogDialogTest.class);

        String testMessage = "test message";

        testLogger.error(testMessage);
        testLogger.info(testMessage);

        Matcher testErrorMessageMatcher = CoreMatchers.containsString("ERROR : " + testMessage);
        Matcher testInfoMessageMatcher = CoreMatchers.containsString("INFO  : " + testMessage);

        Assert.assertThat(logDialog.logTextArea.getText(), testErrorMessageMatcher);
        Assert.assertThat(logDialog.logTextArea.getText(), testInfoMessageMatcher);

        Assert.assertThat(logDialog.lastLogTextField.getText(), testInfoMessageMatcher);
    }
}
