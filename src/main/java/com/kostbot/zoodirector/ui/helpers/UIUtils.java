package com.kostbot.zoodirector.ui.helpers;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class UIUtils {
    static final Color COLOR_CONDITION_NOT_MET = Color.RED;
    static final Color COLOR_CONDITION_MET = Color.BLACK;

    public static interface Condition {
        boolean isMet();
    }

    public static void highlightIfConditionMet(final JTextComponent textField, Condition condition) {
        if (condition.isMet()) {
            textField.setForeground(COLOR_CONDITION_MET);
        } else {
            textField.setForeground(COLOR_CONDITION_NOT_MET);
        }
    }

    public static void highlightIfConditionMetOnUpdate(final JTextComponent textField, final Condition condition) {

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                highlightIfConditionMet(textField, condition);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                highlightIfConditionMet(textField, condition);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                highlightIfConditionMet(textField, condition);
            }
        });

        highlightIfConditionMet(textField, condition);
    }

    private static final int UNIT_SIZE = 1024;
    private static final String UNIT_PREFIXES = "KMGTPE";

    public static String humanReadableByteCount(int numberOfBytes) {
        if (numberOfBytes < UNIT_SIZE) {
            return numberOfBytes + " bytes";
        }
        int exp = (int) (Math.log(numberOfBytes) / Math.log(UNIT_SIZE));
        char unitPrefix = UNIT_PREFIXES.charAt(exp - 1);
        return String.format("%.2f %sB", numberOfBytes / Math.pow(UNIT_SIZE, exp), unitPrefix);
    }
}
