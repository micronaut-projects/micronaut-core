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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.UriTemplateMatcher;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Index of the routes of one HTTP method by the literal that the paths they match start with
 * ({@link UriTemplateMatcher#getRequiredPrefix()}). A lookup walks the normalised request path
 * through a character trie of those prefixes and returns the routes whose prefix the path starts
 * with, plus the routes without a prefix, in their original order.
 * <p>The index only skips routes that cannot match: a route's own matching still decides, so a
 * router gets the same result as when it tries every route.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteIndex {
    private static final int[] NONE = new int[0];

    private final Node root;

    private RouteIndex(Node root) {
        this.root = root;
    }

    /**
     * Build an index.
     *
     * @param prefixes The required path prefix of each route, by route position; empty for none
     * @return The index
     */
    static RouteIndex build(String[] prefixes) {
        MutableNode root = new MutableNode();
        for (int i = 0; i < prefixes.length; i++) {
            String prefix = prefixes[i];
            MutableNode node = root;
            for (int c = 0; c < prefix.length(); c++) {
                node = node.children.computeIfAbsent(prefix.charAt(c), k -> new MutableNode());
            }
            node.ranks.add(i);
        }
        return new RouteIndex(root.freeze(NONE));
    }

    /**
     * The positions of the routes that can match the path, in ascending order. The array is
     * precomputed when the index is built and shared by every lookup that ends on the same node of
     * the trie, so a lookup allocates nothing: the caller must not modify it.
     *
     * @param path The request path
     * @return The route positions
     */
    int[] candidates(String path) {
        Node node = root;
        int end = matchedLength(path);
        for (int i = 0; i < end; i++) {
            Node child = node.child(path.charAt(i));
            if (child == null) {
                break;
            }
            node = child;
        }
        return node.candidates;
    }

    /**
     * The length of the path that is matched, like {@link UriTemplateMatcher#normalizeForMatching(String)}
     * without a copy: up to the query, without a trailing slash.
     *
     * @param path The request path
     * @return The length
     */
    private static int matchedLength(String path) {
        int end = path.indexOf('?');
        if (end < 0) {
            end = path.length();
        }
        if (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return end;
    }

    /**
     * A node of the frozen trie.
     *
     * @param keys       The characters of the children, sorted
     * @param children   The children, by key position
     * @param candidates The positions of the routes whose prefix ends at this node or at one of
     *                   its ancestors, the root and its routes without a prefix included, in
     *                   ascending order: the candidates of a path whose walk ends here. The array
     *                   of the parent when no prefix ends here
     */
    private record Node(char[] keys, Node[] children, int[] candidates) {
        @Nullable Node child(char c) {
            int i = Arrays.binarySearch(keys, c);
            return i < 0 ? null : children[i];
        }
    }

    /**
     * A node of the trie while it is built.
     */
    private static final class MutableNode {
        final Map<Character, MutableNode> children = new TreeMap<>();
        final List<Integer> ranks = new ArrayList<>(1);

        Node freeze(int[] inherited) {
            int[] candidates = ranks.isEmpty() ? inherited : merge(inherited, ranks);
            char[] keys = new char[children.size()];
            Node[] nodes = new Node[children.size()];
            int i = 0;
            for (Map.Entry<Character, MutableNode> entry : children.entrySet()) {
                keys[i] = entry.getKey();
                nodes[i] = entry.getValue().freeze(candidates);
                i++;
            }
            return new Node(keys, nodes, candidates);
        }

        private static int[] merge(int[] inherited, List<Integer> ranks) {
            int[] result = Arrays.copyOf(inherited, inherited.length + ranks.size());
            for (int i = 0; i < ranks.size(); i++) {
                result[inherited.length + i] = ranks.get(i);
            }
            Arrays.sort(result);
            return result;
        }
    }
}
