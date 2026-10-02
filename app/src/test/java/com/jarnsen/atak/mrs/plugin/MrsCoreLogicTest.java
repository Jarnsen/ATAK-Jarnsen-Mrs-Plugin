package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MrsCoreLogicTest {

    @Test
    public void fixedGeometryMatchesSpecification() {
        assertEquals(8000.0, MrsCoreLogic.MAX_RANGE_M, 0.0);
        assertEquals(600.0, MrsCoreLogic.HALF_SECTOR_MIL, 0.0);
        assertEquals(33.75, MrsCoreLogic.HALF_SECTOR_DEG, 0.000001);
    }

    @Test
    public void degreesConvertToNato6400Mil() {
        assertEquals(0, MrsCoreLogic.degreesToMil(0.0));
        assertEquals(1600, MrsCoreLogic.degreesToMil(90.0));
        assertEquals(3200, MrsCoreLogic.degreesToMil(180.0));
        assertEquals(4800, MrsCoreLogic.degreesToMil(270.0));
        assertEquals(0, MrsCoreLogic.degreesToMil(360.0));
    }

    @Test
    public void compactMgrsIsCanonicalized() {
        assertEquals(
                "32U MV 12345 67890",
                MrsCoreLogic.normalizeMgrsInput("32umv1234567890")
        );
        assertEquals(
                "32U MV 12345 67890",
                MrsCoreLogic.normalizeMgrsInput("32U MV 12345 67890")
        );
        assertTrue(
                MrsCoreLogic.isStructurallyValidMgrs(
                        "32UMV1234567890"
                )
        );
    }
}
