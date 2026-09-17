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
import org.eclipse.serializer.reflect.ClassLoaderProvider
import org.eclipse.store.storage.embedded.types.EmbeddedStorage
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager
import org.graalvm.polyglot.Value
import spock.lang.TempDir

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Path

/**
 * The Java class generated for an introspected Python class can be stored by a Java persistence
 * library that walks the object graph reflectively (EclipseStore here): only the property fields are
 * persistent, and an object Java created or loaded from storage keeps its state in those fields, so
 * what Python changes through it is what gets stored.
 */
class PersistableStubSpec extends AbstractPythonTypeElementSpec {

    private static final String PYTHON = '''
from dataclasses import dataclass, field

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Customer:
    id: str
    firstName: str
    lastName: str | None = None


@Introspected
@dataclass
class Data:
    customers: dict[str, Customer] = field(default_factory=dict)
    names: list[str] = field(default_factory=list)


@Singleton
class CustomerRepository:

    @Executable
    def add(self, data: Data, id: str, first_name: str) -> Customer:
        customer = Customer(id, first_name)
        data.customers[customer.id] = customer
        data.names.append(first_name)
        return customer

    @Executable
    def rename(self, data: Data, id: str, last_name: str) -> Customer | None:
        c = data.customers.get(id)
        if c is not None:
            c.lastName = last_name
        return c

    @Executable
    def remove(self, data: Data, id: str) -> None:
        data.customers.pop(id, None)

    @Executable
    def find(self, data: Data, id: str) -> Customer | None:
        return data.customers.get(id)

    @Executable
    def describe(self, data: Data) -> str:
        return ",".join(sorted(c.firstName + ":" + str(c.lastName) for c in data.customers.values())) + "|" + ",".join(data.names)
'''

    @TempDir
    Path storageDirectory

    void "only the property fields of an introspected Python class are persistent"() {
        given:
        ApplicationContext ctx = buildContext(PYTHON, true)
        Class<?> customerClass = ctx.classLoader.loadClass('python.Customer')
        Class<?> dataClass = ctx.classLoader.loadClass('python.Data')

        expect:
        persistentFieldNames(customerClass) == ['id', 'firstName', 'lastName'] as Set
        persistentFieldNames(dataClass) == ['customers', 'names'] as Set

        cleanup:
        ctx?.close()
    }

    void "a Python object graph changed from Python is stored and loaded by EclipseStore across restarts"() {
        given:
        ApplicationContext ctx = buildContext(PYTHON, true)
        ClassLoader classLoader = ctx.classLoader
        Class<?> dataClass = classLoader.loadClass('python.Data')
        Class<?> customerClass = classLoader.loadClass('python.Customer')
        def repository = getBean(ctx, 'python.CustomerRepository')

        when: "the root is created from Java, filled from Python and stored"
        def root = dataClass.getDeclaredConstructor(Map, List).newInstance(new HashMap(), new ArrayList())
        EmbeddedStorageManager storage = start(classLoader, root)
        def sergio = repository.add(root, 'c1', 'Sergio')
        repository.add(root, 'c2', 'Tim')
        storage.store(root.customers)
        storage.store(root.names)
        storage.shutdown()

        then: "the Java fields hold what Python added"
        customerClass.isInstance(sergio)
        sergio.firstName == 'Sergio'
        root.customers.keySet() == ['c1', 'c2'] as Set
        customerClass.isInstance(root.customers['c1'])
        root.customers['c1'].firstName == 'Sergio'
        root.names == ['Sergio', 'Tim']

        when: "the storage is restarted"
        storage = start(classLoader, null)
        def loaded = storage.root()

        then: "the graph is back, as Java objects"
        dataClass.isInstance(loaded)
        loaded.customers.keySet() == ['c1', 'c2'] as Set
        loaded.customers['c1'].firstName == 'Sergio'
        loaded.customers['c1'].lastName == null
        loaded.names == ['Sergio', 'Tim']

        and: "Python reads it through the loaded objects"
        repository.find(loaded, 'c1').firstName == 'Sergio'
        repository.describe(loaded) == 'Sergio:None,Tim:None|Sergio,Tim'

        when: "Python updates a loaded object, which is stored"
        def renamed = repository.rename(loaded, 'c1', 'del Amo')
        storage.store(renamed)
        storage.shutdown()
        storage = start(classLoader, null)
        loaded = storage.root()

        then:
        renamed.lastName == 'del Amo'
        loaded.customers['c1'].lastName == 'del Amo'
        repository.describe(loaded) == 'Sergio:del Amo,Tim:None|Sergio,Tim'

        when: "Python removes an entry of a loaded collection, which is stored"
        repository.remove(loaded, 'c1')
        storage.store(loaded.customers)
        storage.shutdown()
        storage = start(classLoader, null)
        loaded = storage.root()

        then:
        loaded.customers.keySet() == ['c2'] as Set
        repository.find(loaded, 'c1') == null
        repository.find(loaded, 'c2').firstName == 'Tim'
        repository.describe(loaded) == 'Tim:None|Sergio,Tim'

        cleanup:
        storage?.shutdown()
        ctx?.close()
    }

