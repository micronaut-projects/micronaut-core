/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.inheritance;

import javax.inject.Inject;

public abstract class AbstractBJava {
    // inject via field
    @Inject
    protected AJava a;

    private AJava another;

    private AJava packagePrivate;

    // inject via method
    @Inject
    public void setAnother(AJava a) {
        this.another = a;
    }

    // inject via package private method
    @Inject
    void setPackagePrivate(AJava a) {
        this.packagePrivate = a;
    }

    public AJava getA() {
        return a;
    }

    public AJava getAnother() {
        return another;
    }

    AJava getPackagePrivate() {
        return packagePrivate;
    }
}
