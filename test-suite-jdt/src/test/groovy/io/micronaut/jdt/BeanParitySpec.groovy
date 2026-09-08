package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Compares the bean definitions produced by javac and the Eclipse JDT compiler for the common
 * dependency injection constructs.
 */
class BeanParitySpec extends AbstractCompilerParitySpec {

    void "test field, method and constructor injection"() {
        expect:
        assertParity([
                'test.A'   : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class A {
}
''',
                'test.B'   : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class B {
}
''',
                'test.Bean': '''
package test;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Bean {

    @Inject
    protected B zulu;

    @Inject
    protected A alpha;

    private final A constructorInjected;

    public Bean(A constructorInjected) {
        this.constructorInjected = constructorInjected;
    }

    @Inject
    void setZebra(B b) {
    }

    @Inject
    void setAardvark(A a) {
    }

    @PostConstruct
    void init() {
    }

    @PreDestroy
    void cleanup() {
    }
}
'''
        ])
    }

    void "test factory with several bean methods"() {
        expect:
        assertParity('test.MyFactory', '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Factory
public class MyFactory {

    @Singleton
    @Named("zulu")
    List<String> zulu() {
        return List.of();
    }

    @Bean
    Map<String, Integer> alpha() {
        return Map.of();
    }

    @Singleton
    CharSequence mike(List<String> zulu) {
        return "";
    }
}
''')
    }

    void "test @Executable methods and inherited members"() {
        expect:
        assertParity([
                'test.Base'   : '''
package test;

public abstract class Base<T> {

    public abstract T zulu();

    public String alpha() {
        return "alpha";
    }

    public void mike(T value) {
    }
}
''',
                'test.Derived': '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
@Executable
public class Derived extends Base<Integer> {

    @Override
    public Integer zulu() {
        return 0;
    }

    public boolean bravo() {
        return true;
    }
}
'''
        ])
    }

    void "test around advice"() {
        expect:
        assertParity([
                'test.Logged'      : '''
package test;

import io.micronaut.aop.Around;

import java.lang.annotation.*;

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Logged {
}
''',
                'test.LoggedBean'  : '''
package test;

import jakarta.inject.Singleton;

@Singleton
@Logged
public class LoggedBean {

    public String zulu(String input) {
        return input;
    }

    public int alpha() {
        return 1;
    }

    public void mike(int a, String b) {
    }
}
'''
        ])
    }

    void "test introduction advice on an interface"() {
        expect:
        assertParity([
                'test.Stubbed'      : '''
package test;

import io.micronaut.aop.Introduction;
import jakarta.inject.Singleton;

import java.lang.annotation.*;

@Introduction
@Singleton
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Stubbed {
}
''',
                'test.StubbedApi'   : '''
package test;

import java.util.List;

@Stubbed
public interface StubbedApi {

    String zulu();

    List<String> alpha(int index);

    default String mike() {
        return "mike";
    }
}
'''
        ])
    }

    void "test @ConfigurationProperties with setters and a nested class"() {
        expect:
        assertParity('test.AppConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app")
public class AppConfiguration {

    private String zulu;
    private int yankee;
    private List<String> alpha;
    private boolean enabled;

    public String getZulu() {
        return zulu;
    }

    public void setZulu(String zulu) {
        this.zulu = zulu;
    }

    public int getYankee() {
        return yankee;
    }

    public void setYankee(int yankee) {
        this.yankee = yankee;
    }

    public List<String> getAlpha() {
        return alpha;
    }

    public void setAlpha(List<String> alpha) {
        this.alpha = alpha;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @ConfigurationProperties("child")
    public static class ChildConfiguration {
        private String zebra;
        private String aardvark;

        public String getZebra() {
            return zebra;
        }

        public void setZebra(String zebra) {
            this.zebra = zebra;
        }

        public String getAardvark() {
            return aardvark;
        }

        public void setAardvark(String aardvark) {
            this.aardvark = aardvark;
        }
    }
}
''')
    }

    void "test @EachProperty class with a @Parameter constructor"() {
        expect:
        assertParity('test.EachConfiguration', '''
package test;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

@EachProperty("each")
public class EachConfiguration {

    private final String name;
    private String zulu;
    private String alpha;

    public EachConfiguration(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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
''')
    }

    void "test generic bean hierarchy"() {
        expect:
        assertParity([
                'test.Repo'       : '''
package test;

import java.util.List;

public interface Repo<E, ID> {

    E findById(ID id);

    List<E> findAll();

    void save(E entity);
}
''',
                'test.Entity'     : '''
package test;

public class Entity {
}
''',
                'test.EntityRepo' : '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Executable
public class EntityRepo implements Repo<Entity, Long> {

    @Override
    public Entity findById(Long id) {
        return null;
    }

    @Override
    public List<Entity> findAll() {
        return List.of();
    }

    @Override
    public void save(Entity entity) {
    }
}
'''
        ])
    }

    void "test qualifiers and multiple constructors"() {
        expect:
        assertParity([
                'test.Engine'    : '''
package test;

public interface Engine {
    String start();
}
''',
                'test.V8'        : '''
package test;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("v8")
public class V8 implements Engine {
    @Override
    public String start() {
        return "v8";
    }
}
''',
                'test.Vehicle'   : '''
package test;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class Vehicle {

    private final Engine engine;

    public Vehicle() {
        this.engine = null;
    }

    @Inject
    public Vehicle(@Named("v8") Engine engine) {
        this.engine = engine;
    }
}
'''
        ])
    }

    void "test bean with varargs and primitive arrays"() {
        expect:
        assertParity('test.VarargsBean', '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
@Executable
public class VarargsBean {

    public String zulu(String... values) {
        return String.join(",", values);
    }

    public int alpha(int[] values, long[]... more) {
        return values.length;
    }
}
''')
    }

    void "test enum introspection and enum valued annotations"() {
        expect:
        assertParity([
                'test.Mode'      : '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum Mode {
    ZULU,
    ALPHA,
    MIKE
}
''',
                'test.WithEnum'  : '''
package test;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(notEnv = {"zulu", "alpha"})
public class WithEnum {

    private Mode mode = Mode.ALPHA;

    public Mode getMode() {
        return mode;
    }
}
'''
        ])
    }
}
