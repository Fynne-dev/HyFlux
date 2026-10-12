package dev.hyflux.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfTestTest {

    @Test
    void allInServerChecksPass() {
        for (SelfTest.Check check : SelfTest.run()) {
            assertTrue(check.passed(), check.name() + ": " + check.detail());
        }
    }
}
