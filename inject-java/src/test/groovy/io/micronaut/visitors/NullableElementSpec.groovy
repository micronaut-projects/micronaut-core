/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement

class NullableElementSpec extends AbstractTypeElementSpec {

    void "test jspecify annotations"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class Test {

    @Nullable
    String myNullableField;

    @NonNull
    String myNotNullField = "";

    static @Nullable String emptyToNull(@NonNull String x) {
        return x.isEmpty() ? null : x;
    }

    static @NonNull String nullToEmpty(@Nullable String x) {
        return x == null ? "" : x;
    }
}
''') { ClassElement classElement ->

            def emptyToNull = classElement.findMethod("emptyToNull").get()
            assert emptyToNull.isNullable()
            assert !emptyToNull.isNonNull()

            assert emptyToNull.getReturnType().isNullable()
            assert !emptyToNull.getReturnType().isNonNull()

            assert emptyToNull.getGenericReturnType().isNullable()
            assert !emptyToNull.getGenericReturnType().isNonNull()

            assert emptyToNull.getParameters()[0].isNonNull()
            assert !emptyToNull.getParameters()[0].isNullable()

            MethodElement nullToEmpty = classElement.findMethod("nullToEmpty").get()
            assert nullToEmpty.isNonNull()
            assert !nullToEmpty.isNullable()

            assert nullToEmpty.getReturnType().isNonNull()
            assert !nullToEmpty.getReturnType().isNullable()

            assert nullToEmpty.getGenericReturnType().isNonNull()
            assert !nullToEmpty.getGenericReturnType().isNullable()

            assert nullToEmpty.getParameters()[0].isNullable()
            assert !nullToEmpty.getParameters()[0].isNonNull()

            def myNullableField = classElement.findField("myNullableField").get()
            assert myNullableField.isNullable()
            assert !myNullableField.isNonNull()

            assert myNullableField.getType().isNullable()
            assert myNullableField.getType().isNullable()

            assert myNullableField.getGenericType().isNullable()
            assert myNullableField.getGenericType().isNullable()

            assert myNullableField.getGenericField().isNullable()
            assert myNullableField.getGenericField().isNullable()

            FieldElement myNotNullField = classElement.findField("myNotNullField").get()
            assert myNotNullField.isNonNull()
            assert !myNotNullField.isNullable()

            assert myNotNullField.getType().isNonNull()
            assert !myNotNullField.getType().isNullable()

            assert myNotNullField.getGenericType().isNonNull()
            assert !myNotNullField.getGenericType().isNullable()

            assert myNotNullField.getGenericField().isNonNull()
            assert !myNotNullField.getGenericField().isNullable()

            classElement
        }
    }

    void "test jspecify @NullMarked annotation"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

@NullMarked
class Test {

    @Nullable
    String myNullableField;

    String myNotNullField = "";

    static @Nullable String emptyToNull(String x) {
        return x.isEmpty() ? null : x;
    }

    static String nullToEmpty(@Nullable String x) {
        return x == null ? "" : x;
    }
}
''') { ClassElement classElement ->

            def emptyToNull = classElement.findMethod("emptyToNull").get()
            assert emptyToNull.isNullable()
            assert !emptyToNull.isNonNull()

            assert emptyToNull.getReturnType().isNullable()
            assert !emptyToNull.getReturnType().isNonNull()

            assert emptyToNull.getGenericReturnType().isNullable()
            assert !emptyToNull.getGenericReturnType().isNonNull()

            assert emptyToNull.getParameters()[0].isNonNull()
            assert !emptyToNull.getParameters()[0].isNullable()

            MethodElement nullToEmpty = classElement.findMethod("nullToEmpty").get()
            assert nullToEmpty.isNonNull()
            assert !nullToEmpty.isNullable()

            assert nullToEmpty.getReturnType().isNonNull()
            assert !nullToEmpty.getReturnType().isNullable()

            assert nullToEmpty.getGenericReturnType().isNonNull()
            assert !nullToEmpty.getGenericReturnType().isNullable()

            assert nullToEmpty.getParameters()[0].isNullable()
            assert !nullToEmpty.getParameters()[0].isNonNull()

            def myNullableField = classElement.findField("myNullableField").get()
            assert myNullableField.isNullable()
            assert !myNullableField.isNonNull()

            assert myNullableField.getType().isNullable()
            assert myNullableField.getType().isNullable()

            assert myNullableField.getGenericType().isNullable()
            assert myNullableField.getGenericType().isNullable()

            assert myNullableField.getGenericField().isNullable()
            assert myNullableField.getGenericField().isNullable()

            FieldElement myNotNullField = classElement.findField("myNotNullField").get()
            assert myNotNullField.isNonNull()
            assert !myNotNullField.isNullable()

            assert myNotNullField.getType().isNonNull()
            assert !myNotNullField.getType().isNullable()

            assert myNotNullField.getGenericType().isNonNull()
            assert !myNotNullField.getGenericType().isNullable()

            assert myNotNullField.getGenericField().isNonNull()
            assert !myNotNullField.getGenericField().isNullable()

            classElement
        }
    }

    void "test jspecify @NullMarked annotation on a method"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

