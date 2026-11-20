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
package io.micronaut.python.annotation.processing.test.collections;

import io.micronaut.core.beans.BeanIntrospection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * A Java store of object graphs that, like a graph database runtime, tells nodes apart by Java object
 * identity: a node reached through two relationships is one node only when both lead to the same Java
 * instance. Saving assigns every node an identifier, as a generated id is written back after a save.
 */
public class GraphStore {

    private final List<Object> saved = new ArrayList<>();
    private final BeanIntrospection<Object> introspection;

    /**
     * @param introspection The introspection of the node class
     */
    @SuppressWarnings("unchecked")
    public GraphStore(BeanIntrospection<?> introspection) {
        this.introspection = (BeanIntrospection<Object>) introspection;
    }

    /**
     * @param node A node
     * @return Whether the node links to one Java instance twice
     */
    public boolean linksOneInstanceTwice(Object node) {
        List<?> links = (List<?>) introspection.getRequiredProperty("links", Iterable.class).get(node);
        return links.size() == 2 && links.get(0) == links.get(1);
    }

    /**
     * Saves the graph of a root node through its {@code links} property.
     *
     * @param root The root node
     * @return The root node
     */
    public Object save(Object root) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        visit(root, seen);
        return root;
    }

    /**
     * @return The nodes saved so far, one per distinct Java instance
     */
    public List<Object> saved() {
        return saved;
    }

    private void visit(Object node, Set<Object> seen) {
        if (!seen.add(node)) {
            return;
        }
        saved.add(node);
        introspection.getRequiredProperty("id", String.class).set(node, "node-" + saved.size());
        for (Object linked : introspection.getRequiredProperty("links", Iterable.class).get(node)) {
            visit(linked, seen);
        }
    }
}
