package io.micronaut.core.util;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {
    @Test
    void byteCount() {
        StringBuilder sb = new StringBuilder();
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length < 4096) {
            sb.append('a');
        }
        assertEquals(4096, StringUtils.utf8Bytes(sb.toString()));
    }
}
