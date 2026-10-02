package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MrsDrawingStoreCodecTest {

    @Test
    public void drawingRoundTripPreservesPersistentFields() {
        MrsDrawing input = new MrsDrawing("test-id");
        input.label = "Mrs Test";
        input.originLat = 49.1;
        input.originLon = 8.2;
        input.targetLat = 49.2;
        input.targetLon = 8.4;
        input.fillColor = 0x55112233;
        input.showHalfKm = false;
        input.showBracket = false;
        input.visible = false;

        String json = MrsDrawingStore.encodeDrawings(
                Arrays.asList(input)
        );
        assertTrue(MrsDrawingStore.isSnapshotValid(json));

        List<MrsDrawing> decoded =
                MrsDrawingStore.decodeDrawings(json);
        assertEquals(1, decoded.size());

        MrsDrawing output = decoded.get(0);
        assertEquals("test-id", output.id);
        assertEquals("Mrs Test", output.label);
        assertEquals(49.1, output.originLat, 0.0);
        assertEquals(8.4, output.targetLon, 0.0);
        assertEquals(0x55112233, output.fillColor);
        assertFalse(output.showHalfKm);
        assertFalse(output.showBracket);
        assertFalse(output.visible);
    }

    @Test
    public void malformedSnapshotIsRejected() {
        assertFalse(MrsDrawingStore.isSnapshotValid("{broken"));
        assertEquals(
                0,
                MrsDrawingStore.decodeDrawings("{broken").size()
        );
    }

    @Test
    public void historyCodecKeepsOnlyValidSnapshots() {
        String valid = "[]";
        String encoded = MrsDrawingStore.encodeHistory(
                Arrays.asList(valid, "{broken")
        );
        List<String> decoded =
                MrsDrawingStore.decodeHistory(encoded);
        assertEquals(1, decoded.size());
        assertEquals(valid, decoded.get(0));
    }
}
