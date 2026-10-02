package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
