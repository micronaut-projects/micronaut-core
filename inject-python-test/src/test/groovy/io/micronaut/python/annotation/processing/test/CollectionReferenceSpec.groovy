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
 * A Java collection class of its own (a cache, a registry) crosses into Python by reference and keeps its API,
 * a plain JDK collection is copied, and the list and dict attributes of a Python object stay Python collections
 * that the generated Java class views.
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

    void "a plain Java collection is copied for Python, so Python neither mutates it nor fails on an unmodifiable one"() {
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

        then: "the Python method worked on copies"
        size == 2
        values == ['first']
        counts.isEmpty()

        and: "an unmodifiable collection can be appended to in Python"
        service.extend(List.of('a'), Map.of()) == 2

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

        when: "Java adds to the list"
        cart.items.add('plum')

        then: "Python sees it, and the attribute is still a Python list"
        service.add_item('fig') == 4
        ((ValueCoercible) cart).asPolyglotValue().getMember('items').getMetaObject().getMetaSimpleName() == 'list'

        cleanup:
        ctx?.close()
    }

    void "the attributes of a wrapped Python object keep their Python types"() {
        given:
        String py = '''
import copy
import dataclasses
import json
from dataclasses import dataclass, field

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Cart:
    items: list[str] = field(default_factory=list)
    counts: dict[str, int] = field(default_factory=dict)
    matrix: list[list[int]] = field(default_factory=list)

    def describe(self) -> str:
        clone = copy.deepcopy(self)
        clone.items.append("cloned")
        return type(self.items).__name__ + ":" + type(self.counts).__name__ + ":" + json.dumps(dataclasses.asdict(self)) + ":" + str(len(clone.items))


@Introspected
@dataclass(frozen=True)
class FrozenCart:
    items: list[str] = field(default_factory=list)

    def describe(self) -> str:
        return type(self.items).__name__ + ":" + json.dumps(dataclasses.asdict(self))


'''
        ApplicationContext ctx = buildContext(py, true)
        Context polyglot = ctx.getBean(Context)
        Class<?> cartClass = ctx.classLoader.loadClass('python.Cart')
        Class<?> frozenCartClass = ctx.classLoader.loadClass('python.FrozenCart')
        def pythonCart = polyglot.eval('python', "Cart(['a'], {'a': 1}, [[1, 2], [3]])")

        when: "the Python object is wrapped, used from Java and crosses back into Python"
        def cart = pythonCart.as(cartClass)
        cart.items.add('b')
        cart.counts.put('b', 2)
        cart.matrix.get(1).add(4)
        cart.matrix.add([5])
        String description = cart.describe()

        then: "the attributes are still Python lists and dicts holding the Java-side changes"
        description == 'list:dict:{"items": ["a", "b"], "counts": {"a": 1, "b": 2}, "matrix": [[1, 2], [3, 4], [5]]}:3'
        pythonCart.getMember('items').getArraySize() == 2
        cart.items == ['a', 'b']
        cart.counts == [a: 1, b: 2]
        cart.matrix == [[1, 2], [3, 4], [5]]

        when: "Java assigns a collection"
        cart.items = new ArrayList<>(['x', 'y'])
        cart.counts = Map.of('x', 1)

        then: "the next crossing copies it into a Python collection, which the Java property views from then on"
        cart.describe() == 'list:dict:{"items": ["x", "y"], "counts": {"x": 1}, "matrix": [[1, 2], [3, 4], [5]]}:3'
        pythonCart.getMember('items').getMetaObject().getMetaSimpleName() == 'list'
        cart.items.add('z')
        pythonCart.getMember('items').getArraySize() == 3

        when: "a frozen dataclass is wrapped"
        def pythonFrozen = polyglot.eval('python', "FrozenCart(['a'])")
        def frozen = pythonFrozen.as(frozenCartClass)
        frozen.items.add('b')

        then: "its list stays the Python list of the instance"
        frozen.describe() == 'list:{"items": ["a", "b"]}'
        pythonFrozen.getMember('items').getArraySize() == 2
        ((ValueCoercible) frozen).asPolyglotValue().getMember('items').getArraySize() == 2

        cleanup:
        ctx?.close()
    }
}
