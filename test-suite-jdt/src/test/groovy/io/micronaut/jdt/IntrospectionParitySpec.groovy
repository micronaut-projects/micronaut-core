package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Compares the bean introspections produced by javac and the Eclipse JDT compiler.
 */
class IntrospectionParitySpec extends AbstractCompilerParitySpec {

    void "test java bean introspection property order"() {
        expect:
        assertParity('test.Person', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class Person {

    private String zulu;
    private int yankee;
    private String alpha;
    private boolean mike;

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

    public String getAlpha() {
        return alpha;
    }

    public void setAlpha(String alpha) {
        this.alpha = alpha;
    }

    public boolean isMike() {
        return mike;
    }

    public void setMike(boolean mike) {
        this.mike = mike;
    }
}
''')
    }

    void "test introspection with inherited properties"() {
        expect:
        assertParity([
                'test.Animal' : '''
package test;

public abstract class Animal {

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
                'test.Dog'    : '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class Dog extends Animal {

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

    void "test introspection with field access"() {
        expect:
        assertParity('test.FieldBean', '''
package test;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;

@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD})
public class FieldBean {

    public String zulu;
    public int yankee;
    public String alpha;
}
''')
    }

    void "test introspection with a @Creator static factory"() {
        expect:
        assertParity('test.Created', '''
package test;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class Created {

    private final String zulu;
    private final int alpha;

    private Created(String zulu, int alpha) {
        this.zulu = zulu;
        this.alpha = alpha;
    }

    @Creator
    public static Created of(String zulu, int alpha) {
        return new Created(zulu, alpha);
    }

    public String getZulu() {
        return zulu;
    }

    public int getAlpha() {
        return alpha;
    }
}
''')
    }

    void "test introspection with fluent accessors"() {
        expect:
        assertParity('test.Fluent', '''
package test;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;

@Introspected
@AccessorsStyle(readPrefixes = "", writePrefixes = "")
public class Fluent {

    private String zulu;
    private String alpha;

    public String zulu() {
        return zulu;
    }

    public void zulu(String zulu) {
        this.zulu = zulu;
    }

    public String alpha() {
        return alpha;
    }

    public void alpha(String alpha) {
        this.alpha = alpha;
    }
}
''')
    }

    void "test introspection of an interface with default methods"() {
        expect:
        assertParity('test.HasDefaults', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public interface HasDefaults {

    String getZulu();

    default String getAlpha() {
        return "alpha";
    }

    default int getMike() {
        return 1;
    }
}
''')
    }

    void "test introspection with excludes, includes and indexed annotations"() {
        expect:
        assertParity([
                'test.Indexed' : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Indexed {
    String value() default "";
}
''',
                'test.Filtered': '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(excludes = "secret", indexed = @Introspected.IndexedAnnotation(annotation = Indexed.class, member = "value"))
public class Filtered {

    private String zulu;
    private String secret;
    private String alpha;

    @Indexed("z")
    public String getZulu() {
        return zulu;
    }

    public void setZulu(String zulu) {
        this.zulu = zulu;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Indexed("a")
    public String getAlpha() {
        return alpha;
    }

    public void setAlpha(String alpha) {
        this.alpha = alpha;
    }
}
'''
        ])
    }

    void "test introspection of a class extending a type from the classpath"() {
        expect:
        assertParity('test.Extending', '''
package test;

import io.micronaut.core.annotation.Introspected;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

@Introspected
public class Extending extends AbstractMap<String, String> {

    private String zulu;
    private String alpha;

    @Override
    public Set<Entry<String, String>> entrySet() {
        return Set.of();
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

    void "test introspection of a sealed hierarchy"() {
        expect:
        assertParity('test.Shape', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public sealed interface Shape permits Shape.Circle, Shape.Square {

    @Introspected
    record Circle(double radius) implements Shape {
    }

    @Introspected
    record Square(double side, String label) implements Shape {
    }
}
''')
    }

    void "test introspection of generic bean with type variables"() {
        expect:
        assertParity('test.Holder', '''
package test;

import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Map;

@Introspected
public class Holder<T extends Number, S> {

    private T zulu;
    private List<S> alpha;
    private Map<String, ? extends T> mike;

    public T getZulu() {
        return zulu;
    }

    public void setZulu(T zulu) {
        this.zulu = zulu;
    }

    public List<S> getAlpha() {
        return alpha;
    }

    public void setAlpha(List<S> alpha) {
        this.alpha = alpha;
    }

    public Map<String, ? extends T> getMike() {
        return mike;
    }

    public void setMike(Map<String, ? extends T> mike) {
        this.mike = mike;
    }
}
''')
    }
}
