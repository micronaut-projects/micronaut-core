/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.Internal;

/**
 * The default implementation if {@link TypeElementQuery}.
 *
 * @param includesConstructors      Include constructors
 * @param includesMethods           Include methods
 * @param includesFields            Include fields
 * @param includesEnumConstants     Include enum constants
 * @param skipsUnresolvedInterfaces Skips unresolved interfaces
 * @author Denis Stepanov
 * @since 1.9
 */
@Internal
record DefaultTypeElementQuery(boolean includesConstructors,
                               boolean includesMethods,
                               boolean includesFields,
                               boolean includesEnumConstants,
                               boolean skipsUnresolvedInterfaces) implements TypeElementQuery {

    static DefaultTypeElementQuery DEFAULT = new DefaultTypeElementQuery(true, true, true, true, false);

    @Override
    public TypeElementQuery includeMethods() {
        return new DefaultTypeElementQuery(includesConstructors, true, includesFields, includesEnumConstants, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery excludeMethods() {
        return new DefaultTypeElementQuery(includesConstructors, false, includesFields, includesEnumConstants, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery includeConstructors() {
        return new DefaultTypeElementQuery(true, includesMethods, includesFields, includesEnumConstants, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery excludeConstructors() {
        return new DefaultTypeElementQuery(false, includesMethods, includesFields, includesEnumConstants, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery includeFields() {
        return new DefaultTypeElementQuery(includesConstructors, includesMethods, true, includesEnumConstants, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery excludeFields() {
        return new DefaultTypeElementQuery(includesConstructors, includesMethods, false, includesEnumConstants, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery includeEnumConstants() {
        return new DefaultTypeElementQuery(includesConstructors, includesMethods, includesFields, true, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery excludeEnumConstants() {
        return new DefaultTypeElementQuery(includesConstructors, includesMethods, includesFields, false, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery includeAll() {
        return new DefaultTypeElementQuery(true, true, true, true, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery excludeAll() {
        return new DefaultTypeElementQuery(false, false, false, false, skipsUnresolvedInterfaces);
    }

    @Override
    public TypeElementQuery skipUnresolvedInterfaces() {
        return new DefaultTypeElementQuery(includesConstructors, includesMethods, includesFields, includesEnumConstants, true);
    }
}
