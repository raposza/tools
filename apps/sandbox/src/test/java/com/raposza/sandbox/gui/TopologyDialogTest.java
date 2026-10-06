// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.DiscoveryDoc;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The topology question is an APPLICATION WINDOW - his report of 2026-10-04,
 * "This dialog is not an app window. Does not show up in the panel below."
 *
 * Author Claude/bentzn
 */
class TopologyDialogTest {

    /** Both answers map to the discovery document's words; anything else is a refusal. */
    @Test
    void answersMapToTheDiscoveryWords() {
        assertEquals(DiscoveryDoc.STR_TOPOLOGY_SANDBOX, TopologyDialog.strTopologyOf("Single participant"));
        assertEquals(DiscoveryDoc.STR_TOPOLOGY_LOCALNET, TopologyDialog.strTopologyOf("LocalNetND"));
        assertNull(TopologyDialog.strTopologyOf(null));
        assertNull(TopologyDialog.strTopologyOf(JOptionPane.UNINITIALIZED_VALUE));
    }


    /**
     * A FRAME WITH NO OWNER is what the panel lists. The dialog it replaces was
     * owned by Swing's hidden shared frame and so marked transient for it.
     */
    @Test
    void theQuestionIsAnOwnerlessNormalFrame() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "no display");
        AtomicReference<JFrame> refFrame = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> refFrame.set(TopologyDialog.frmBuild(null)));
        JFrame frm = refFrame.get();
        try {
            assertNull(frm.getOwner());
            assertEquals(Window.Type.NORMAL, frm.getType());
            assertEquals("Raposza Sandbox", frm.getTitle());
            assertEquals(JFrame.DO_NOTHING_ON_CLOSE, frm.getDefaultCloseOperation());
        }
        finally {
            SwingUtilities.invokeAndWait(frm::dispose);
        }
    }

}
