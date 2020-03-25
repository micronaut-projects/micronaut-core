/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.util

/**
 * Uses cosine similarity to find matches from a candidate set for a specified input.
 * Based on code from http://www.nearinfinity.com/blogs/seth_schroeder/groovy_cosine_similarity_in_grails.html
 *
 * @author Burt Beckwith
 */
class CosineSimilarity {

    /**
     * Sort the candidates by their similarity to the specified input.
     * @param pattern the input string
     * @param candidates the possible matches
     * @return the ordered candidates
     */
    static List<String> mostSimilar(String pattern, candidates, double threshold = 0) {
        SortedMap<Double, String> sorted = new TreeMap<Double, String>()
        for (candidate in candidates) {
            double score = stringSimilarity(pattern, candidate)
            if (score > threshold) {
                sorted[score] = candidate
            }
        }

        (sorted.values() as List).reverse()
    }

    private static double stringSimilarity(String s1, String s2, int degree = 2) {
        similarity s1.toLowerCase().toCharArray(), s2.toLowerCase().toCharArray(), degree
    }

    private static double similarity(sequence1, sequence2, int degree = 2) {
        Map<List, Integer> m1 = countNgramFrequency(sequence1, degree)
        Map<List, Integer> m2 = countNgramFrequency(sequence2, degree)

        dotProduct(m1, m2) / Math.sqrt(dotProduct(m1, m1) * dotProduct(m2, m2))
    }

    private static Map<List, Integer> countNgramFrequency(sequence, int degree) {
        Map<List, Integer> m = [:]
        int count = sequence.size()

        for (int i = 0; i + degree <= count; i++) {
            List gram = sequence[i..<(i + degree)]
            m[gram] = 1 + m.get(gram, 0)
        }

        m
    }

    private static double dotProduct(Map<List, Integer> m1, Map<List, Integer> m2) {
        m1.keySet().collect { key -> m1[key] * m2.get(key, 0) }.sum()
    }
}
