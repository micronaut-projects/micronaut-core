package io.micronaut.inject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({ElementType.TYPE})
public @interface EnumAnn {

    MyEnum value();

    // provides a custom toString()
    enum MyEnum {
        ONE,
        TWO;


        @Override
        public String toString() {
            return this == ONE ? "1" : "2";
        }
    }
}
