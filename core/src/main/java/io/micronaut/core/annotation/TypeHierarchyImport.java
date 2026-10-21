/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Imports the generic hierarchy of a type into the compilation, as a record the compiler writes, to be found at run time
 * with {@link io.micronaut.core.type.TypeHierarchies#find(Class)} as a {@link io.micronaut.core.type.TypeHierarchy}.
 * Like {@link Introspected#classes()}, it can import the hierarchies of classes that cannot be annotated.
 *
 * <p>Put on a class without {@link #classes()}, it imports the hierarchy of that class. With {@link #classes()}, it
 * imports the hierarchies of those classes instead, which can be classes of a library or of the JDK, such as the
 * class of a value an application passes where its generic supertypes are needed at run time:</p>
 *
 * <pre class="code">
 * &#064;TypeHierarchyImport(classes = {ArrayList.class, HashMap.class})
 * class Hierarchies {
 * }</pre>
 *
 * <p>A record is a small generated class, one per type, loaded only when the type is asked for. It carries the type
 * variables with their bounds, the generic superclass and the generic interfaces as the type declares them, and
 * nothing of its members.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Experimental
public @interface TypeHierarchyImport {

    /**
     * The classes to import the hierarchies of, instead of the annotated class. When none is given, the default, the
     * hierarchy of the annotated class itself is imported, and the annotated class can be the type whose hierarchy a
     * consumer asks for at run time.
     *
     * @return The classes, empty for the annotated class
     */
    Class<?>[] classes() default {};
}
