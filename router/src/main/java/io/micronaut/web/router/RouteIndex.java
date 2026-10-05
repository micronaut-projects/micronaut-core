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

    private final int size;
    private final Node root;
    private final int[] unprefixed;

    private RouteIndex(int size, Node root, int[] unprefixed) {
        this.size = size;
        this.root = root;
        this.unprefixed = unprefixed;
    }

    /**
     * Build an index.
     *
     * @param prefixes The required path prefix of each route, by route position; empty for none
     * @return The index
     */
    static RouteIndex build(String[] prefixes) {
        MutableNode root = new MutableNode();
        List<Integer> unprefixed = new ArrayList<>();
        for (int i = 0; i < prefixes.length; i++) {
            String prefix = prefixes[i];
            if (prefix.isEmpty()) {
                unprefixed.add(i);
                continue;
            }
            MutableNode node = root;
            for (int c = 0; c < prefix.length(); c++) {
                node = node.children.computeIfAbsent(prefix.charAt(c), k -> new MutableNode());
            }
            node.ranks.add(i);
        }
        return new RouteIndex(prefixes.length, root.freeze(), unprefixed.stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * The positions of the routes that can match the path, in ascending order.
     *
     * @param path The request path
     * @return The route positions
     */
    int[] candidates(String path) {
        if (size == 0) {
            return NONE;
        }
        long[] bits = isSingleWord() ? new long[] {candidateMask(path)} : candidateBits(path);
        int count = 0;
        for (long word : bits) {
            count += Long.bitCount(word);
        }
        int[] result = new int[count];
        int n = 0;
        for (int w = 0; w < bits.length; w++) {
            long word = bits[w];
            while (word != 0) {
                result[n++] = (w << 6) + Long.numberOfTrailingZeros(word);
                word &= word - 1;
            }
        }
        return result;
    }

    /**
     * @return Whether the index has at most 64 routes, so that {@link #candidateMask(String)}
     * gives the candidates without an allocation
     */
    boolean isSingleWord() {
        return size <= 64;
    }

    /**
     * The routes that can match the path, as a mask of their positions: bit {@code i} is set for
     * the route at position {@code i}. Only for an index with at most 64 routes, see
     * {@link #isSingleWord()}.
     *
     * @param path The request path
     * @return The mask of the route positions
     */
    long candidateMask(String path) {
        long mask = 0;
        for (int rank : unprefixed) {
            mask |= 1L << rank;
        }
        @Nullable Node node = root;
        int end = matchedLength(path);
        for (int i = 0; i < end && node != null; i++) {
            node = node.child(path.charAt(i));
            if (node != null) {
                for (int rank : node.ranks) {
                    mask |= 1L << rank;
                }
            }
        }
        return mask;
    }

    /**
     * The routes that can match the path, as the bits of their positions in words of 64: bit
     * {@code i % 64} of word {@code i / 64} is set for the route at position {@code i}.
     *
     * @param path The request path
     * @return The bits of the route positions
     */
    long[] candidateBits(String path) {
        long[] bits = new long[(size + 63) >>> 6];
        mark(bits, unprefixed);
        @Nullable Node node = root;
        int end = matchedLength(path);
        for (int i = 0; i < end && node != null; i++) {
            node = node.child(path.charAt(i));
            if (node != null) {
                mark(bits, node.ranks);
            }
        }
        return bits;
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

    private static void mark(long[] bits, int[] ranks) {
        for (int rank : ranks) {
            bits[rank >>> 6] |= 1L << rank;
        }
    }

    /**
     * A node of the frozen trie.
     *
     * @param keys     The characters of the children, sorted
     * @param children The children, by key position
     * @param ranks    The positions of the routes whose prefix ends here
     */
    private record Node(char[] keys, Node[] children, int[] ranks) {
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

        Node freeze() {
            char[] keys = new char[children.size()];
            Node[] nodes = new Node[children.size()];
            int i = 0;
            for (Map.Entry<Character, MutableNode> entry : children.entrySet()) {
                keys[i] = entry.getKey();
                nodes[i] = entry.getValue().freeze();
                i++;
            }
            return new Node(keys, nodes, ranks.stream().mapToInt(Integer::intValue).toArray());
        }
    }
}
