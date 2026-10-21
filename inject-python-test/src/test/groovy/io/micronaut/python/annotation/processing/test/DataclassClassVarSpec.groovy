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

class DataclassClassVarSpec extends AbstractPythonTypeElementSpec {

    void "test ClassVars stay on the Python class rather than becoming instance properties"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import ClassVar, ClassVar as Shared
import typing
import typing as t

@dataclass
class Contact:
    name: str
    kind: ClassVar[str] = "service"
    aliased: Shared[int] = 7
    qualified: typing.ClassVar[str] = "qualified"
    module_alias: t.ClassVar[str] = "module"
    quoted: "ClassVar[str]" = "quoted"
    untyped: ClassVar = "untyped"
    VERSION: int = 1

@dataclass
class ContactChild(Contact):
    kind = "changed"

class AnnotatedMid(Contact):
    kind: str = "annotated change"

@dataclass
class ContactLeaf(AnnotatedMid):
    pass

@dataclass
class Constants:
    kind: ClassVar[str] = "constants"
''')

        when:
        def introspection = getBeanIntrospection(context, "python.Contact")
        def childIntrospection = getBeanIntrospection(context, "python.ContactChild")
        def leafIntrospection = getBeanIntrospection(context, "python.ContactLeaf")
        def constantsIntrospection = getBeanIntrospection(context, "python.Constants")

        then:
        introspection.constructorArguments*.name == ["name", "VERSION"]
        introspection.propertyNames == ["name", "VERSION"] as String[]
        childIntrospection.constructorArguments*.name == ["name", "VERSION"]
        childIntrospection.propertyNames == ["name", "VERSION"] as String[]
        leafIntrospection.constructorArguments*.name == ["name", "VERSION"]
        leafIntrospection.propertyNames == ["name", "VERSION"] as String[]
        constantsIntrospection.constructorArguments.length == 0
        constantsIntrospection.propertyNames.length == 0

        when:
        def contact = introspection.instantiate("Ada", 2)
        def value = contact.asPolyglotValue()
        def pythonClass = value.getMetaObject()

        then:
        contact.name == "Ada"
        contact.VERSION == 2
        value.getMember("name").asString() == "Ada"
        pythonClass.getMember("kind").asString() == "service"
        pythonClass.getMember("aliased").asInt() == 7
        pythonClass.getMember("qualified").asString() == "qualified"
        pythonClass.getMember("module_alias").asString() == "module"
        pythonClass.getMember("quoted").asString() == "quoted"
        pythonClass.getMember("untyped").asString() == "untyped"
        constantsIntrospection.instantiate().asPolyglotValue().getMember("kind").asString() == "constants"
        !introspection.beanType.declaredFields*.name.contains("kind")
        childIntrospection.instantiate("Grace", 3).asPolyglotValue().getMember("kind").asString() == "changed"
        leafIntrospection.instantiate("Lin", 4).asPolyglotValue().getMember("kind").asString() == "annotated change"

        cleanup:
        context?.close()
    }

    void "test ClassVar overrides stay excluded across inheritance and retain field order when reintroduced"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import ClassVar

@dataclass
class Base:
    name: str
    kind: str = "base"

@dataclass
class SharedKind(Base):
    kind: ClassVar[str] = "shared"
    level: int = 1

@dataclass
class Grandchild(SharedKind):
    pass

@dataclass
class InstanceKind(SharedKind):
    kind: str = "instance"

@dataclass
class ClassVarBase:
    kind: ClassVar[str] = "shared"
    name: str = "default"

@dataclass
class InstanceFromClassVar(ClassVarBase):
    kind: str = "instance"
''')

        when:
        def shared = getBeanIntrospection(context, "python.SharedKind")
        def grandchild = getBeanIntrospection(context, "python.Grandchild")
        def instance = getBeanIntrospection(context, "python.InstanceKind")
        def fromClassVar = getBeanIntrospection(context, "python.InstanceFromClassVar")

        then:
        shared.constructorArguments*.name == ["name", "level"]
        shared.propertyNames == ["name", "level"] as String[]
        grandchild.constructorArguments*.name == ["name", "level"]
        grandchild.propertyNames == ["name", "level"] as String[]
        instance.constructorArguments*.name == ["name", "kind", "level"]
        instance.propertyNames == ["name", "kind", "level"] as String[]
        fromClassVar.constructorArguments*.name == ["kind", "name"]
        fromClassVar.propertyNames == ["kind", "name"] as String[]

        when:
        def childValue = grandchild.instantiate("Ada", 4).asPolyglotValue()
        def instanceValue = instance.instantiate("Grace", "personal", 5).asPolyglotValue()
        def fromClassVarValue = fromClassVar.instantiate("private", "Lin").asPolyglotValue()

        then:
        childValue.getMember("name").asString() == "Ada"
        childValue.getMember("kind").asString() == "shared"
        childValue.getMember("level").asInt() == 4
        childValue.getMetaObject().getMember("kind").asString() == "shared"
        instanceValue.getMember("kind").asString() == "personal"
        instanceValue.getMember("level").asInt() == 5
        fromClassVarValue.getMember("kind").asString() == "private"
        fromClassVarValue.getMember("name").asString() == "Lin"

        cleanup:
        context?.close()
    }

    void "test an inherited ClassVar excludes constructor fields across a diamond"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import ClassVar

@dataclass
class A:
    name: str = "default"
    kind: ClassVar[str] = "shared"

    def has_instance_kind(self) -> bool:
        return "kind" in vars(self)

@dataclass
class B(A):
    kind: str = "instance"

@dataclass
class SharedBranch(A):
    pass

class Mid(A):
    pass

@dataclass
class Leaf(SharedBranch, B):
    pass

@dataclass
class PlainLeaf(Mid, B):
    pass
''')

        when:
        def leaf = getBeanIntrospection(context, "python.Leaf")
        def plainLeaf = getBeanIntrospection(context, "python.PlainLeaf")

        then:
        leaf.constructorArguments*.name == ["name"]
        leaf.propertyNames == ["name"] as String[]
        plainLeaf.constructorArguments*.name == ["name"]
        plainLeaf.propertyNames == ["name"] as String[]

        when:
        def child = leaf.instantiate("Ada")
        def plainChild = plainLeaf.instantiate("Grace")
        def value = child.asPolyglotValue()
        def plainValue = plainChild.asPolyglotValue()

        then: "the attribute still resolves through Python's MRO, but is not stored on the instance"
        value.getMember("name").asString() == "Ada"
        value.getMember("kind").asString() == "instance"
        plainValue.getMember("name").asString() == "Grace"
        plainValue.getMember("kind").asString() == "instance"
        !child.has_instance_kind()
        !plainChild.has_instance_kind()

        cleanup:
        context?.close()
    }
}
