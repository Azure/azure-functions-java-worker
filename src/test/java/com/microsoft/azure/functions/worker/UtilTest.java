package com.microsoft.azure.functions.worker;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilTest {

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