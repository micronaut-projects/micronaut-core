/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Internal;

/**
 * Simple implementation of {@link io.micronaut.inject.ast.PackageElement}.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
final class SimplePackageElement implements PackageElement {
    private final String packageName;

    SimplePackageElement(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String getName() {
        return packageName;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Object getNativeType() {
        return packageName;
    }
}