    void "an object created from Java hands its collections and nested objects to Python by reference"() {
        given:
        ApplicationContext ctx = buildContext(PYTHON, true)
        Class<?> dataClass = ctx.classLoader.loadClass('python.Data')
        Class<?> customerClass = ctx.classLoader.loadClass('python.Customer')
        def repository = getBean(ctx, 'python.CustomerRepository')
        def root = dataClass.getDeclaredConstructor(Map, List).newInstance(new HashMap(), new ArrayList())
        Map customers = root.customers

        when:
        repository.add(root, 'c1', 'Sergio')
        Value python = ((ValueCoercible) root).asPolyglotValue()

        then: "the Python attribute is the Java map, and Python's entry is the Java wrapper"
        root.customers.is(customers)
        customers.size() == 1
        python.getMember('customers').isHostObject()
        python.getMember('customers').asHostObject().is(customers)
        customerClass.isInstance(customers['c1'])

        when: "a nested object is changed from Python through the wrapper"
        repository.rename(root, 'c1', 'del Amo')

        then: "the Java field and the Python object of the nested wrapper agree"
        customers['c1'].lastName == 'del Amo'
        ((ValueCoercible) customers['c1']).asPolyglotValue().getMember('lastName').asString() == 'del Amo'

        when: "Java changes a field, and the object crosses again"
        customers['c1'].firstName = 'Sergio B.'

        then:
        repository.find(root, 'c1').firstName == 'Sergio B.'
        repository.describe(root) == 'Sergio B.:del Amo|Sergio'

        cleanup:
        ctx?.close()
    }

    void "an object created in Python keeps its Python objects"() {
        given:
        ApplicationContext ctx = buildContext(PYTHON, true)
        Class<?> dataClass = ctx.classLoader.loadClass('python.Data')
        Value python = ctx.getBean(org.graalvm.polyglot.Context).eval('python', '''
d = Data()
d.customers["c1"] = Customer("c1", "Sergio")
d
''')
        def wrapper = dataClass.fromPolyglotValue(python)

        when:
        def customers = wrapper.customers

        then: "the Java side is a converted view, the Python side keeps its own objects"
        customers.keySet() == ['c1'] as Set
        customers['c1'].firstName == 'Sergio'
        wrapper.asPolyglotValue().is(python)
        !python.getMember('customers').getHashValue('c1').isHostObject()
        python.getMember('customers').getHashValue('c1').getMetaObject().getMetaSimpleName() == 'Customer'
        python.getMember('customers').getHashValue('c1') == ((ValueCoercible) customers['c1']).asPolyglotValue()

        cleanup:
        ctx?.close()
    }

    private EmbeddedStorageManager start(ClassLoader classLoader, Object root) {
        def foundation = EmbeddedStorage.Foundation(storageDirectory)
            .onConnectionFoundation { it.setClassLoaderProvider(ClassLoaderProvider.New(classLoader)) }
        root == null ? foundation.start() : foundation.start(root)
    }

    /**
     * The fields a reflective persistence library stores: instance fields that are not transient.
     */
    private static Set<String> persistentFieldNames(Class<?> type) {
        Set<String> names = []
        for (Class<?> current = type; current != null && current != Object; current = current.superclass) {
            for (Field field : current.declaredFields) {
                if (!Modifier.isStatic(field.modifiers) && !Modifier.isTransient(field.modifiers)) {
                    names << field.name
                }
            }
        }
        names
    }
}
