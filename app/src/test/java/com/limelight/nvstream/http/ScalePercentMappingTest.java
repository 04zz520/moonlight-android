package com.limelight.nvstream.http;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScalePercentMappingTest {
    @Test public void detectsLegacyOnly() {
        assertTrue(ScalePercentMapping.needsCompatibility(new int[]{100,120,125,140,150}));
        assertFalse(ScalePercentMapping.needsCompatibility(new int[]{100,125,150,175,200}));
    }
    @Test public void correctsObserved150ReportedAs125() {
        assertEquals(150,ScalePercentMapping.decode(125,true));
        assertEquals(125,ScalePercentMapping.encode(150,true));
    }
    @Test public void roundTripAllWindowsSteps() {
        for(int value:new int[]{100,125,150,175,200,225,250,300,350,400,450,500})
            assertEquals(value,ScalePercentMapping.decode(ScalePercentMapping.encode(value,true),true));
    }
    @Test public void fixedSunshineIsNotRemapped() {
        assertEquals(175,ScalePercentMapping.encode(175,false));
        assertEquals(175,ScalePercentMapping.decode(175,false));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnknownLegacyStep() {
        ScalePercentMapping.decode(350,true);
    }
}
