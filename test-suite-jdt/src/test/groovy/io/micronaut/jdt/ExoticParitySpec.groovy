package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractCompilerParitySpec

/**
 * Parity checks for the constructs where the two compilers model the source most differently:
 * enum constant bodies, {@code TYPE_USE} only annotations, single element annotation arrays and
 * members that are not visible to injection.
 */
class ExoticParitySpec extends AbstractCompilerParitySpec {

    void "test enum with constant bodies and an abstract method"() {
        expect:
        assertParity('test.Op', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum Op {

    ZULU {
        @Override
        public int apply(int a) {
            return a + 1;
        }
    },
    ALPHA {
        @Override
        public int apply(int a) {
            return a - 1;
        }
    };

    public abstract int apply(int a);
}
''')
    }

    void "test enum with fields and a constructor"() {
        expect:
        assertParity('test.Coded', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum Coded {

    ZULU("z", 26),
    ALPHA("a", 1);

    private final String code;
    private final int position;

    Coded(String code, int position) {
        this.code = code;
        this.position = position;
    }

    public String getCode() {
        return code;
    }

    public int getPosition() {
        return position;
    }
}
''')
    }

    void "test TYPE_USE only annotations"() {
        expect:
        assertParity([
                'test.NotNull' : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface NotNull {
}
''',
                'test.TypeOnly': '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Executable
@Introspected
public class TypeOnly {

    private @NotNull String zulu;

    public @NotNull String getZulu() {
        return zulu;
    }

    public void setZulu(@NotNull String zulu) {
        this.zulu = zulu;
    }

    public List<@NotNull String> alpha(@NotNull List<String> input) {
        return input;
    }
}
'''
        ])
    }

    void "test single element annotation arrays"() {
        expect:
        assertParity([
                'test.Item'  : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface Item {
    String value();
}
''',
                'test.Group' : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Group {
    Item[] items() default {};
    String[] names() default {};
    Class<?>[] types() default {};
}
''',
                'test.Grouped' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
@Group(items = @Item("single"), names = "one", types = String.class)
public class Grouped {
}
'''
        ])
    }

    void "test non injectable members are consistently ignored"() {
        expect:
        assertParity([
                'test.Dep'      : '''
package test;

import jakarta.inject.Singleton;

@Singleton
public class Dep {
}
''',
                'test.Awkward'  : '''
package test;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Awkward {

    @Inject
    private Dep privateField;

    static Dep staticField;

    final Dep finalField = null;

    transient Dep transientField;

    @Inject
    private void privateMethod(Dep dep) {
    }

    @Inject
    static void staticMethod(Dep dep) {
    }

    public Awkward() {
    }

    class NonStaticInner {
    }

    public void withLocalClass() {
        class Local {
        }
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
            }
        };
    }
}
'''
        ])
    }

    void "test interface constants and annotations on constants"() {
        expect:
        assertParity('test.Constants', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public interface Constants {

    String ZULU = "zulu";
    int ALPHA = 1;

    String getMike();
}
''')
    }

    void "test annotation with all primitive member types"() {
        expect:
        assertParity([
                'test.Primitives' : '''
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Primitives {
    boolean bool() default false;
    byte b() default 0;
    char c() default 'a';
    short s() default 0;
    int i() default 0;
    long l() default 0L;
    float f() default 0.0f;
    double d() default 0.0d;
    String str() default "";
}
''',
                'test.UsesPrimitives' : '''
package test;

import jakarta.inject.Singleton;

@Singleton
@Primitives(bool = true, b = 1, c = 'z', s = 2, i = 3, l = 4L, f = 5.5f, d = 6.5d, str = "seven")
public class UsesPrimitives {
}
'''
        ])
    }

    void "test generic record and generic factory combination"() {
        expect:
        assertParity([
                'test.Pair'        : '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record Pair<L, R>(L zulu, R alpha) {
}
''',
                'test.PairFactory' : '''
package test;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.List;

@Factory
public class PairFactory {

    @Singleton
    Pair<String, Integer> zulu() {
        return new Pair<>("z", 1);
    }

    @Singleton
    Pair<List<String>, Pair<String, String>> alpha() {
        return new Pair<>(List.of(), new Pair<>("a", "b"));
    }
}
'''
        ])
    }
}
