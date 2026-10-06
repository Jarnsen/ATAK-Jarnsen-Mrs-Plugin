package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MrsDrawingImportTest {

    private static MrsDrawing drawing(String id) {
        MrsDrawing d = new MrsDrawing(id);
        d.label = "Mrs " + id;
        d.originLat = 49.1;
        d.originLon = 8.2;
        d.targetLat = 49.2;
        d.targetLon = 8.4;
        return d;
    }

    @Test
    public void exportedPackageCanBeImportedAgain() {
        String json = "{\"format\":\"jarnsen-mrs\",\"version\":1,\"drawings\":"
                + MrsDrawingStore.encodeDrawings(
                Arrays.asList(drawing("a"), drawing("b"))) + "}";
        List<MrsDrawing> imported = MrsDrawingStore.parseImport(json);
        assertNotNull(imported);
        assertEquals(2, imported.size());
    }

    @Test
    public void bareArrayIsAccepted() {
        List<MrsDrawing> imported = MrsDrawingStore.parseImport(
                MrsDrawingStore.encodeDrawings(Arrays.asList(drawing("a"))));
        assertNotNull(imported);
        assertEquals(1, imported.size());
    }

    @Test
    public void wrongFormatAndGarbageAreRejected() {
        assertNull(MrsDrawingStore.parseImport(null));
        assertNull(MrsDrawingStore.parseImport("   "));
        assertNull(MrsDrawingStore.parseImport("{broken"));
        assertNull(MrsDrawingStore.parseImport(
                "{\"format\":\"other\",\"drawings\":[]}"));
        assertNull(MrsDrawingStore.parseImport("42"));
    }

    @Test
    public void outOfRangeCoordinatesAreRejected() {
        MrsDrawing badLat = drawing("lat");
        badLat.originLat = 95.0;
        assertNull(MrsDrawingStore.parseImport(
                MrsDrawingStore.encodeDrawings(Arrays.asList(badLat))));

        MrsDrawing badLon = drawing("lon");
        badLon.targetLon = -181.0;
        assertNull(MrsDrawingStore.parseImport(
                MrsDrawingStore.encodeDrawings(Arrays.asList(badLon))));

        assertFalse(MrsDrawingStore.isSnapshotValid(
                MrsDrawingStore.encodeDrawings(Arrays.asList(badLat))));
    }

    @Test
    public void boundaryCoordinatesAreAccepted() {
        MrsDrawing edge = drawing("edge");
        edge.originLat = 90.0;
        edge.originLon = -180.0;
        edge.targetLat = -90.0;
        edge.targetLon = 180.0;
        assertTrue(MrsDrawingStore.isSnapshotValid(
                MrsDrawingStore.encodeDrawings(Arrays.asList(edge))));
    }

    @Test
    public void tooManyDrawingsAreRejected() {
        List<MrsDrawing> many = new ArrayList<>();
        for (int i = 0; i <= MrsDrawingStore.MAX_IMPORT_DRAWINGS; i++) {
            many.add(drawing("d" + i));
        }
        assertNull(MrsDrawingStore.parseImport(
                MrsDrawingStore.encodeDrawings(many)));

        many.remove(0);
        assertNotNull(MrsDrawingStore.parseImport(
                MrsDrawingStore.encodeDrawings(many)));
    }

    @Test
    public void oversizedInputIsRejected() {
        StringBuilder big = new StringBuilder("[");
        while (big.length() <= MrsDrawingStore.MAX_IMPORT_BYTES) {
            big.append(' ');
        }
        big.append(']');
        assertNull(MrsDrawingStore.parseImport(big.toString()));
    }

    @Test
    public void duplicateIdsInsideOneFileBecomeUnique() {
        String json = MrsDrawingStore.encodeDrawings(
                Arrays.asList(drawing("same"), drawing("same"),
                        drawing("same")));
        List<MrsDrawing> imported = MrsDrawingStore.parseImport(json);
        assertNotNull(imported);
        assertEquals(3, imported.size());
        Set<String> ids = new HashSet<>();
        for (MrsDrawing d : imported) {
            ids.add(d.id);
        }
        assertEquals(3, ids.size());
    }

    @Test
    public void emptyImportIsValidButEmpty() {
        List<MrsDrawing> imported = MrsDrawingStore.parseImport("[]");
        assertNotNull(imported);
        assertTrue(imported.isEmpty());
    }

    @Test
    public void fillAlphaIsClampedAndLongLabelsAreTruncated() {
        MrsDrawing d = drawing("clamp");
        d.fillAlpha = 999;
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            label.append('x');
        }
        d.label = label.toString();
        List<MrsDrawing> decoded = MrsDrawingStore.decodeDrawings(
                MrsDrawingStore.encodeDrawings(Arrays.asList(d)));
        assertEquals(1, decoded.size());
        assertEquals(255, decoded.get(0).fillAlpha);
        assertEquals(MrsDrawing.MAX_LABEL_LENGTH, decoded.get(0).label.length());

        d.fillAlpha = -5;
        decoded = MrsDrawingStore.decodeDrawings(
                MrsDrawingStore.encodeDrawings(Arrays.asList(d)));
        assertEquals(0, decoded.get(0).fillAlpha);
    }
}
