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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.codehaus.groovy.ast.PackageNode;

/**
 * A class element returning data from a {@link PackageNode}.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@Internal
public class GroovyPackageElement extends AbstractGroovyElement {
    private final PackageNode packageNode;

    /**
     * Default constructor.
     *
     * @param visitorContext The visitor context
     * @param packageNode      The annotated node
     * @param annotationMetadata The annotation metadata
     */
    public GroovyPackageElement(GroovyVisitorContext visitorContext, PackageNode packageNode, AnnotationMetadata annotationMetadata) {
        super(visitorContext, packageNode, annotationMetadata);
        this.packageNode = packageNode;
    }

    @NonNull
    @Override
    public String getName() {
        return packageNode.getName();
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
    public Object getNativeType() {
        return packageNode;
    }
}
