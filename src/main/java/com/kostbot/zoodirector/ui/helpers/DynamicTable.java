package com.kostbot.zoodirector.ui.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.*;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class DynamicTable {
    private static final Logger logger = LoggerFactory.getLogger(DynamicTable.class);

    public interface Callback {
        void execute(int row);
    }

    /**
     * Delete the selected rows from the table model raking into account the sort order.
     *
     * @param table
     */
    public static void removeSelectedRows(JTable table) {
        removeSelectedRows(table, null);
    }

    /**
     * Delete the selected rows from the table model raking into account the sort order. Executes the callback prior to
     * each delete.
     *
     * @param table
     * @param callback executed prior to each row deletion.
     */
    public static void removeSelectedRows(JTable table, Callback callback) {

        int[] rows = table.getSelectedRows();

        // Return early to avoid unnecessary object creation.
        if (rows.length == 0) {
            return;
        }

        Set<Integer> realRows = new TreeSet<Integer>(Collections.reverseOrder());

        for (int i = 0; i < rows.length; i++) {
            realRows.add(table.convertRowIndexToModel(rows[i]));
        }

        DefaultTableModel tableModel = (DefaultTableModel) table.getModel();
        for (int row : realRows) {
            if (callback != null) {
                callback.execute(row);
            }
            logger.debug("delete row {} : {}", row, tableModel.getValueAt(row, 0));
            tableModel.removeRow(row);
        }
    }
}
