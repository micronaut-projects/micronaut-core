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
package io.micronaut.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecFetchTest {

    @Test
    void parsesFetchMetadataHeaders() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.SAME_ORIGIN.toString())
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.CORS.toString())
            .header(HttpHeaders.SEC_FETCH_DEST, Destination.JSON.toString())
            .header(HttpHeaders.SEC_FETCH_USER, "?1");
        assertEquals(new SecFetch(Site.SAME_ORIGIN, Mode.CORS, Destination.JSON, true), request.getSecFetch());
        assertEquals(new SecFetch(Site.SAME_ORIGIN, Mode.CORS, Destination.JSON, true), SecFetch.of(request));
    }

    @Test
    void returnsNullOnlyWhenNoFetchMetadataHeaderIsPresent() {
        HttpRequest<?> request = HttpRequest.GET("/");

        assertNull(request.getSecFetch());
        assertNull(SecFetch.of(request));
    }

    @Test
    void aSingleFetchMetadataHeaderIsEnoughToProduceMetadata() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.CROSS_SITE.toString());

        assertEquals(new SecFetch(Site.CROSS_SITE, null, null, false), request.getSecFetch());
    }

    @Test
    void missingHeadersLeaveTheOthersReadable() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.SAME_ORIGIN.toString())
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.CORS.toString());

        assertEquals(new SecFetch(Site.SAME_ORIGIN, Mode.CORS, null, false), request.getSecFetch());
    }

    @Test
    void anUnrecognisedSiteLeavesTheModeAndDestinationReadable() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, "unknown")
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.CORS.toString())
            .header(HttpHeaders.SEC_FETCH_DEST, Destination.JSON.toString());

        assertEquals(new SecFetch(null, Mode.CORS, Destination.JSON, false), request.getSecFetch());
    }

    @Test
    void anUnrecognisedDestinationLeavesTheSiteAndModeReadable() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.CROSS_SITE.toString())
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.NAVIGATE.toString())
            .header(HttpHeaders.SEC_FETCH_DEST, "destination-added-after-this-release");

        assertEquals(new SecFetch(Site.CROSS_SITE, Mode.NAVIGATE, null, false), request.getSecFetch());
    }

    @Test
    void metadataThatIsEntirelyUnrecognisedIsStillDistinguishableFromNoMetadata() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, "unknown")
            .header(HttpHeaders.SEC_FETCH_MODE, "unknown")
            .header(HttpHeaders.SEC_FETCH_DEST, "unknown");

        assertEquals(new SecFetch(null, null, null, false), request.getSecFetch());
    }

    @Test
    void absentUserHeaderMeansRequestWasNotUserActivated() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.SAME_ORIGIN.toString())
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.CORS.toString())
            .header(HttpHeaders.SEC_FETCH_DEST, Destination.JSON.toString());

        assertEquals(new SecFetch(Site.SAME_ORIGIN, Mode.CORS, Destination.JSON, false), request.getSecFetch());
    }

    @Test
    void falseUserHeaderMeansRequestWasNotUserActivated() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.SAME_ORIGIN.toString())
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.CORS.toString())
            .header(HttpHeaders.SEC_FETCH_DEST, Destination.JSON.toString())
            .header(HttpHeaders.SEC_FETCH_USER, "?0");

        assertEquals(new SecFetch(Site.SAME_ORIGIN, Mode.CORS, Destination.JSON, false), request.getSecFetch());
    }

    @Test
    void invalidUserHeaderValueMeansRequestWasNotUserActivated() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_SITE, Site.SAME_ORIGIN.toString())
            .header(HttpHeaders.SEC_FETCH_MODE, Mode.CORS.toString())
            .header(HttpHeaders.SEC_FETCH_DEST, Destination.JSON.toString())
            .header(HttpHeaders.SEC_FETCH_USER, "true");

        assertEquals(new SecFetch(Site.SAME_ORIGIN, Mode.CORS, Destination.JSON, false), request.getSecFetch());
    }

    @Test
    void aUserHeaderOnItsOwnProducesMetadata() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.SEC_FETCH_USER, "?1");

        assertEquals(new SecFetch(null, null, null, true), request.getSecFetch());
    }
}
