/*
 * Copyright 2018 original authors
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
package io.micronaut.docs

import org.asciidoctor.ast.AbstractBlock
import org.asciidoctor.ast.Inline
import org.asciidoctor.extension.InlineMacroProcessor

/**
 * @author graemerocher
 * @since 1.0
 */
class PackageMacro extends InlineMacroProcessor {
    PackageMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    protected Object process(AbstractBlock parent, String target, Map<String, Object> attributes) {
        String defaultPackage = getDefaultPackagePrefix()
        if(defaultPackage != null && !target.startsWith(defaultPackage)) {
            target = "${defaultPackage}${target}" // allow excluding io.micronaut
        }
        String baseUri = getBaseUri(parent.document.attributes)
        final Map options = [
                type: ':link',
                target: "${baseUri}/${target.replace('.','/')}/package-summary.html".toString()
        ] as Map<String, Object>

        String pkg = target
        if (attributes.text) {
            pkg = attributes.text
        }
        // Prepend twitterHandle with @ as text link.
        final Inline apiLink = createInline(parent, 'anchor', pkg, attributes, options)

        // Convert to String value.
        return apiLink.convert()
    }

    protected String getBaseUri(Map<String, Object> attrs) {
        "../api"
    }

    protected String getDefaultPackagePrefix() {
        "io.micronaut."
    }


}
