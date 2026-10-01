package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Compares the configuration binding metadata produced by javac and the Eclipse JDT compiler.
 */
class ConfigurationParitySpec extends AbstractCompilerParitySpec {

    void "test immutable configuration with @ConfigurationInject"() {
        expect:
        assertParity('test.ImmutableConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

@ConfigurationProperties("immutable")
public class ImmutableConfiguration {

    private final String zulu;
    private final int yankee;
    private final List<String> alpha;

    @ConfigurationInject
    public ImmutableConfiguration(String zulu, @Nullable Integer yankee, List<String> alpha) {
        this.zulu = zulu;
        this.yankee = yankee == null ? 0 : yankee;
        this.alpha = alpha;
    }

    public String getZulu() {
        return zulu;
    }

    public int getYankee() {
        return yankee;
    }

    public List<String> getAlpha() {
        return alpha;
    }
}
''')
    }

    void "test configuration properties interface"() {
        expect:
        assertParity('test.InterfaceConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("iface")
public interface InterfaceConfiguration {

    String getZulu();

    @Nullable
    Integer getYankee();

    default String getAlpha() {
        return "alpha";
    }
}
''')
    }

    void "test @ConfigurationBuilder"() {
        expect:
        assertParity([
                'test.Builder'          : '''
package test;

public class Builder {

    private String zulu;
    private int alpha;

    public Builder zulu(String zulu) {
        this.zulu = zulu;
        return this;
    }

    public Builder alpha(int alpha) {
        this.alpha = alpha;
        return this;
    }

    public String getZulu() {
        return zulu;
    }

    public int getAlpha() {
        return alpha;
    }
}
''',
                'test.BuilderConfiguration' : '''
package test;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("builder")
public class BuilderConfiguration {

    @ConfigurationBuilder(prefixes = "")
    Builder builder = new Builder();

    public Builder getBuilder() {
        return builder;
    }
}
'''
        ])
    }

    void "test @EachBean and @Replaces"() {
        expect:
        assertParity([
                'test.Base'     : '''
package test;

public interface Base {
    String name();
}
''',
                'test.Original' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class Original implements Base {
    @Override
    public String name() {
        return "original";
    }
}
''',
                'test.Replacement' : '''
package test;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Singleton
@Replaces(Original.class)
public class Replacement implements Base {
    @Override
    public String name() {
        return "replacement";
    }
}
''',
                'test.PerBase'  : '''
package test;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;

@EachBean(Base.class)
public class PerBase {

    private final Base base;

    public PerBase(@Parameter Base base) {
        this.base = base;
    }

    public Base getBase() {
        return base;
    }
}
'''
        ])
    }

    void "test bridge methods from a generic super type"() {
        expect:
        assertParity([
                'test.Handler'        : '''
package test;

public interface Handler<T> {

    void handle(T event);

    T create();
}
''',
                'test.StringHandler'  : '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Singleton
@Executable
@Introspected
public class StringHandler implements Handler<String> {

    @Override
    public void handle(String event) {
    }

    @Override
    public String create() {
        return "";
    }
}
'''
        ])
    }

    void "test overloaded methods"() {
        expect:
        assertParity('test.Overloaded', '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Executable
public class Overloaded {

    public String zulu() {
        return "";
    }

    public String zulu(String a) {
        return a;
    }

    public String zulu(String a, int b) {
        return a + b;
    }

    public String zulu(List<String> a) {
        return a.toString();
    }

    public String alpha(int a) {
        return "" + a;
    }
}
''')
    }

    void "test optional and reactive return types"() {
        expect:
        assertParity('test.Returns', '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Singleton
@Executable
public class Returns {

    public Optional<String> zulu() {
        return Optional.empty();
    }

    public OptionalInt yankee() {
        return OptionalInt.empty();
    }

    public CompletableFuture<List<Map<String, Integer>>> alpha() {
        return CompletableFuture.completedFuture(List.of());
    }

    public Stream<? extends CharSequence> mike() {
        return Stream.empty();
    }

    public <T extends Number> T bravo(Class<T> type) {
        return null;
    }
}
''')
    }

    void "test bean provider and nullable injection"() {
        expect:
        assertParity([
                'test.Thing'    : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class Thing {
}
''',
                'test.Consumer' : '''
package test;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class Consumer {

    @Inject
    BeanProvider<Thing> zulu;

    @Inject
    Provider<Thing> alpha;

    @Inject
    List<Thing> mike;

    @Inject
    Optional<Thing> bravo;

    @Inject
    Consumer(@Nullable Thing thing) {
    }
}
'''
        ])
    }

    void "test inner enum and nested interfaces"() {
        expect:
        assertParity('test.Container', '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Singleton
public class Container {

    @Introspected
    public enum State {
        ZULU,
        ALPHA,
        MIKE
    }

    @Introspected
    public record Payload(State state, String zulu, int alpha) {
    }

    public interface Callback {
        void call(Payload payload);
    }
}
''')
    }

    void "test static and instance members mixed with initializers"() {
        expect:
        assertParity('test.Mixed', '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class Mixed {

    public static final String CONSTANT = "constant";

    static {
        System.getProperty("zulu");
    }

    private String zulu;

    {
        zulu = "init";
    }

    private String alpha;

    public static String staticMethod() {
        return "static";
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
}
