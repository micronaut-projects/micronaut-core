package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import jakarta.inject.Singleton

/**
 * Divergences a review of the module found between a reflective description and a generated one, each written
 * down as the processors answer it and then as the reflective side has to answer it too.
 */
class ReviewedGapsParitySpec extends AbstractTypeElementSpec {

    private static final String HEADER = '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
'''

    private BeanIntrospection<?> reflectiveOf(BeanIntrospection<?> generated) {
        return ReflectionBeanIntrospection.of(generated.beanType)
    }

    // --- instantiation

    void "a static creator is the way a type is instantiated, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Made", HEADER + '''
@Introspected
class Made {
    private final String name;
    private Made(String name) { this.name = name; }
    public String getName() { return name; }
    @Creator
    public static Made of(String name) { return new Made(name.toUpperCase()); }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)

        expect: "what a generated description answers today: the factory is the constructor of the introspection"
        generated.constructorArguments*.type == [String]
        generated.isBuildable()
        generated.instantiate("made").name == "MADE"
        generated.constructor.arguments*.type == [String]

        and: "and a reflective description answers the same - by type, as a class compiled without parameter names"
        and: "cannot answer the names a generated description reads from the source"
        reflective.constructorArguments*.type == generated.constructorArguments*.type
        reflective.isBuildable() == generated.isBuildable()
        reflective.instantiate("made").name == generated.instantiate("made").name
        reflective.constructor.arguments*.type == generated.constructor.arguments*.type
        reflective.builder().with(0, reflective.constructorArguments[0], "built").build().name == generated.builder().with("name", "built").build().name
    }

    void "a builder the type configures is the builder of the introspection, described either way"() {
        given: "the fixture is compiled by the processor, which writes the introspection of the builder too"
        BeanIntrospection<Built> generated = BeanIntrospector.SHARED.getIntrospection(Built)
        BeanIntrospection<Built> reflective = ReflectionBeanIntrospection.of(Built)

        expect: "what a generated description answers today"
        generated.hasBuilder()
        generated.isBuildable()
        generated.builder().builderArguments*.name.toSorted() == ["count", "name"]
        generated.builder().builderArguments*.type.toSorted { it.name } == [Integer, String]
        generated.builder().buildMethodArguments.length == 0
        with(generated.builder().with("name", "a").with("count", 3).build()) {
            name == "a"
            count == 3
        }

        and: "and a reflective description answers the same"
        reflective.hasBuilder() == generated.hasBuilder()
        reflective.isBuildable() == generated.isBuildable()
        reflective.builder().builderArguments*.name.toSorted() == generated.builder().builderArguments*.name.toSorted()
        reflective.builder().builderArguments*.type.toSorted { it.name } == generated.builder().builderArguments*.type.toSorted { it.name }
        reflective.builder().buildMethodArguments.length == generated.builder().buildMethodArguments.length
        with(reflective.builder().with("name", "a").with("count", 3).build()) {
            name == "a"
            count == 3
        }

        and: "a builder from an existing instance copies its properties, either way"
        Built existing = reflective.builder().with("name", "b").with("count", 5).build()
        reflective.builder().with(existing).with("count", 6).build().name == generated.builder().with(existing).with("count", 6).build().name
        reflective.builder().with(existing).with("count", 6).build().count == 6
    }

    void "a builder reached through a builder method is the builder of the introspection, described either way"() {
        given:
        BeanIntrospection<Shaped> generated = BeanIntrospector.SHARED.getIntrospection(Shaped)
        BeanIntrospection<Shaped> reflective = ReflectionBeanIntrospection.of(Shaped)

        expect: "what a generated description answers today"
        generated.hasBuilder()
        generated.builder().builderArguments*.name == ["label"]
        generated.builder().with("label", "x").build().label == "x"

        and: "and a reflective description answers the same"
        reflective.hasBuilder() == generated.hasBuilder()
        reflective.builder().builderArguments*.name == generated.builder().builderArguments*.name
        reflective.builder().with("label", "x").build().label == generated.builder().with("label", "x").build().label
    }

    // --- properties

    void "field access reads and writes the field even when an accessor exists, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Fielded", HEADER + '''
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Fielded {
    public String name = "field";
    public String getName() { return "getter"; }
    public void setName(String name) { this.name = "setter"; }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def generatedBean = generated.instantiate()
        def reflectiveBean = reflective.instantiate()

        expect: "what a generated description answers today: the value is the field's, not the accessor's"
        generated.getRequiredProperty("name", String).get(generatedBean) == "field"
        reflective.getRequiredProperty("name", String).get(reflectiveBean) == generated.getRequiredProperty("name", String).get(generatedBean)

        when: "written either way"
        generated.getRequiredProperty("name", String).set(generatedBean, "written")
        reflective.getRequiredProperty("name", String).set(reflectiveBean, "written")

        then: "the field holds the value, not the setter's"
        generatedBean.@name == "written"
        reflectiveBean.@name == generatedBean.@name
    }

    void "the accessor prefixes the type declares make the properties, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Styled", HEADER + '''
