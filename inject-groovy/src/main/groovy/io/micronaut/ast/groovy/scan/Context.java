/*
 * Copyright 2017-2011 original authors
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
package io.micronaut.ast.groovy.scan;

import io.micronaut.core.annotation.Internal;

/**
 * Information about a class being parsed in a {@link groovyjarjarasm.asm.ClassReader}.
 *
 * @author Eric Bruneton
 */
@Internal
class Context {

    /**
     * Prototypes of the attributes that must be parsed for this class.
     */
    Attribute[] attrs;

    /**
     * The {@link groovyjarjarasm.asm.ClassReader} option flags for the parsing of this class.
     */
    int flags;

    /**
     * The buffer used to read strings.
     */
    char[] buffer;

    /**
     * The start index of each bootstrap method.
     */
    int[] bootstrapMethods;
}
