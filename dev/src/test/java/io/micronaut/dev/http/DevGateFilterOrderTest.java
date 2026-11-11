package io.micronaut.dev.http;

import io.micronaut.http.filter.ServerFilterPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevGateFilterOrderTest {

    @Test
    void theGateRunsAfterTheSecurityPhase() {
        DevGateFilter filter = new DevGateFilter(null, null);
        assertEquals(ServerFilterPhase.SECURITY.after(), filter.getOrder());
        assertTrue(filter.getOrder() > ServerFilterPhase.SECURITY.order());
    }
}