@Introspected
@AccessorsStyle(readPrefixes = "read", writePrefixes = "write")
class Styled {
    private String name;
    private boolean active;
    public String readName() { return name; }
    public void writeName(String name) { this.name = name; }
    public boolean readActive() { return active; }
    public void writeActive(boolean active) { this.active = active; }
    public String getIgnored() { return "ignored"; }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def bean = reflective.instantiate()

        expect: "what a generated description answers today"
        generated.propertyNames.toSorted() == ["active", "name"]

        and: "and a reflective description answers the same, reading and writing through the prefixed accessors"
        reflective.propertyNames.toSorted() == generated.propertyNames.toSorted()
        reflective.getRequiredProperty("name", String).set(bean, "styled")
        reflective.getRequiredProperty("name", String).get(bean) == "styled"
        !reflective.getRequiredProperty("name", String).isReadOnly()
    }

    void "fluent accessors under an empty prefix make the properties, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Fluent", HEADER + '''
@Introspected
@AccessorsStyle(readPrefixes = "", writePrefixes = "")
class Fluent {
    private String name;
    public String name() { return name; }
    public void name(String name) { this.name = name; }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def bean = reflective.instantiate()

        expect: "what a generated description answers today"
        generated.propertyNames == ["name"]

        and: "and a reflective description answers the same"
        reflective.propertyNames == generated.propertyNames
        reflective.getRequiredProperty("name", String).set(bean, "fluent")
        reflective.getRequiredProperty("name", String).get(bean) == "fluent"
    }

    void "a member declared @Introspected.Property is a property, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Declared", HEADER + '''
@Introspected
class Declared {
    private String hidden = "hidden";
    @Introspected.Property
    String tag = "tag";
    private String secret = "secret";

    @Introspected.Property
    public String label() { return hidden; }
    @Introspected.Property
    public void label(String label) { this.hidden = label; }

    @Introspected.Property(accessKind = Introspected.Property.Access.READ)
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def generatedBean = generated.instantiate()
        def reflectiveBean = reflective.instantiate()

        expect: "what a generated description answers today: the method-named property, the field, and a read-only accessor"
        generated.propertyNames.toSorted() == ["label", "secret", "tag"]
        generated.getRequiredProperty("label", String).get(generatedBean) == "hidden"
        generated.getRequiredProperty("tag", String).get(generatedBean) == "tag"
        generated.getRequiredProperty("secret", String).isReadOnly()
        !generated.getRequiredProperty("label", String).isReadOnly()

        and: "and a reflective description answers the same"
        reflective.propertyNames.toSorted() == generated.propertyNames.toSorted()
        reflective.getRequiredProperty("label", String).get(reflectiveBean) == generated.getRequiredProperty("label", String).get(generatedBean)
        reflective.getRequiredProperty("tag", String).get(reflectiveBean) == generated.getRequiredProperty("tag", String).get(generatedBean)
        reflective.getRequiredProperty("secret", String).isReadOnly() == generated.getRequiredProperty("secret", String).isReadOnly()
        reflective.getRequiredProperty("label", String).isReadOnly() == generated.getRequiredProperty("label", String).isReadOnly()

        when:
        reflective.getRequiredProperty("label", String).set(reflectiveBean, "relabelled")
        generated.getRequiredProperty("label", String).set(generatedBean, "relabelled")

