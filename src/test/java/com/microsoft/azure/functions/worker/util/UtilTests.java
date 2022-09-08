package com.microsoft.azure.functions.worker.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UtilTests {

    @Test
    public void isTrueNull() {
        assertFalse(Util.isTrue(null));
    }

    @Test
    public void isTrueEmptyString() {
        assertFalse(Util.isTrue(""));
    }

    @Test
    public void isTrueValueTrue() {
        assertTrue(Util.isTrue("True"));
    }
}