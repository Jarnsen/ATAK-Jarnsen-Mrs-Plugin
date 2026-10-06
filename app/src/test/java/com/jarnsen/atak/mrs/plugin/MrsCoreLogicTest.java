package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void gridDirectionSnapsToNearestFiftyMilAndWrapsAtNorth() {
        assertEquals(0, MrsCoreLogic.snapMilToStep(0, 50));
        assertEquals(0, MrsCoreLogic.snapMilToStep(24, 50));
        assertEquals(50, MrsCoreLogic.snapMilToStep(26, 50));
        assertEquals(6350, MrsCoreLogic.snapMilToStep(6374, 50));
        assertEquals(0, MrsCoreLogic.snapMilToStep(6375, 50));
        assertEquals(50, MrsCoreLogic.snapMilToStep(6450, 50));
        assertEquals(2.8125, MrsCoreLogic.milToDegrees(50), 0.000001);
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

    @Test
    public void annotationAppearsWhenMeasuredTextFitsAvailableLine() {
        double requiredMeters = MrsCoreLogic.annotationLengthMeters(
                100.0,
                16.0,
                2.0
        );
        assertEquals(264.0, requiredMeters, 0.0);
        assertTrue(MrsCoreLogic.annotationFits(
                requiredMeters,
                100.0,
                16.0,
                2.0
        ));
        org.junit.Assert.assertFalse(MrsCoreLogic.annotationFits(
                requiredMeters - 0.1,
                100.0,
                16.0,
                2.0
        ));
        org.junit.Assert.assertFalse(MrsCoreLogic.annotationFits(
                Double.POSITIVE_INFINITY,
                100.0,
                16.0,
                2.0
        ));
    }

    @Test
    public void negativeAndWrappedDegreesStayInsideOneCircle() {
        assertEquals(4800, MrsCoreLogic.degreesToMil(-90.0));
        assertEquals(0, MrsCoreLogic.degreesToMil(-360.0));
        assertEquals(1600, MrsCoreLogic.degreesToMil(450.0));
        // 359.99 degrees rounds up to a full circle and wraps to 0.
        assertEquals(0, MrsCoreLogic.degreesToMil(359.99));
        assertEquals(0, MrsCoreLogic.degreesToMil(Double.NaN));
    }

    @Test
    public void mgrsZoneMustBeBetween1And60() {
        assertTrue(MrsCoreLogic.isStructurallyValidMgrs("1CAA"));
        assertTrue(MrsCoreLogic.isStructurallyValidMgrs("01CAA"));
        assertTrue(MrsCoreLogic.isStructurallyValidMgrs("32UMV1234567890"));
        assertTrue(MrsCoreLogic.isStructurallyValidMgrs("60UMV1234567890"));
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("00UMV1234567890"));
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("61UMV1234567890"));
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("99UMV1234567890"));
    }

    @Test
    public void mgrsRejectsOddDigitCountsAndInvalidLetters() {
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("32UMV123456789"));
        assertTrue(MrsCoreLogic.isStructurallyValidMgrs("32UMV123678"));
        // 100 km row letters only run from A to V.
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("32UMW1234567890"));
        // I and O are never used as band or square letters.
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("32IMV1234567890"));
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs("32UOV1234567890"));
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs(""));
        assertFalse(MrsCoreLogic.isStructurallyValidMgrs(null));
    }
}
