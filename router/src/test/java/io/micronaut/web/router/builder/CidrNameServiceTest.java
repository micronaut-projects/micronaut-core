/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router.builder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An address a request carries is parsed as a literal, never looked up.
 */
class CidrNameServiceTest {

    @Test
    void aValueThatIsNotALiteralIsNotLookedUp() {
        RecordingInetAddressResolverProvider.LOOKUPS.clear();
        for (String value : List.of(".::1", ".1:2", "[.::1]", ".a:b:c")) {
            assertNull(Cidr.address(value), value);
        }
        assertEquals(List.of(), RecordingInetAddressResolverProvider.LOOKUPS);
    }

    @Test
    void anIpv6LiteralIsStillParsed() {
        byte[] loopback = new byte[16];
        loopback[15] = 1;
        assertArrayEquals(loopback, Cidr.address("::1"));
        assertArrayEquals(loopback, Cidr.address("[::1]:8080"));
    }
}
