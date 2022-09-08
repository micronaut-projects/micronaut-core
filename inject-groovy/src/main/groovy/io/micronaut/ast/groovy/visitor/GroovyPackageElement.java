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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.PackageElement;
import org.codehaus.groovy.ast.PackageNode;

/**
 * A class element returning data from a {@link PackageNode}.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@Internal
public class GroovyPackageElement extends AbstractGroovyElement implements PackageElement {
    private final PackageNode packageNode;

    /**
     * Default constructor.
     *
     * @param visitorContext            The visitor context
     * @param packageNode               The annotated node
     * @param annotationMetadataFactory The annotation metadata
     */
    public GroovyPackageElement(GroovyVisitorContext visitorContext,
                                PackageNode packageNode,
                                ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, packageNode, annotationMetadataFactory);
        this.packageNode = packageNode;
    }

    @Override
    protected AbstractGroovyElement copyThis() {
        return new GroovyPackageElement(visitorContext, packageNode, elementAnnotationMetadataFactory);
    }

    @NonNull
    @Override
    public String getName() {
        final String n = packageNode.getName();
        if (n.endsWith(".")) {
            return n.substring(0, n.length() - 1);
        }
        return n;
    }

    @Override
    public String getSimpleName() {
        String name = getName();
        int index = name.lastIndexOf(".");
        if (index > -1) {
            return name.substring(index + 1);
        }
        return name;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @NonNull
    @Override
    public PackageNode getNativeType() {
        return packageNode;
    }

}
