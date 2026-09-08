package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Parity checks for the annotation handling paths that make assumptions about the compiler's
 * {@code javax.lang.model} implementation.
 */
class AnnotationEdgeCaseParitySpec extends AbstractCompilerParitySpec {

    void "test explicitly set scalar enum, class and nested annotation members"() {
        expect:
        assertParity([
                'test.Inner'   : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface Inner {
    String value() default "";
    java.lang.annotation.RetentionPolicy policy() default RetentionPolicy.CLASS;
}
''',
                'test.Outer'   : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Outer {
    ElementType elementType() default ElementType.TYPE;
    ElementType[] elementTypes() default {};
    Class<?> type() default Object.class;
    Class<?>[] types() default {};
    Inner inner() default @Inner;
    Inner[] inners() default {};
}
''',
                'test.Subject' : '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

@Singleton
@Executable
@Outer(
    elementType = ElementType.FIELD,
    elementTypes = {ElementType.METHOD, ElementType.PARAMETER},
    type = Map.Entry.class,
    types = {String.class, int.class, String[].class},
    inner = @Inner(value = "scalar", policy = RetentionPolicy.SOURCE),
    inners = {@Inner("one"), @Inner(value = "two", policy = RetentionPolicy.RUNTIME)}
)
public class Subject {

    @Outer(elementType = ElementType.LOCAL_VARIABLE, inner = @Inner("method"))
    public void zulu() {
    }
}
'''
        ])
    }

    void "test @AliasFor members"() {
        expect:
        assertParity([
                'test.Aliased' : '''
package test;

import io.micronaut.context.annotation.AliasFor;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.lang.annotation.*;

@Singleton
@Named
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Aliased {

    @AliasFor(annotation = Named.class, member = "value")
    String value() default "";
}
''',
                'test.Aliasing' : '''
package test;

@Aliased("zulu")
public class Aliasing {
}
'''
        ])
    }

    void "test annotations declared on an interface and inherited by the implementation"() {
        expect:
        assertParity([
                'test.Marked'  : '''
package test;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.*;

@Executable
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Marked {
    String value() default "marked";
}
''',
                'test.Api'     : '''
package test;

@Marked("api")
public interface Api {

    @Marked("zulu")
    String zulu();

    String alpha();
}
''',
                'test.ApiImpl' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class ApiImpl implements Api {

    @Override
    public String zulu() {
        return "zulu";
    }

    @Override
    public String alpha() {
        return "alpha";
    }
}
'''
        ])
    }

    void "test annotations from the classpath with defaults"() {
        expect:
        assertParity('test.FromClasspath', '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

@Prototype
@Order(10)
@Bean(typed = {CharSequence.class, Comparable.class})
public class FromClasspath implements CharSequence, Comparable<String> {

    @Override
    public int length() {
        return 0;
    }

    @Override
    public char charAt(int index) {
        return 0;
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return "";
    }

    @Override
    public int compareTo(String o) {
        return 0;
    }
}
''')
    }

    void "test inner class references in annotation values"() {
        expect:
        assertParity('test.Nested', '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Singleton
@Introspected(classes = {Nested.Inner.class, Nested.Inner.Deepest.class})
public class Nested {

    public static class Inner {

        private String zulu;

        public String getZulu() {
            return zulu;
        }

        public void setZulu(String zulu) {
            this.zulu = zulu;
        }

        public static class Deepest {

            private String alpha;

            public String getAlpha() {
                return alpha;
            }

            public void setAlpha(String alpha) {
                this.alpha = alpha;
            }
        }
    }
}
''')
    }

    void "test generic methods with type variables and wildcards"() {
        expect:
        assertParity('test.Generics', '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Singleton
@Executable
public class Generics {

    public <T> List<T> zulu(Class<T> type, T... values) {
        return List.of();
    }

    public <K extends Comparable<K>, V> Map<K, ? extends V> alpha(Map<? super K, ? extends V> input) {
        return Map.of();
    }

    public <T extends Number & Comparable<T>> T mike(Collection<? extends T> values) {
        return null;
    }

    public void bravo(List<? super Integer> sink, List<?> anything) {
    }
}
''')
    }

    void "test javadoc is captured for configuration metadata"() {
        expect:
        assertParity('test.DocumentedConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Documented configuration.
 */
@ConfigurationProperties("documented")
public class DocumentedConfiguration {

    private String zulu;
    private int alpha;

    /**
     * The zulu value.
     *
     * @return the zulu
     */
    public String getZulu() {
        return zulu;
    }

    /**
     * Sets the zulu value.
     *
     * @param zulu the zulu
     */
    public void setZulu(String zulu) {
        this.zulu = zulu;
    }

    /**
     * The alpha value.
     *
     * @return the alpha
     */
    public int getAlpha() {
        return alpha;
    }

    /**
     * Sets the alpha value.
     *
     * @param alpha the alpha
     */
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }
}
''')
    }

    void "test javadoc is captured for record configuration metadata"() {
        expect:
        assertParity('test.DocumentedRecord', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Documented record configuration.
 *
 * @param zulu the zulu value
 * @param alpha the alpha value
 */
@ConfigurationProperties("documented-record")
public record DocumentedRecord(String zulu, int alpha) {
}
''')
    }

    void "test @Property map binding and @Value expressions"() {
        expect:
        assertParity('test.Injected', '''
package test;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
public class Injected {

    @Property(name = "zulu")
    Map<String, String> zulu;

    @Value("${alpha:[]}")
    List<String> alpha;

    @Value("#{ 1 + 2 }")
    int mike;
}
''')
    }
}
