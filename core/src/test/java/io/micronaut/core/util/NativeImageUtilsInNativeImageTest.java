package io.micronaut.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.condition.EnabledInNativeImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeImageUtilsInNativeImageTest {
    
    @EnabledInNativeImage
    @Test
    void testInImageCode() {
        assertTrue(NativeImageUtils.inImageCode());
    }
}