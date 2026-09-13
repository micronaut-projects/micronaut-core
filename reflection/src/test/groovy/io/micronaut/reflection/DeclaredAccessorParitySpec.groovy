package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection

/**
 * What {@link io.micronaut.core.annotation.Introspected.Property} on an accessor makes of the types deriving from
 * the one declaring it, described either way. The annotation is not {@code @Inherited}, so the processors read it
 * on the most derived declaration alone: an override hides the declaration it overrides and carries no annotation
 * of its own, whereas a type inheriting the accessor without overriding it reads the declaration itself. A
 * reflective description reads it the same way, an override told from an overload of the same arity by the
 * parameters the declaration resolves to for the described type.
 */
class DeclaredAccessorParitySpec extends AbstractTypeElementSpec {

    private static final String HEADER = '''
package test;
import io.micronaut.core.annotation.Introspected;
'''

    private static final String DECLARING = '''
interface A { @Introspected.Property String value(); }
'''

    private static final String READING = '''
interface R { @Introspected.Property(accessKind = Introspected.Property.Access.READ) String getValue(); }
'''

    private static final String GENERIC = '''
interface Repo<T> { @Introspected.Property T item(); @Introspected.Property void store(T t); }
'''

    private static final String UNRELATED = '''
interface Left { String value(); }
interface Right { @Introspected.Property String value(); }
'''

    private static final String BASE = '''
class Base { @Introspected.Property public String value() { return "base"; } }
'''

    void "#label"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test." + type, HEADER + source)
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect: "the generated description reports the properties of the most derived declarations"
        generated.beanProperties*.name.toSorted() == properties

        and: "and the reflective description reports the same, read-only where the generated one is"
        reflective.beanProperties*.name.toSorted() == generated.beanProperties*.name.toSorted()
        reflective.beanProperties.collectEntries { [it.name, it.readOnly] } == generated.beanProperties.collectEntries { [it.name, it.readOnly] }

        where:
        label                                                                     | type            | source                                                                                                              | properties
        "an interface inherits the accessor of the interface it extends"          | "Sub"           | DECLARING + '@Introspected interface Sub extends A {}'                                                              | ["value"]
        "an interface redeclaring the accessor hides the declaration"             | "ReSub"         | DECLARING + '@Introspected interface ReSub extends A { String value(); }'                                           | []
        "an abstract class inherits the accessor of its interface"                | "Abs"           | DECLARING + '@Introspected abstract class Abs implements A {}'                                                      | ["value"]
        "a class overriding the accessor hides the declaration"                   | "Impl"          | DECLARING + '@Introspected class Impl implements A { public String value() { return "x"; } }'                       | []
        "a class inherits the accessor of its super class"                        | "Inheriting"    | BASE + '@Introspected class Inheriting extends Base {}'                                                             | ["value"]
        "a class overriding the accessor of its super class hides the declaration" | "Overriding"   | BASE + '@Introspected class Overriding extends Base { public String value() { return "sub"; } }'                    | []
        "a method inherited from a super class implements the interface accessor" | "SubOfPlain"    | DECLARING + 'class Plain { public String value() { return "p"; } } @Introspected class SubOfPlain extends Plain implements A {}' | []
        "an override hides the access kind of the declaration too"                | "Rw"            | READING + '@Introspected class Rw implements R { public String getValue() { return "x"; } public void setValue(String v) {} }' | ["value"]
        "an interface overriding generic accessors hides the declarations"        | "StringRepo"    | GENERIC + '@Introspected interface StringRepo extends Repo<String> { @Override String item(); @Override void store(String t); }' | []
        "a class overriding generic accessors hides the declarations"             | "StringRepoImpl" | GENERIC + '@Introspected class StringRepoImpl implements Repo<String> { public String item() { return "i"; } public void store(String t) {} }' | []
        "an abstract class inherits generic accessors"                            | "AbsRepo"       | GENERIC + '@Introspected abstract class AbsRepo implements Repo<String> {}'                                         | ["item", "store"]
        "the declaration of an unrelated interface is not hidden by an earlier one" | "Both"        | UNRELATED + '@Introspected abstract class Both implements Left, Right {}'                                           | ["value"]
        "nor by one an interface extends before it"                               | "BothI"         | UNRELATED + '@Introspected interface BothI extends Left, Right {}'                                                  | ["value"]
    }
}
