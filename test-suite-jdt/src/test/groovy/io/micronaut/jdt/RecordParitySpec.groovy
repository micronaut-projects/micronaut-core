package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Records are the construct where javac and JDT differ most: JDT reports the accessor methods of a
 * record in alphabetical order from {@code getEnclosedElements()}, javac in declaration order.
 */
class RecordParitySpec extends AbstractCompilerParitySpec {

    void "test @EachProperty record with @Parameter that is not alphabetically first"() {
        expect:
        assertParity('test.UnorderedConfiguration', '''
package test;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

@EachProperty("demos")
public record UnorderedConfiguration(
    @Parameter String name,
    String mode,
    boolean enabled) {
}
''')
    }

    void "test @ConfigurationProperties record with non alphabetical components"() {
        expect:
        assertParity('test.ZebraConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("zebra")
public record ZebraConfiguration(
    String zulu,
    int yankee,
    boolean xray,
    String alpha) {
}
''')
    }

    void "test introspected record with non alphabetical components"() {
        expect:
        assertParity('test.ReverseRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

@Introspected
public record ReverseRecord(
    String zulu,
    @Nullable String yankee,
    int xray,
    java.util.List<String> whiskey) {
}
''')
    }

    void "test record with compact constructor and defaults"() {
        expect:
        assertParity('test.CompactRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record CompactRecord(String zulu, int yankee, String alpha) {
    public CompactRecord {
        if (zulu == null) {
            zulu = "default";
        }
    }
}
''')
    }

    void "test record with explicit canonical constructor and extra accessor"() {
        expect:
        assertParity('test.ExplicitRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ExplicitRecord(String zulu, int yankee) {
    public ExplicitRecord(String zulu, int yankee) {
        this.zulu = zulu;
        this.yankee = yankee;
    }

    public String getDerived() {
        return zulu + yankee;
    }
}
''')
    }

    void "test record with generic components"() {
        expect:
        assertParity('test.GenericRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Map;

@Introspected
public record GenericRecord<T extends CharSequence>(
    Map<String, List<T>> zulu,
    List<? extends Number> yankee,
    T alpha) {
}
''')
    }

    void "test record as a bean with injected components"() {
        expect:
        assertParity([
                'test.Dependency': '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class Dependency {
}
''',
                'test.RecordBean' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public record RecordBean(Dependency zulu, Dependency alpha) {
}
'''
        ])
    }

    void "test nested record inside a configuration properties class"() {
        expect:
        assertParity('test.OuterConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

@ConfigurationProperties("outer")
public class OuterConfiguration {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @EachProperty("inner")
    public record InnerConfiguration(@Parameter String name, int zulu, boolean alpha) {
    }
}
''')
    }
}