        then:
        reflective.getRequiredProperty("label", String).get(reflectiveBean) == generated.getRequiredProperty("label", String).get(generatedBean)
    }

    void "a property of a variable type stays a variable, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Box", HEADER + '''
@Introspected
class Box<T> {
    private T value;
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)

        expect: "what a generated description answers today"
        generated.getRequiredProperty("value", Object).asArgument().isTypeVariable()

        and: "and a reflective description answers the same"
        reflective.getRequiredProperty("value", Object).asArgument().isTypeVariable() == generated.getRequiredProperty("value", Object).asArgument().isTypeVariable()
        reflective.getRequiredProperty("value", Object).asArgument().type == generated.getRequiredProperty("value", Object).asArgument().type
    }

    // --- methods

    void "a default method of an interface is a bean method of the class inheriting it, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Person", HEADER + '''
interface Greeter {
    @Executable
    default String greet() { return "hi"; }
}

@Introspected
class Person implements Greeter {
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def bean = reflective.instantiate()

        expect: "what a generated description answers today"
        generated.beanMethods*.name == ["greet"]
        generated.beanMethods[0].invoke(bean) == "hi"

        and: "and a reflective description answers the same"
        reflective.beanMethods*.name.contains("greet")
        reflective.beanMethods.find { it.name == "greet" }.invoke(bean) == generated.beanMethods[0].invoke(bean)
    }

    void "an inherited generic method is described as the inheriting type sees it, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Child", HEADER + '''
class Base<T> {
    @Executable
    public T echo(T value) { return value; }
}

@Introspected
class Child extends Base<String> {
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def method = generated.beanType.getMethod("echo", Object)

        expect: "what a generated description answers today: the variable is the type the sub type gives it"
        generated.beanMethods.find { it.name == "echo" }.arguments[0].type == String
        generated.beanMethods.find { it.name == "echo" }.returnType.type == String

        and: "an executable method over the inherited method invoked on the sub type answers the same"
        def executable = new ReflectionExecutableMethod(generated.beanType, method)
        executable.arguments[0].type == generated.beanMethods.find { it.name == "echo" }.arguments[0].type
        executable.returnType.type == generated.beanMethods.find { it.name == "echo" }.returnType.type
    }

    void "an overriding generic method keeps its variable through the hierarchy, described either way"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Identity", HEADER + '''
interface Ids {
    <T> T id(T value);
}

@Introspected
class Identity implements Ids {
    @Executable
    @Override
    public <T> T id(T value) { return value; }
}
''')
        BeanIntrospection<?> reflective = reflectiveOf(generated)
        def hierarchy = MethodHierarchy.resolve(new ReflectionBeanIntrospector(BeanIntrospector.SHARED), generated.beanType, "id", Object)

        expect: "what a generated description answers today"
        generated.beanMethods.find { it.name == "id" }.returnType.asArgument().isTypeVariable()
        generated.beanMethods.find { it.name == "id" }.arguments[0].isTypeVariable()

        and: "and the merged hierarchy answers the same"
        hierarchy.returnArgument().isTypeVariable() == generated.beanMethods.find { it.name == "id" }.returnType.asArgument().isTypeVariable()
        hierarchy.arguments()[0].isTypeVariable() == generated.beanMethods.find { it.name == "id" }.arguments[0].isTypeVariable()
        reflective.beanMethods.find { it.name == "id" }.returnType.asArgument().isTypeVariable()
    }

    void "a method overridden along one line of super classes is not declared in parallel"() {
        given:
        def introspector = new ReflectionBeanIntrospector(BeanIntrospector.SHARED)

        expect:
        def hierarchy = MethodHierarchy.resolve(introspector, LinearOverride.Leaf, "act", String)
        hierarchy.inherited()*.declaringType() == [LinearOverride.Parent, LinearOverride.Grand]
        !hierarchy.parallel()
    }

    // --- definitions

    void "the scope a definition is given decides whether it is a singleton"() {
        expect: "a singleton class registered under another scope is not one"
        DefJob.getAnnotation(Singleton) != null
        !ReflectionBeanDefinition.builder(DefJob).scope(Prototype).build().isSingleton()
        ReflectionBeanDefinition.builder(DefJob).scope(Prototype).build().scopeName.get() == Prototype.name

        and: "a class without a scope registered as a singleton is one"
        ReflectionBeanDefinition.builder(Constructors.OnlyPublic).scope(Singleton).build().isSingleton()

        and: "what the class declares holds when the builder says nothing"
        ReflectionBeanDefinition.builder(DefJob).build().isSingleton()

        and: "and an explicit answer wins over both"
        ReflectionBeanDefinition.builder(DefJob).scope(Prototype).singleton(true).build().isSingleton()
    }
}
