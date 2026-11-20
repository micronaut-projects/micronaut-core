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
package io.micronaut.http.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The path and query transforms: they work on the raw path and query, which they never decode
 * and encode again.
 */
class PathTransformsTest extends GatewayTestBase {

    static final String SPEC = "PathTransformsTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        String r = "micronaut.gateway.routes.";
        // StripPrefix without an argument removes one segment
        p.put(r + "strip-default.uri", "${test.upstream}");
        p.put(r + "strip-default.predicates", List.of("Path=/sd/inspect/**"));
        p.put(r + "strip-default.filters", List.of("StripPrefix"));
        p.put(r + "rewrite.uri", "${test.upstream}");
        p.put(r + "rewrite.predicates", List.of("Path=/rw/**"));
        p.put(r + "rewrite.filters", List.of("RewritePath=/rw/(?<segment>.*), /inspect/rewritten/$\\{segment}"));
        return p;
    }

    @Test
    void pt01StripPrefixRemovesSegmentsAndABodyStillPasses() throws IOException {
        assertEquals("/inspect/x/y", get("/api/v1/inspect/x/y").inspected("path"));
        RawHttp.Response post = send("POST", "/api/v1/inspect/x", "payload", "Content-Type: text/plain");
        assertEquals("/inspect/x", post.inspected("path"));
        assertEquals("payload", post.inspected("body"));
        assertEquals("/inspect/one", get("/sd/inspect/one").inspected("path"));
    }

    @Test
    void pt02StrippingMoreSegmentsThanThePathHasLeavesTheRoot() throws IOException {
        RawHttp.Response response = get("/deep");
        assertEquals("/inspect/", response.inspected("path"), response.toString());
    }

    @Test
    void pt03StripKeepsTheEncodingOfTheRestAndOfTheQuery() throws IOException {
        RawHttp.Response response = get("/api/v1/inspect/b%20c/d%2Bx/%E2%80%93?q=%2B+&r=a%26b");
        assertEquals("/inspect/b%20c/d%2Bx/%E2%80%93", response.inspected("path"));
        assertEquals("q=%2B+&r=a%26b", response.inspected("query"));
    }

    @Test
    void pt04APrefixMatchesOnASegmentBoundaryOnly() throws IOException {
        assertEquals(404, get("/api/v1x/inspect").status());
        assertEquals(404, get("/apix/v1/inspect").status());
    }

    @Test
    void pt05DotsInTheRestAreNeverCollapsedIntoAnotherPath() throws IOException {
        // a dot segment is not relayed: the upstream could resolve it out of the prefix
        assertEquals(400, get("/api/v1/inspect/../../admin").status());
        // dots that are not a segment of their own are a name like any other
        assertEquals("/inspect/a..b/..../c.", get("/api/v1/inspect/a..b/..../c.").inspected("path"));
    }

    @Test
    void pt06StripPrefixSendsTheRemovedPrefixAsXForwardedPrefix() throws IOException {
        RawHttp.Response response = get("/api/v1/inspect/x");
        assertEquals("/api/v1", response.inspected("h:x-forwarded-prefix"));
    }

    @Test
    void pt07PrefixPathWithAVariableKeepsTheEncodingAndAnEmptyPath() throws IOException {
        assertEquals("/inspect/tenant-a/items%20x", get("/pp/tenant-a/items%20x").inspected("path"));
        RawHttp.Response empty = get("/prefix-only");
        assertEquals(200, empty.status(), empty.toString());
        assertEquals("/inspect/", empty.inspected("path"));
    }

    @Test
    void pt08RewritePathWithNamedGroupsKeepsTheEncoding() throws IOException {
        assertEquals("/inspect/rewritten/a%20b/%E2%80%93", get("/rw/a%20b/%E2%80%93").inspected("path"));
        assertEquals("/inspect/rewritten/x", get("/rw/x?y=1").inspected("path"));
        assertEquals("y=1", get("/rw/x?y=1").inspected("query"));
    }

    @Test
    void pt09SetPathEncodesAVariableOnce() throws IOException {
        assertEquals("/inspect/set/a%20b", get("/sp/a%20b").inspected("path"));
        RawHttp.Response plus = get("/sp/a+b");
        assertEquals("/inspect/set/a+b", plus.inspected("path"), plus.toString());
    }

    @Test
    void pt11AndPt12TheQueryIsRelayedByteForByteIncludingAnEmptyOne() throws IOException {
        assertEquals("a=%2B&b=+&c=%20&d=%26&&e", get("/api/v1/inspect/q?a=%2B&b=+&c=%20&d=%26&&e").inspected("query"));
        RawHttp.Response empty = get("/api/v1/inspect/q?");
        assertEquals("/inspect/q?", empty.inspected("uri"));
        RawHttp.Response none = get("/api/v1/inspect/q");
        assertEquals("/inspect/q", none.inspected("uri"));
    }

    @Test
    void pt13AddingAndRemovingAParameterLeavesTheOthersAsTheyWere() throws IOException {
        RawHttp.Response response = get("/params/inspect?a=1+2&drop=x&b=%2B&drop=y&c");
        assertEquals("a=1+2&b=%2B&c&added=x%20y", response.inspected("query"));
        RawHttp.Response onlyDropped = get("/params/inspect?drop=1");
        assertEquals("added=x%20y", onlyDropped.inspected("query"));
    }

    @Test
    void pt14AVeryLongUriIsRelayedIntact() throws IOException {
        String longSegment = "s".repeat(3000);
        RawHttp.Response response = get("/api/v1/inspect/" + longSegment + "?q=" + "v".repeat(500));
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/" + longSegment, response.inspected("path"));
        assertEquals("q=" + "v".repeat(500), response.inspected("query"));
    }

    @Test
    void pt17TheBasePathOfTheUpstreamIsJoinedWithoutADoubleSlash() throws IOException {
        assertEquals("/inspect/base/x/y", get("/base/x/y").inspected("path"));
        assertEquals("/inspect/base/x", get("/base-slash/x").inspected("path"));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;

        Routes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("strip").path("/api/v1/**").filters(GatewayFilters.stripPrefix(2)).uri(upstream);
            gateway.route("deep").path("/deep/**").filters(GatewayFilters.stripPrefix(5), GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("prefix").path("/pp/{tenant}/**").filters(GatewayFilters.stripPrefix(2), GatewayFilters.prefixPath("/inspect/{tenant}")).uri(upstream);
            gateway.route("prefix-only").path("/prefix-only").filters(GatewayFilters.stripPrefix(1), GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("set").path("/sp/{segment}").filters(GatewayFilters.setPath("/inspect/set/{segment}")).uri(upstream);
            gateway.route("params").path("/params/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.removeRequestParameter("drop"), GatewayFilters.addRequestParameter("added", "x y"))
                .uri(upstream);
            gateway.route("base").path("/base/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream + "/inspect/base");
            gateway.route("base-slash").path("/base-slash/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream + "/inspect/base/");
        }
    }
}
