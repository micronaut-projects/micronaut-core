package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Compares the annotation metadata produced by javac and the Eclipse JDT compiler.
 */
class AnnotationMetadataParitySpec extends AbstractCompilerParitySpec {

    void "test annotation defaults, arrays and nested annotations"() {
        expect:
        assertParity([
                'test.Nested'    : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface Nested {
    String value() default "zulu";
    int number() default 42;
}
''',
                'test.Complex'   : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface Complex {
    String[] strings() default {"zulu", "alpha"};
    int[] numbers() default {3, 1, 2};
    Class<?> type() default String.class;
    Class<?>[] types() default {String.class, Integer.class};
    Nested nested() default @Nested;
    Nested[] nesteds() default {@Nested("a"), @Nested("b")};
    ElementType elementType() default ElementType.METHOD;
}
''',
                'test.Annotated' : '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
@Executable
@Complex(strings = {"one", "two"}, numbers = {9, 8}, nested = @Nested("custom"), type = Long.class)
public class Annotated {

    @Complex
    private String zulu;

    @Complex(numbers = {1})
    public String alpha(@Complex(strings = "p") String param) {
        return param;
    }
}
'''
        ])
    }

    void "test repeatable annotations"() {
        expect:
        assertParity([
                'test.Tags' : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Tags {
    Tag[] value();
}
''',
                'test.Tag'  : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Tags.class)
public @interface Tag {
    String value();
}
''',
                'test.Tagged' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
@Tag("zulu")
@Tag("alpha")
public class Tagged {
}
'''
        ])
    }

    void "test inherited and meta annotations"() {
        expect:
        assertParity([
                'test.Meta'       : '''
package test;

import jakarta.inject.Singleton;

import java.lang.annotation.*;

@Singleton
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.TYPE)
public @interface Meta {
    String name() default "meta";
}
''',
                'test.MetaParent' : '''
package test;

@Meta(name = "parent")
public class MetaParent {
}
''',
                'test.MetaChild'  : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class MetaChild extends MetaParent {
}
'''
        ])
    }

    void "test type use annotations on generics"() {
        expect:
        assertParity([
                'test.TypeUse'  : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface TypeUse {
    String value() default "";
}
''',
                'test.TypeUsed' : '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
@Executable
@Introspected
public class TypeUsed {

    private @TypeUse("field") String zulu;

    public @TypeUse("return") String getZulu() {
        return zulu;
    }

    public void setZulu(@TypeUse("param") String zulu) {
        this.zulu = zulu;
    }

    public List<@TypeUse("arg") String> alpha(Map<@TypeUse("k") String, @TypeUse("v") Integer> map) {
        return List.of();
    }
}
'''
        ])
    }

    void "test annotation aliases and requires"() {
        expect:
        assertParity('test.RequiresBean', '''
package test;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "zulu.enabled", value = "true")
@Requires(missingBeans = String.class)
public class RequiresBean {

    @Value("${zulu.name:default}")
    private String name;

    @Property(name = "zulu.other")
    private String other;
}
''')
    }

    void "test annotations on a package"() {
        expect:
        assertParity([
                'test.pkg.package-info': '''
@io.micronaut.context.annotation.Requires(property = "zulu")
package test.pkg;
''',
                'test.pkg.PackageBean' : '''
package test.pkg;

import jakarta.inject.Singleton;

@Singleton
public class PackageBean {
}
'''
        ])
    }
}
