package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Parity checks for inheritance, generic type argument resolution and the different advice kinds.
 */
class InheritanceParitySpec extends AbstractCompilerParitySpec {

    void "test inherited injection points and lifecycle methods"() {
        expect:
        assertParity([
                'test.Dep'    : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class Dep {
}
''',
                'test.Parent' : '''
package test;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

public abstract class Parent {

    @Inject
    protected Dep zulu;

    @Inject
    protected Dep alpha;

    @Inject
    void setParentThing(Dep dep) {
    }

    @PostConstruct
    void parentInit() {
    }
}
''',
                'test.Child'  : '''
package test;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Child extends Parent {

    @Inject
    Dep whiskey;

    @Inject
    Dep bravo;

    @Inject
    void setChildThing(Dep dep) {
    }

    @PostConstruct
    void childInit() {
    }
}
'''
        ])
    }

    void "test deep generic interface hierarchy type arguments"() {
        expect:
        assertParity([
                'test.Level1' : '''
package test;

public interface Level1<A, B> {
    A first();
    B second();
}
''',
                'test.Level2' : '''
package test;

import java.util.List;

public interface Level2<C> extends Level1<String, List<C>> {
}
''',
                'test.Level3' : '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Executable
@Introspected
public class Level3 implements Level2<Integer> {

    @Override
    public String first() {
        return "";
    }

    @Override
    public List<Integer> second() {
        return List.of();
    }
}
'''
        ])
    }

    void "test proxy target advice on a concrete class"() {
        expect:
        assertParity([
                'test.Cached'     : '''
package test;

import io.micronaut.aop.Around;

import java.lang.annotation.*;

@Around(proxyTarget = true)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Cached {
}
''',
                'test.CachedBean' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
@Cached
public class CachedBean {

    public String zulu(String key) {
        return key;
    }

    public int alpha() {
        return 1;
    }
}
'''
        ])
    }

    void "test advice on an abstract class"() {
        expect:
        assertParity([
                'test.Traced'      : '''
package test;

import io.micronaut.aop.Around;

import java.lang.annotation.*;

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Traced {
}
''',
                'test.AbstractBase': '''
package test;

public abstract class AbstractBase {

    public abstract String zulu();

    public String alpha() {
        return "alpha";
    }
}
''',
                'test.TracedBean'  : '''
package test;

import jakarta.inject.Singleton;

@Singleton
@Traced
public class TracedBean extends AbstractBase {

    @Override
    public String zulu() {
        return "zulu";
    }

    public String mike() {
        return "mike";
    }
}
'''
        ])
    }

    void "test record implementing a generic interface"() {
        expect:
        assertParity([
                'test.Named'      : '''
package test;

public interface Named<T> {
    T value();
    String zulu();
}
''',
                'test.NamedRecord': '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Introspected
@Singleton
public record NamedRecord(String zulu, Integer value, boolean alpha) implements Named<Integer> {
}
'''
        ])
    }

    void "test record with an overridden accessor and static members"() {
        expect:
        assertParity('test.OverriddenRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record OverriddenRecord(String zulu, int yankee, String alpha) {

    public static final String CONSTANT = "constant";

    private static int counter = 0;

    @Override
    public String zulu() {
        return zulu == null ? "" : zulu;
    }

    public static OverriddenRecord empty() {
        return new OverriddenRecord("", 0, "");
    }
}
''')
    }

    void "test configuration properties inheritance"() {
        expect:
        assertParity([
                'test.BaseConfiguration'  : '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("base")
public class BaseConfiguration {

    private String zulu;
    private String alpha;

    public String getZulu() {
        return zulu;
    }

    public void setZulu(String zulu) {
        this.zulu = zulu;
    }

    public String getAlpha() {
        return alpha;
    }

    public void setAlpha(String alpha) {
        this.alpha = alpha;
    }
}
''',
                'test.ChildConfiguration' : '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("child")
public class ChildConfiguration extends BaseConfiguration {

    private String whiskey;
    private String bravo;

    public String getWhiskey() {
        return whiskey;
    }

    public void setWhiskey(String whiskey) {
        this.whiskey = whiskey;
    }

    public String getBravo() {
        return bravo;
    }

    public void setBravo(String bravo) {
        this.bravo = bravo;
    }
}
'''
        ])
    }

    void "test interface with static and private methods"() {
        expect:
        assertParity('test.WithStatics', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;

@Introspected
@Executable
public interface WithStatics {

    String getZulu();

    static WithStatics create() {
        return () -> "zulu";
    }

    private String helper() {
        return "helper";
    }

    default String getAlpha() {
        return helper();
    }
}
''')
    }

    void "test bean extending an abstract class loaded from the classpath"() {
        expect:
        assertParity('test.FromBinary', """
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

import java.util.AbstractList;

@Singleton
@Executable
@Introspected
public class FromBinary extends AbstractList<String> {

    private String zulu;
    private String alpha;

    @Override
    public String get(int index) {
        return "";
    }

    @Override
    public int size() {
        return 0;
    }

    public String getZulu() {
        return zulu;
    }

    public void setZulu(String zulu) {
        this.zulu = zulu;
    }

    public String getAlpha() {
        return alpha;
    }

    public void setAlpha(String alpha) {
        this.alpha = alpha;
    }
}
""")
    }

    void "test bean implementing several interfaces from the classpath"() {
        expect:
        assertParity('test.ManyInterfaces', """
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;
import jakarta.inject.Singleton;

import java.io.Closeable;
import java.util.function.Function;

@Singleton
@Executable
public class ManyInterfaces implements Ordered, Toggleable, Closeable, Function<String, Integer> {

    @Override
    public Integer apply(String s) {
        return s.length();
    }

    @Override
    public void close() {
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
""")
    }

    void "test factory returning generic and disposable beans"() {
        expect:
        assertParity([
                'test.Resource'    : '''
package test;

import java.io.Closeable;

public class Resource<T> implements Closeable {

    private final T value;

    public Resource(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public void close() {
    }
}
''',
                'test.ResourceFactory' : '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Factory
public class ResourceFactory {

    @Singleton
    @Bean(preDestroy = "close")
    Resource<String> zulu() {
        return new Resource<>("zulu");
    }

    @Singleton
    @Named("alpha")
    Resource<Map<String, List<Integer>>> alpha() {
        return new Resource<>(Map.of());
    }
}
'''
        ])
    }
}
