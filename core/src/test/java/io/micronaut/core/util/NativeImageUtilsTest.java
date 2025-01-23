package io.micronaut.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NativeImageUtilsTest {

    @DisabledInNativeImage
    @Test
    void testInImageCode() {
        assertFalse(NativeImageUtils.inImageCode());
        assertFalse(NativeImageUtils.inImageRuntimeCode());
        assertFalse(NativeImageUtils.inImageBuildtimeCode());
    }
}