package io.micronaut.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeImageUtilsTest {

    @DisabledInNativeImage
    @Test
    void testInImageCode() {
        assertFalse(NativeImageUtils.inImageCode());
        assertFalse(NativeImageUtils.inImageRuntimeCode());
        assertFalse(NativeImageUtils.inImageBuildtimeCode());
    }

    @DisabledInNativeImage
    @Test
    void testJfrAvailableOnJdkWithJfrModule() {
        // The runtime without jdk.jfr is covered by JfrEventGatingTest in http-server-netty
        assertTrue(NativeImageUtils.JFR_AVAILABLE);
    }
}