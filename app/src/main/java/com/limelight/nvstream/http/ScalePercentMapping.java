package com.limelight.nvstream.http;

/** Compatibility with Foundation v2026.823's incorrect relative-DPI index table.
 * Windows -3/-4 DPI APIs use standard desktop scale indices, not phone DPI steps.
 * Activate only when the host advertises the distinctive legacy 120% step.
 */
public final class ScalePercentMapping {
    private static final int[] WIRE = {100,120,125,140,150,160,175,180,200,225,250,300};
    private static final int[] WINDOWS = {100,125,150,175,200,225,250,300,350,400,450,500};
    public static boolean needsCompatibility(int[] options) {
        for (int value : options) if (value == 120) return true;
        return false;
    }
    public static int encode(int actual, boolean legacy) {
        if (!legacy) return actual;
        for(int i=0;i<WINDOWS.length;i++) if(WINDOWS[i]==actual) return WIRE[i];
        throw new IllegalArgumentException("Unsupported actual scale: " + actual);
    }
    public static int decode(int wire, boolean legacy) {
        if (!legacy) return wire;
        for(int i=0;i<WIRE.length;i++) if(WIRE[i]==wire) return WINDOWS[i];
        throw new IllegalArgumentException("Unrecognized Sunshine scale: " + wire);
    }
}