class Test {

    @NullMarked
    static String nullToEmpty(@Nullable String x) {
        return x == null ? "" : x;
    }
}
''') { ClassElement classElement ->

            MethodElement nullToEmpty = classElement.findMethod("nullToEmpty").get()
            assert nullToEmpty.isNonNull()
            assert !nullToEmpty.isNullable()

            assert nullToEmpty.getReturnType().isNonNull()
            assert !nullToEmpty.getReturnType().isNullable()

            assert nullToEmpty.getGenericReturnType().isNonNull()
            assert !nullToEmpty.getGenericReturnType().isNullable()

            assert nullToEmpty.getParameters()[0].isNullable()
            assert !nullToEmpty.getParameters()[0].isNonNull()

            classElement
        }
    }

    void "test jspecify @NullMarked annotation on a package"() {
        expect:
        buildClassElement('''
@NullMarked
package test;
import org.jspecify.annotations.NullMarked;

''','''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

class Test {

    static String nullToEmpty(@Nullable String x) {
        return x == null ? "" : x;
    }
}
''') { ClassElement classElement ->

            MethodElement nullToEmpty = classElement.findMethod("nullToEmpty").get()
            assert nullToEmpty.isNonNull()
            assert !nullToEmpty.isNullable()

            assert nullToEmpty.getReturnType().isNonNull()
            assert !nullToEmpty.getReturnType().isNullable()

            assert nullToEmpty.getGenericReturnType().isNonNull()
            assert !nullToEmpty.getGenericReturnType().isNullable()

            assert nullToEmpty.getParameters()[0].isNullable()
            assert !nullToEmpty.getParameters()[0].isNonNull()

            classElement
        }
    }

    void "test jspecify @NullMarked annotation on a property"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.Nullable;

class Test {

    private final @Nullable String name;

    Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
''') { ClassElement classElement ->

            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()
            assert !propertyElement.isNonNull()
            assert propertyElement.isNullable()

            assert !propertyElement.getType().isNonNull()
            assert propertyElement.getType().isNullable()

            assert !propertyElement.getGenericType().isNonNull()
            assert propertyElement.getGenericType().isNullable()

            classElement
        }
    }

    void "test jspecify @NullMarked annotation on a property @NullMarked"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

@NullMarked
class Test {

