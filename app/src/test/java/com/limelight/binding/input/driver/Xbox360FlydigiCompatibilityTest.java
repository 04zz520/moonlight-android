package com.limelight.binding.input.driver;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Xbox360FlydigiCompatibilityTest {
    @Test
    public void recognizesPreviouslyVerifiedReceiver() {
        assertTrue(Xbox360Controller.isFlydigiVader5ProReceiver(0x37d7, 0x2401));
    }

    @Test
    public void doesNotTreatEveryFlydigiProductAsThisReceiver() {
        assertFalse(Xbox360Controller.isFlydigiVader5ProReceiver(0x37d7, 0x2402));
        assertFalse(Xbox360Controller.canClaimDevice(0x37d7, 0x2402, 255, 93, 1));
    }

    @Test
    public void doesNotMatchAnotherVendorWithSameProductId() {
        assertFalse(Xbox360Controller.isFlydigiVader5ProReceiver(0x1234, 0x2401));
        assertFalse(Xbox360Controller.canClaimDevice(0x1234, 0x2401, 255, 93, 1));
    }

    @Test
    public void acceptsVerifiedXinputInterface() {
        assertTrue(Xbox360Controller.canClaimDevice(0x37d7, 0x2401, 255, 93, 1));
    }

    @Test
    public void rejectsAuxiliaryHidInterface() {
        assertFalse(Xbox360Controller.canClaimDevice(0x37d7, 0x2401, 3, 93, 1));
    }

    @Test
    public void rejectsWrongSubclass() {
        assertFalse(Xbox360Controller.canClaimDevice(0x37d7, 0x2401, 255, 0, 1));
    }

    @Test
    public void rejectsWrongProtocol() {
        assertFalse(Xbox360Controller.canClaimDevice(0x37d7, 0x2401, 255, 93, 2));
    }

    @Test
    public void keepsExistingMicrosoftAndEightBitDoSupport() {
        assertTrue(Xbox360Controller.canClaimDevice(0x045e, 0x028e, 255, 93, 1));
        assertTrue(Xbox360Controller.canClaimDevice(0x2dc8, 0x3106, 255, 93, 1));
    }

    @Test
    public void keepsInterfaceGuardsForExistingVendors() {
        assertFalse(Xbox360Controller.canClaimDevice(0x045e, 0x028e, 3, 93, 1));
        assertFalse(Xbox360Controller.canClaimDevice(0x045e, 0x028e, 255, 93, 129));
    }
}
