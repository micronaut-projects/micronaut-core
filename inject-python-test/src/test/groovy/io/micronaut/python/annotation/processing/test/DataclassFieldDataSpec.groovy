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

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.validation.validator.Validator
import org.h2.jdbcx.JdbcDataSource
import spock.lang.TempDir
import javax.sql.DataSource
import java.util.function.Supplier

class DataclassFieldDataSpec extends AbstractPythonTypeElementSpec {

    @TempDir
    File classesDirectory

    void "Annotated identity infers existing Micronaut Data entity mapping"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, GeneratedValue

@dataclass
class Fruit:
    name: str
    id: Annotated[int | None, Id, GeneratedValue] = None
''')
        def introspection = getBeanIntrospection(context, "python.Fruit")
        def id = introspection.getRequiredProperty("id", Integer)

        expect:
        id.hasAnnotation(Id)
        id.hasAnnotation(GeneratedValue)
        introspection.annotationMetadata.hasAnnotation(MappedEntity)
        introspection.instantiate("Apple", null).asPolyglotValue().getMember("id").isNull()

        cleanup:
        context?.close()
    }

    void "inferred entity retains identity indexes and Python property writeback with validation"() {
        given:
        def context = buildContext('''
import dataclasses as dc
from dataclasses import field as f
from typing import Annotated
from micronaut.data.annotation import Id as Identity, GeneratedValue
from jakarta.validation.constraints import NotBlank

@dc.dataclass
class Fruit:
    name: Annotated[str, NotBlank] = f()
    id: Annotated[int | None, Identity(), GeneratedValue()] = dc.field(default=None)
''', true)
        def introspection = getBeanIntrospection(context, "python.Fruit")
        def entity = new RuntimePersistentEntity(introspection)
        def fruit = introspection.instantiate("Apple", null)

        expect:
        entity.hasIdentity()
        entity.identity.name == "id"
        entity.identity.generated
        introspection.getIndexedProperty(Id).get().name == "id"
        context.getBean(Validator).validate(introspection, fruit).isEmpty()

        when:
        entity.identity.property.set(fruit, 42)
        fruit.name = " "

        then:
        fruit.id == 42
        fruit.asPolyglotValue().getMember("id").asInt() == 42
        context.getBean(Validator).validate(introspection, fruit).size() == 1

        cleanup:
        context?.close()
    }

    void "generation strategy and existing Annotated member options survive (#strategy)"() {
        given:
        def context = buildContext("""
from dataclasses import dataclass
from typing import Annotated
import micronaut.data.annotation as data

@dataclass
class Fruit:
    id: Annotated[int | None, data.Id, data.GeneratedValue(
        value=data.GeneratedValue.Type.$strategy, definition="CREATE SEQUENCE fruit_seq", ref="fruit_seq")] = None
""")
        def id = getBeanIntrospection(context, "python.Fruit").getRequiredProperty("id", Integer)

        expect:
        id.annotationMetadata.enumValue(GeneratedValue, GeneratedValue.Type).get().name() == strategy
        id.stringValue(GeneratedValue, "definition").get() == "CREATE SEQUENCE fruit_seq"
        id.stringValue(GeneratedValue, "ref").get() == "fruit_seq"

        cleanup:
        context?.close()

        where:
        strategy << ["AUTO", "SEQUENCE", "IDENTITY", "UUID"]
    }

    void "configured entity introspection and DTO controls remain unchanged"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field
from typing import Annotated
from micronaut.data.annotation import Id, GeneratedValue, MappedEntity
from micronaut.core.annotation import Introspected
from jakarta.validation.constraints import NotBlank

@MappedEntity("fruit_rows")
@Introspected(excludes=["secret"])
@dataclass
class Configured:
    id: Annotated[int | None, Id] = None
    secret: str = "hidden"
    tags: list[str] = field(default_factory=list)

@MappedEntity("composed_rows")
def Entity():
    def decorator(target):
        return target
    return decorator

@Entity
@dataclass
class Composed:
    id: Annotated[int, Id]

@Id
def PrimaryKey():
    def decorator(target):
        return target
    return decorator

@dataclass
class ComposedIdentity:
    id: Annotated[int, PrimaryKey]

@dataclass
class GeneratedOnly:
    value: Annotated[int, GeneratedValue]

@dataclass
class Plain:
    id: int

@dataclass
class Validated:
    name: Annotated[str, NotBlank]

@dataclass
class Metadata:
    id: int = field(metadata={"data": {"id": True, "generated_value": True}, "third_party": object()})

@dataclass
class Unrelated:
    id: int = field(metadata={"data": None})
''')
        def configured = getBeanIntrospection(context, "python.Configured")

        expect:
        configured.stringValue(MappedEntity).get() == "fruit_rows"
        configured.propertyNames.toList() == ["id", "tags"]
        !configured.getRequiredProperty("id", Integer).hasAnnotation(GeneratedValue)
        getBeanIntrospection(context, "python.Composed").annotationMetadata.hasStereotype(MappedEntity)
        !getBeanIntrospection(context, "python.Composed").hasAnnotation(MappedEntity)
        getBeanIntrospection(context, "python.Composed").stringValue(MappedEntity).get() == "composed_rows"
        getBeanIntrospection(context, "python.ComposedIdentity").annotationMetadata.hasAnnotation(MappedEntity)
        getBeanIntrospection(context, "python.ComposedIdentity").getIndexedProperty(Id).get().name == "id"
        getBeanIntrospection(context, "python.GeneratedOnly").annotationMetadata.hasAnnotation(MappedEntity)
        !getBeanIntrospection(context, "python.GeneratedOnly").getRequiredProperty("value", Integer).hasAnnotation(Id)
        ["Plain", "Validated", "Metadata", "Unrelated"].every {
            !getBeanIntrospection(context, "python.$it").hasAnnotation(MappedEntity)
        }

        when:
        def first = configured.instantiate()
        def second = configured.instantiate()
        first.tags.add("fresh")

        then:
        first.id == null
        second.tags.isEmpty()

        cleanup:
        context?.close()
    }

    void "inferred entity generates JDBC queries and saves and finds through H2"() {
        given:
        // Compile to disk so normal service discovery sees the real introspection and
        // bean-definition indexes; this exercises the unmodified Data JDBC runtime.
        PythonContextRuntime.resetContext()
        def compiler = PyronautCompiler.builder().pythonCode('''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, GeneratedValue
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository

@dataclass
class Fruit:
    name: str
    id: Annotated[int | None, Id, GeneratedValue] = None

@JdbcRepository(dialect="H2")
class FruitRepository(CrudRepository[Fruit, int], ABC):
    @abstractmethod
    def save(self, fruit: Fruit) -> Fruit:
        pass

''').targetDir(classesDirectory).build()
        compiler.compile()
        def classLoader = new URLClassLoader([classesDirectory.toURI().toURL()] as URL[], this.class.classLoader)
        def dataSource = new JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:dataclass_field_${UUID.randomUUID()}")
        def schemaConnection = dataSource.connection
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .environments("test")
            .properties(["datasources.default.dialect": "H2"])
            .beanDefinitions(RuntimeBeanDefinition.builder(DataSource, { dataSource } as Supplier<DataSource>)
                .qualifier(Qualifiers.byName("default")).singleton(true).build())
            .start()
        def fruitType = classLoader.loadClass("python.Fruit")
        def introspection = BeanIntrospector.forClassLoader(classLoader).getIntrospection(fruitType)
        def repository = context.getBean(classLoader.loadClass("python.FruitRepository"))
        def definition = context.getBeanDefinition(classLoader.loadClass("python.FruitRepository"))
        def save = definition.getRequiredMethod("save", fruitType)
        def find = definition.executableMethods.find { it.methodName == "findById" && it.arguments.length == 1 }
        schemaConnection.createStatement().withCloseable { statement ->
            statement.execute('CREATE TABLE fruit (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        }

        expect:
        save.stringValue(Query).get() == 'INSERT INTO `fruit` (`name`) VALUES (?)'
        find.stringValue(Query).get() == 'SELECT fruit_.`id`,fruit_.`name` FROM `fruit` fruit_ WHERE (fruit_.`id` = ?)'

        when:
        def original = introspection.instantiate("Apple", null)
        def saved = repository.save(original)
        def found = repository.findById(saved.id).orElseThrow()

        then:
        saved.id != null
        saved.asPolyglotValue().getMember("id").asInt() == saved.id
        found.name == "Apple"
        found.id == saved.id
        repository.findById(saved.id + 1).isEmpty()

        cleanup:
        context?.close()
        schemaConnection?.close()
        classLoader?.close()
        PythonContextRuntime.resetContext()
    }
}
