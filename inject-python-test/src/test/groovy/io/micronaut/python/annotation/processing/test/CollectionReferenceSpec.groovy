/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.context.python.ValueCoercible
import io.micronaut.python.annotation.processing.test.collections.NamedRegistry
import io.micronaut.python.annotation.processing.test.collections.ObjectStore
import org.graalvm.polyglot.Context

/**
 * Java collections cross into Python by reference: a {@code Map} with an API of its own keeps it,
 * and in-place mutations made in Python reach the Java object.
 */
class CollectionReferenceSpec extends AbstractPythonTypeElementSpec {

    void "an injected Map subclass keeps its identity and API in Python"() {
        given:
        String py = '''
from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.collections import NamedRegistry


@Singleton
class RegistryService:
    registry: Annotated[NamedRegistry, Inject]

    def __init__(self, constructor_registry: NamedRegistry):
        self.constructor_registry = constructor_registry

    @Executable
    def register(self, key: str, value: str) -> str:
        self.registry.put(key, value)
        self.constructor_registry[key + "-2"] = value
        return self.registry.getName() + ":" + self.constructor_registry.getName() + ":" + str(len(self.registry))


'''
        ApplicationContext ctx = buildContext(py, true)
        NamedRegistry registry = new NamedRegistry('registry')
        ctx.registerSingleton(NamedRegistry, registry)
        def service = getBean(ctx, 'python.RegistryService')

        when:
        String description = service.register('k', 'v')

        then:
        description == 'registry:registry:2'
        registry.get('k') == 'v'
        registry.get('k-2') == 'v'
        registry.size() == 2

        cleanup:
        ctx?.close()
    }

    void "a plain Java collection is passed to Python by reference"() {
        given:
        String py = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class ListService:

    @Executable
    def extend(self, values: list[str], counts: dict[str, int]) -> int:
        values.append("added")
        counts["added"] = len(values)
        return len(values)


'''
        ApplicationContext ctx = buildContext(py, true)
        def service = getBean(ctx, 'python.ListService')
        List<String> values = new ArrayList<>(['first'])
        Map<String, Integer> counts = new LinkedHashMap<>()

        when:
        int size = service.extend(values, counts)

        then:
        size == 2
        values == ['first', 'added']
        counts == [added: 2]

        cleanup:
        ctx?.close()
    }

    void "a Java collection of Python objects still arrives as Python objects"() {
        given:
        String py = '''
from dataclasses import dataclass

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Item:
    name: str


@Singleton
class ItemService:

    @Executable
    def names(self, items: list[Item], by_name: dict[str, Item]) -> str:
        return ",".join(item.name for item in items) + ":" + ",".join(sorted(by_name.keys()))


'''
        ApplicationContext ctx = buildContext(py, true)
        Class<?> itemClass = ctx.classLoader.loadClass('python.Item')
        def service = getBean(ctx, 'python.ItemService')
        def one = itemClass.getDeclaredConstructor(String).newInstance('one')
        def two = itemClass.getDeclaredConstructor(String).newInstance('two')

        expect:
        service.names([one, two], [a: one, b: two]) == 'one,two:a,b'

        cleanup:
        ctx?.close()
    }

    void "a Python object stored in Java keeps a list attribute that can be mutated in place"() {
        given:
        String py = '''
from dataclasses import dataclass, field

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from micronaut.python.annotation.processing.test.collections import ObjectStore


@Introspected
@dataclass
class Cart:
    items: list[str] = field(default_factory=list)


@Singleton
class CartService:
    def __init__(self, store: ObjectStore):
        self.store = store

    @Executable
    def add_item(self, name: str) -> int:
        cart = self.store.get("cart")
        if cart is None:
            cart = Cart()
            self.store.put("cart", cart)
        cart.items.append(name)
        return len(cart.items)


'''
        ApplicationContext ctx = buildContext(py, true)
        ObjectStore store = new ObjectStore()
        ctx.registerSingleton(ObjectStore, store)
        Class<?> cartClass = ctx.classLoader.loadClass('python.Cart')
        def service = getBean(ctx, 'python.CartService')

        when:
        int first = service.add_item('apple')
        int second = service.add_item('pear')
        def cart = store.get('cart')

        then:
        first == 1
        second == 2
        cartClass.isInstance(cart)
        cart.items == ['apple', 'pear']
        ((ValueCoercible) cart).asPolyglotValue().getMember('items').getArraySize() == 2

        cleanup:
        ctx?.close()
    }
}
