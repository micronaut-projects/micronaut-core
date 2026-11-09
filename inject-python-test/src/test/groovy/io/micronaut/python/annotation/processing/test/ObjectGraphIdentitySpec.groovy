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
package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.python.annotation.processing.test.collections.GraphStore

/**
 * A Python object reached through two paths of a graph passed to Java is one Java object, so a Java runtime
 * that tells objects apart by identity (a graph database session) sees one node where Python has one. What
 * Java writes to the graph reaches the Python objects the caller holds once the saved graph is back in
 * Python, through the sync of the returned wrapper.
 */
class ObjectGraphIdentitySpec extends AbstractPythonTypeElementSpec {

    private static final String GRAPH = '''
from dataclasses import dataclass, field

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from micronaut.python.annotation.processing.test.collections import GraphStore


@Introspected
@dataclass
class Node:
    name: str
    links: list["Node"] = field(default_factory=list)
    id: str | None = None


@Singleton
class GraphService:
    def __init__(self, store: GraphStore):
        self.store = store

    @Executable
    def save_shared(self) -> str:
        joe = Node("Joe")
        fred = Node("Fred", [Node("Barney", [joe]), Node("Wilma", [joe])])
        saved = self.store.save(fred)
        return f"{len(self.store.saved())}|{saved.id}|{fred.id}|{fred.links[0].id}|{fred.links[1].id}|{joe.id}"

    @Executable
    def links_one_object_twice(self) -> bool:
        joe = Node("Joe")
        return self.store.linksOneInstanceTwice(Node("Fred", [joe, joe]))

    @Executable
    def save_cycle(self) -> str:
        fred = Node("Fred")
        barney = Node("Barney", [fred])
        fred.links.append(barney)
        saved = self.store.save(fred)
        return f"{len(self.store.saved())}|{saved.id}|{fred.id}|{barney.id}|{fred.links[0].links[0] is fred}"


'''

    void "a Python object linked from two nodes is one Java object, and the ids Java assigns reach Python"() {
        given:
        ApplicationContext ctx = buildContext(GRAPH, true)
        GraphStore store = new GraphStore(getBeanIntrospection(ctx, 'python.Node'))
        ctx.registerSingleton(GraphStore, store)
        def service = getBean(ctx, 'python.GraphService')

        expect:
        service.save_shared() == "4|node-1|node-1|node-2|node-4|node-3"

        cleanup:
        ctx?.close()
    }

    void "one Python object in a list attribute twice is one Java object"() {
        given:
        ApplicationContext ctx = buildContext(GRAPH, true)
        ctx.registerSingleton(GraphStore, new GraphStore(getBeanIntrospection(ctx, 'python.Node')))
        def service = getBean(ctx, 'python.GraphService')

        expect:
        service.links_one_object_twice()

        cleanup:
        ctx?.close()
    }

    void "a cycle of Python objects is converted once per object"() {
        given:
        ApplicationContext ctx = buildContext(GRAPH, true)
        GraphStore store = new GraphStore(getBeanIntrospection(ctx, 'python.Node'))
        ctx.registerSingleton(GraphStore, store)
        def service = getBean(ctx, 'python.GraphService')

        expect:
        service.save_cycle() == "2|node-1|node-1|node-2|True"

        cleanup:
        ctx?.close()
    }
}