    private final String name;

    Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
''') { ClassElement classElement ->

            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()
            assert propertyElement.isNonNull()
            assert !propertyElement.isNullable()

            assert propertyElement.getType().isNonNull()
            assert !propertyElement.getType().isNullable()

            assert propertyElement.getGenericType().isNonNull()
            assert !propertyElement.getGenericType().isNullable()

            classElement
        }
    }

    void "test jspecify annotations arrays"() {
        expect:
            buildClassElement('''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class Test {

    @Nullable
    String[] jspecifyArrayField;
    @io.micronaut.core.annotation.Nullable
    String[] micronautArrayField;

    String @Nullable [] jspecifyArrayField2;

    String @io.micronaut.core.annotation.Nullable [] micronautArrayField2;

}
''') { ClassElement classElement ->

            FieldElement jspecifyArrayField = classElement.findField("jspecifyArrayField").get()
            assert !jspecifyArrayField.isNullable()
            assert !jspecifyArrayField.isNonNull()

            assert !jspecifyArrayField.getType().isNullable()
            assert !jspecifyArrayField.getType().isNonNull()

            assert !jspecifyArrayField.getGenericType().isNullable()
            assert !jspecifyArrayField.getGenericType().isNonNull()

            assert !jspecifyArrayField.getGenericField().isNullable()
            assert !jspecifyArrayField.getGenericField().isNonNull()

            ClassElement componentType = jspecifyArrayField.getType().fromArray()

            assert componentType.isNullable()
            assert !componentType.isNonNull()

            ClassElement genericComponentType = jspecifyArrayField.getGenericType().fromArray()

            assert genericComponentType.isNullable()
            assert !genericComponentType.isNonNull()

            FieldElement jspecifyArrayField2 = classElement.findField("jspecifyArrayField2").get()
            assert jspecifyArrayField2.isNullable()
            assert !jspecifyArrayField2.isNonNull()

            assert jspecifyArrayField2.getType().isNullable()
            assert !jspecifyArrayField2.getType().isNonNull()

            assert jspecifyArrayField2.getGenericType().isNullable()
            assert !jspecifyArrayField2.getGenericType().isNonNull()

            assert jspecifyArrayField2.getGenericField().isNullable()
            assert !jspecifyArrayField2.getGenericField().isNonNull()

            ClassElement componentType2 = jspecifyArrayField2.getType().fromArray()

            assert !componentType2.isNullable()
            assert !componentType2.isNonNull()

            ClassElement genericComponentType2 = jspecifyArrayField2.getGenericType().fromArray()

            assert !genericComponentType2.isNullable()
            assert !genericComponentType2.isNonNull()

            // Micronaut respects only this syntax for nullable array fields
            FieldElement micronautArrayField = classElement.findField("micronautArrayField").get()
            assert micronautArrayField.isNullable()
            assert !micronautArrayField.isNonNull()

            assert micronautArrayField.getType().isNullable()
            assert !micronautArrayField.getType().isNonNull()

            assert micronautArrayField.getGenericType().isNullable()
            assert !micronautArrayField.getGenericType().isNonNull()

            assert micronautArrayField.getGenericField().isNullable()
            assert !micronautArrayField.getGenericField().isNonNull()

            // Micronaut doesn't support this syntax
            FieldElement micronautArrayField2 = classElement.findField("micronautArrayField2").get()
            assert !micronautArrayField2.isNullable()
            assert !micronautArrayField2.isNonNull()

            assert !micronautArrayField2.getType().isNullable()
            assert !micronautArrayField2.getType().isNonNull()

            assert !micronautArrayField2.getGenericType().isNullable()
            assert !micronautArrayField2.getGenericType().isNonNull()

            assert !micronautArrayField2.getGenericField().isNullable()
            assert !micronautArrayField2.getGenericField().isNonNull()

            classElement
        }
    }

    void "test generics jspecify annotations"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.ArrayList;

class Methods {
  public static <T> @Nullable T firstOrNull(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
  }

  public static <T> T firstOrNonNullDefault(List<T> list, T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

  public static <T extends @Nullable Object> T firstOrDefault(List<T> list, T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

  public static <T extends @Nullable Object> @Nullable T firstOrNullableDefault(List<T> list, @Nullable T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

}
''') { ClassElement classElement ->

            def firstOrNull = classElement.findMethod("firstOrNull").get()
            assert firstOrNull.isNullable()
            assert !firstOrNull.isNonNull()

            assert firstOrNull.getReturnType().isNullable()
            assert !firstOrNull.getReturnType().isNonNull()

            assert firstOrNull.getGenericReturnType().isNullable()
            assert !firstOrNull.getGenericReturnType().isNonNull()

            MethodElement firstOrNonNullDefault = classElement.findMethod("firstOrNonNullDefault").get()
            assert !firstOrNonNullDefault.isNonNull()
            assert !firstOrNonNullDefault.isNullable()

            assert !firstOrNonNullDefault.getReturnType().isNonNull()
            assert !firstOrNonNullDefault.getReturnType().isNullable()

            assert !firstOrNonNullDefault.getGenericReturnType().isNonNull()
            assert !firstOrNonNullDefault.getGenericReturnType().isNullable()

            def firstOrDefault = classElement.findMethod("firstOrDefault").get()
            assert firstOrDefault.isNullable()
            assert !firstOrDefault.isNonNull()

            assert firstOrDefault.getReturnType().isNullable()
            assert !firstOrDefault.getReturnType().isNonNull()

            assert firstOrDefault.getGenericReturnType().isNullable()
            assert !firstOrDefault.getGenericReturnType().isNonNull()

            def firstOrNullableDefault = classElement.findMethod("firstOrNullableDefault").get()
            assert firstOrNullableDefault.isNullable()
            assert !firstOrNullableDefault.isNonNull()

            assert firstOrNullableDefault.getReturnType().isNullable()
            assert !firstOrNullableDefault.getReturnType().isNonNull()

            assert firstOrNullableDefault.getGenericReturnType().isNullable()
            assert !firstOrNullableDefault.getGenericReturnType().isNonNull()

            classElement
        }
    }

    void "test generics jspecify annotations 2"() {
        expect:
        buildClassElement('''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.ArrayList;

class Methods {

    public static <T> List<@Nullable T> nullOutMatches(List<T> list, T toRemove) {
      List<@Nullable T> copy = new ArrayList<>(list);
      for (int i = 0; i < copy.size(); i++) {
        if (copy.get(i).equals(toRemove)) {
          copy.set(i, null);
        }
      }
      return copy;
    }

}
''') { ClassElement classElement ->

            def nullOutMatches = classElement.findMethod("nullOutMatches").get()

            def listItem = nullOutMatches.getGenericReturnType().getFirstTypeArgument().get()
            assert listItem.isNullable()
            assert !listItem.isNonNull()

            classElement
        }
    }

}
