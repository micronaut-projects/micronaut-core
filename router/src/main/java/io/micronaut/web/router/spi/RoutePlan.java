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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A routing plan generated at compile time by the route compiler ({@code micronaut-router-processor}):
 * the descriptors of its {@link RouteSlot slots}, each the potential route of one declaration,
 * and a parser that enumerates the slots whose templates match a request path, with the spans
 * of their captured path variables.
 *
 * <p>A plan records potential routes; it routes nothing by itself. The router binds the routes
 * that the application actually registers to the slots of the plan, by the logical
 * {@link RouteSlot#key() key} of their declaration, once for each router, and asks the parser of
 * the plan for the candidates of a request instead of trying the templates of those routes one by
 * one. The candidates then compete with the other routes of the router under the same selection
 * rules. A slot the parser does not match exactly, see {@link RouteSlot#compiled()}, is matched
 * by the engine of its template like any route.</p>
 *
 * <p>The router checks the {@link #abiVersion() ABI version}, the {@link #inputProfile() input
 * profile}, the {@link #selectionPolicy() selection policy}, the {@link #fingerprint() fingerprint}
 * and the engine versions of the slots before it uses a plan; a plan that does not agree with the
 * runtime is not used, and its routes are matched as ordinary routes.</p>
 *
 * <p>Plans of controllers are registered as services of this interface; other plans are
 * referenced by the {@link PlannedRouteDeclaration declarations} generated with them.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RoutePlan {

    /**
     * The version of the contract between generated plans and the router: the methods of this
     * interface, of {@link RouteCandidateSink} and of {@link RoutePlanSupport} that generated code
     * calls, and the meaning of the slot descriptors.
     */
    int ABI_VERSION = 1;

    /**
     * The matching input of this version: the raw, not decoded, request path, without the query
     * and without one trailing slash, see
     * {@link io.micronaut.http.uri.UriTemplateMatcher#normalizeForMatching(String)}.
     */
    String INPUT_PROFILE = "micronaut-raw-path/1";

    /**
     * The route selection policy the generated facts are computed for: more literal text first,
     * then fewer path variables, then media types, and the other rules of the router.
     */
    String SELECTION_POLICY = "micronaut/1";

    /**
     * @return The identity of the plan, unique among the plans of an application, e.g.
     * {@code controller:example.PetController}
     */
    String id();

    /**
     * @return The {@link #ABI_VERSION} the plan was generated for
     */
    int abiVersion();

    /**
     * @return The {@link #INPUT_PROFILE} the parser of the plan was generated for
     */
    String inputProfile();

    /**
     * @return The {@link #SELECTION_POLICY} the facts of the slots were computed for
     */
    String selectionPolicy();

    /**
     * @return The {@link #fingerprint(RouteSlot[]) fingerprint} of the slots the parser was generated for
     */
    String fingerprint();

    /**
     * @return The names of the controller types whose routes are the slots of this plan, empty for a plan of declarations
     */
    String[] owners();

    /**
     * @return The slots, by slot number; a new array on each call
     */
    RouteSlot[] slots();

    /**
     * @return A literal that every path the parser matches starts with, or an empty string
     */
    String commonPrefix();

    /**
     * @return The largest number of path variables the parser captures for a slot
     */
    int maxCaptures();

    /**
     * Enumerate the {@link RouteSlot#compiled() compiled} slots whose template matches a path. The
     * sink receives each of them once, in no particular order, with the spans of the captured
     * path variables, and must copy what it keeps: the array of spans is reused.
     *
     * @param path The path, normalised with {@link io.micronaut.http.uri.UriTemplateMatcher#normalizeForMatching(String)}
     * @param sink Receives the matched slots
     */
    void match(String path, RouteCandidateSink sink);

    /**
     * The fingerprint of slot descriptors: a digest of their canonical form, which generated
     * plans record and the router recomputes, so that a parser is never used with slots it was
     * not generated for.
     *
     * @param slots The slots
     * @return The fingerprint
     */
    static String fingerprint(RouteSlot[] slots) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(ABI_VERSION).append('\u001e').append(INPUT_PROFILE).append('\u001e').append(SELECTION_POLICY);
        for (RouteSlot slot : slots) {
            canonical.append('\u001e');
            slot.appendCanonical(canonical);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
