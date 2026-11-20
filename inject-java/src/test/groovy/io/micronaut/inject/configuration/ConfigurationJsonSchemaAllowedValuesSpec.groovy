package io.micronaut.inject.configuration

import groovy.json.JsonSlurper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import org.intellij.lang.annotations.Language

class ConfigurationJsonSchemaAllowedValuesSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        new JavaParser() {}
    }

    private Map readSchema(String fqcn, @Language("java") String cls) {
        String json = buildAndReadResourceAsString("META-INF/micronaut-configuration-schemas/${fqcn}.json", cls)
        new JsonSlurper().parseText(json) as Map
    }

    void "@AllowedValues on a String field or setter writes the enum keyword"() {
        when:
        Map m = readSchema('test.KafkaProps', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AllowedValues;

@ConfigurationProperties("kafka")
class KafkaProps {
    @AllowedValues({"earliest", "latest", "none"})
    private String autoOffsetReset = "latest";
    private String compression;
    private String name;

    public String getAutoOffsetReset() { return autoOffsetReset; }
    public void setAutoOffsetReset(String v) { this.autoOffsetReset = v; }

    public String getCompression() { return compression; }
    @AllowedValues({"gzip", "snappy"})
    public void setCompression(String v) { this.compression = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
}
''')

        then:
        Map props = m.get('properties')
        props['auto-offset-reset'].type == 'string'
        props['auto-offset-reset'].enum == ['earliest', 'latest', 'none']
        props['compression'].type == 'string'
        props['compression'].enum == ['gzip', 'snappy']
        props['name'].type == 'string'
        !props['name'].containsKey('enum')
    }

    void "@AllowedValues values are written with the JSON type of the property"() {
        when:
        Map m = readSchema('test.NumProps', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AllowedValues;

@ConfigurationProperties("num")
class NumProps {
    @AllowedValues({"1", "2", "4"})
    private int threads;
    @AllowedValues({"0.5", "1.5"})
    private Double ratio;
    @AllowedValues({"1.1", "2"})
    private float factor;
    @AllowedValues("true")
    private boolean enabled;

    public int getThreads() { return threads; }
    public void setThreads(int v) { this.threads = v; }
    public Double getRatio() { return ratio; }
    public void setRatio(Double v) { this.ratio = v; }
    public float getFactor() { return factor; }
    public void setFactor(float v) { this.factor = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
}
''')

        then:
        Map props = m.get('properties')
        props.threads.type == 'integer'
        props.threads.enum == [1, 2, 4]
        props.ratio.type == 'number'
        props.ratio.enum == [0.5, 1.5]
        props.factor.type == 'number'
        props.factor.enum == [1.1, 2.0]
        props.enabled.type == 'boolean'
        props.enabled.enum == [true]
    }

    void "@AllowedValues applies to the elements of a collection or array and the values of a map"() {
        when:
        Map m = readSchema('test.ContainerProps', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AllowedValues;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties("containers")
class ContainerProps {
    @AllowedValues({"a", "b"})
    private List<String> list;
    @AllowedValues({"c", "d"})
    private String[] array;
    @AllowedValues({"e", "f"})
    private Map<String, String> map;
    @AllowedValues({"g", "h"})
    private Optional<String> optional = Optional.empty();

    public List<String> getList() { return list; }
    public void setList(List<String> v) { this.list = v; }
    public String[] getArray() { return array; }
    public void setArray(String[] v) { this.array = v; }
    public Map<String, String> getMap() { return map; }
    public void setMap(Map<String, String> v) { this.map = v; }
    public Optional<String> getOptional() { return optional; }
    public void setOptional(Optional<String> v) { this.optional = v; }
}
''')

        then:
        Map props = m.get('properties')
        props.list.type == 'array'
        props.list.items == [type: 'string', enum: ['a', 'b']]
        !props.list.containsKey('enum')
        props.array.type == 'array'
        props.array.items == [type: 'string', enum: ['c', 'd']]
        props.map.type == 'object'
        props.map.additionalProperties == [type: 'string', enum: ['e', 'f']]
        props.optional.type == 'string'
        props.optional.enum == ['g', 'h']
    }

    void "@AllowedValues narrows the constants of an enum"() {
        when:
        Map m = readSchema('test.EnumProps', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AllowedValues;
import java.util.List;

@ConfigurationProperties("enums")
class EnumProps {
    @AllowedValues({"RED", "GREEN"})
    private Color color;
    private Color any;
    private Color[] colors;

    public Color getColor() { return color; }
    public void setColor(Color v) { this.color = v; }
    public Color getAny() { return any; }
    public void setAny(Color v) { this.any = v; }
    public Color[] getColors() { return colors; }
    public void setColors(Color[] v) { this.colors = v; }
}

enum Color { RED, GREEN, BLUE }
''')

        then:
        Map props = m.get('properties')
        props.color.type == 'string'
        props.color.enum == ['RED', 'GREEN']
        props.any.enum == ['RED', 'GREEN', 'BLUE']
        props.colors.items == [type: 'string', enum: ['RED', 'GREEN', 'BLUE']]
    }

    void "@AllowedValues on @EachProperty entries, records and as a meta-annotation"() {
        when:
        Map each = readSchema('test.Topics', '''
package test;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.AllowedValues;

@EachProperty("topics")
class Topics {
    @AllowedValues({"delete", "compact"})
    private String cleanupPolicy;

    public String getCleanupPolicy() { return cleanupPolicy; }
    public void setCleanupPolicy(String v) { this.cleanupPolicy = v; }
}
''')
        Map record = readSchema('test.RecordProps', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AllowedValues;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@ConfigurationProperties("rec")
record RecordProps(@AllowedValues({"x", "y"}) String mode, @Level String level) {
}

@AllowedValues({"debug", "info"})
@Retention(RetentionPolicy.RUNTIME)
@interface Level {
}
''')

        then:
        each.'$defs'.Entry.get('properties')['cleanup-policy'].enum == ['delete', 'compact']
        record.get('properties').mode.enum == ['x', 'y']
        record.get('properties').level.enum == ['debug', 'info']
    }

    void "@AllowedValues values that do not match the type of the property fail compilation"() {
        when:
        buildAndReadResourceAsString('META-INF/micronaut-configuration-schemas/test.BadProps.json', """
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AllowedValues;

@ConfigurationProperties("bad")
class BadProps {
    @AllowedValues($values)
    private $type value;

    public $type getValue() { return value; }
    public void setValue($type v) { this.value = v; }
}

enum Color { RED, GREEN }
""")

        then:
        RuntimeException e = thrown()
        e.message.contains(message)

        where:
        type      | values                | message
        'int'     | '{"1", "two"}'        | "@AllowedValues value 'two' of property 'value' is not a valid int"
        'int'     | '"2147483648"'        | "@AllowedValues value '2147483648' of property 'value' is not a valid int"
        'byte'    | '{"1", "1000"}'       | "@AllowedValues value '1000' of property 'value' is not a valid byte"
        'Double'  | '"NaN"'               | "@AllowedValues value 'NaN' of property 'value' is not a valid java.lang.Double"
        'boolean' | '"yes"'               | "@AllowedValues value 'yes' of property 'value' is not a valid boolean"
        'Color'   | '"BLUE"'              | "@AllowedValues value 'BLUE' of property 'value' is not a constant of the enum: [RED, GREEN]"
    }
}
