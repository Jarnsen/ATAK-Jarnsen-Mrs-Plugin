package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MrsHistoryTest {

    @Test
    public void undoRedoRoundTripWorks() {
        MrsHistory history = new MrsHistory(10);
        history.push("A");
        history.push("B");

        assertEquals("B", history.undo("C"));
        assertEquals("C", history.redo("B"));
    }

    @Test
    public void newEditClearsRedoAndHistoryIsBounded() {
        MrsHistory history = new MrsHistory(3);
        history.push("A");
        history.push("B");
        history.push("C");
        history.push("D");
        assertEquals(3, history.undoSize());

        assertEquals("D", history.undo("E"));
        history.push("F");
        assertEquals(0, history.redoSize());
        assertNull(history.redo("F"));
    }

    @Test
    public void persistedHistoryCanBeRestored() {
        MrsHistory history = new MrsHistory(10);
        history.restore(
                Arrays.asList("A", "B"),
                Arrays.asList("C")
        );
        assertEquals(2, history.undoSize());
        assertEquals(1, history.redoSize());
    }

    @Test
    public void cancelledEditDiscardsUnchangedUndoSnapshot() {
        MrsHistory history = new MrsHistory(10);
        assertTrue(history.push("current"));
        assertTrue(history.discardLastUndoIfEquals("current"));
        assertEquals(0, history.undoSize());
        assertFalse(history.discardLastUndoIfEquals("current"));
    }
}
