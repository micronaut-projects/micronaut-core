package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A visitor that cannot finish a type in the current round postpones it. The
 * other visitors still visit the type in that round, but the visitor context
 * refuses to write output for it, and every visitor visits it again in the
 * next round. Micronaut Serialization's visitor postpones a {@code @Serdeable}
 * type this way when one of its property types is itself postponed, such as a
 * record whose static method returns a type generated in the same
 * compilation. The type's introspection must be written in the round that
 * visits it again.
 */
class TypePostponedByAnotherVisitorIntrospectionSpec extends AbstractTypeElementSpec {

    void "a type another visitor postpones is introspected in the next round"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Holder', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
@PostponeOnce
public record Holder(String name) {}

@interface PostponeOnce {}
''')

        then:
        introspection != null
        introspection.instantiate('holder').name() == 'holder'
    }

    void "a type with a builder that another visitor postpones is introspected with its builder in the next round"() {
        when:
        ClassLoader classLoader = buildClassLoader('test.Holder', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = Holder.Builder.class))
@PostponeOnce
public class Holder {
    private final String name;

    private Holder(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static final class Builder {
        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Holder build() {
            return new Holder(name);
        }
    }
}

@interface PostponeOnce {}
''')

        then:
        load(classLoader, 'test.$Holder$Introspection').hasBuilder()
        load(classLoader, 'test.$Holder$Builder$Introspection').beanType.name == 'test.Holder$Builder'
    }

    private static BeanIntrospection load(ClassLoader classLoader, String introspectionName) {
        return (classLoader.loadClass(introspectionName).newInstance() as BeanIntrospectionReference).load()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new PostponeOnceVisitor(), new IntrospectedTypeElementVisitor()]
    }

    /**
     * Postpones each type annotated {@code test.PostponeOnce} the first time it
     * visits it. As an isolating visitor with a higher order, it visits a type
     * before {@link IntrospectedTypeElementVisitor} does in the same round.
     */
    static class PostponeOnceVisitor implements TypeElementVisitor<Object, Object> {

        private final Set<String> postponed = new HashSet<>()

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.hasDeclaredAnnotation('test.PostponeOnce') && postponed.add(element.name)) {
                throw new ElementPostponedToNextRoundException(element)
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
