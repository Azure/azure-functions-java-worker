package com.microsoft.azure.webjobs.script.test;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;

public class IntegrationIT {

    @Test
    public void slowTest() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(true);
    }
}
