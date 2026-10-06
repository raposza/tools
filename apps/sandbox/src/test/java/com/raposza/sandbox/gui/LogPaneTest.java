// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.Test;

/**
 * Lines appended off the event thread land as ONE document insert, in the
 * order they were appended, and a line appended on the event thread lands
 * after everything queued before it - A-63.
 *
 * THE CONTROL CAN FAIL: the pane before 2026-10-04 made one insert per line,
 * so the insert count below would read 200, not 1.
 *
 * Author Claude/bentzn
 */
class LogPaneTest {

    private static final int CNT_LINE = 200;


    @Test
    void aBurstOffTheEventThreadIsOneInsertInOrder() throws Exception {
        LogPane pane = new LogPane();
        JTextComponent area = areaOf(pane);
        AtomicInteger cntInsert = new AtomicInteger();
        area.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent evt) {
                cntInsert.incrementAndGet();
            }


            @Override
            public void removeUpdate(DocumentEvent evt) {
            }


            @Override
            public void changedUpdate(DocumentEvent evt) {
            }
        });

        Thread thread = new Thread(() -> {
            for (int idx = 0; idx < CNT_LINE; idx++) {
                pane.append("line " + idx);
            }
        }, "log-burst");
        thread.start();
        thread.join();

        // The flush timer is 40 ms; the pending events are drained after it.
        Thread.sleep(200L);
        SwingUtilities.invokeAndWait(() -> {
        });

        StringBuilder bld = new StringBuilder();
        for (int idx = 0; idx < CNT_LINE; idx++) {
            bld.append("line ").append(idx).append('\n');
        }
        assertEquals(bld.toString(), area.getText());
        assertEquals(1, cntInsert.get(), "inserts into the document");
    }


    @Test
    void anAppendOnTheEventThreadLandsAfterWhatWasQueued() throws Exception {
        LogPane pane = new LogPane();
        JTextComponent area = areaOf(pane);

        Thread thread = new Thread(() -> pane.append("first"), "log-one");
        thread.start();
        thread.join();
        SwingUtilities.invokeAndWait(() -> pane.append("second"));

        assertEquals("first\nsecond\n", area.getText());
        assertTrue(!pane.isEmpty());
    }


    private static JTextComponent areaOf(LogPane pane) throws Exception {
        java.lang.reflect.Field field = LogPane.class.getDeclaredField("areaLog");
        field.setAccessible(true);
        return (JTextComponent) field.get(pane);
    }

}
