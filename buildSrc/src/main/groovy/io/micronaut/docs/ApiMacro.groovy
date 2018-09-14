/*
 * Copyright 2017 original authors
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

import org.asciidoctor.extension.*
import org.asciidoctor.ast.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ApiMacro extends InlineMacroProcessor {
    ApiMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    protected Object process(AbstractBlock parent, String target, Map<String, Object> attributes) {
        // is it a method reference
        int methodIndex = target.lastIndexOf('(')
        int propIndex = target.lastIndexOf('#')
        String methodRef = ""
        String propRef = ""
        String shortName
        if(methodIndex > -1 && target.endsWith(")")) {
            String sig = target.substring(methodIndex+1, target.length()-1)
            target = target.substring(0, methodIndex)
            methodIndex = target.lastIndexOf('.')
            if(methodIndex > -1) {
                String sigRef = "-${sig.split(',').join('-')}-"
                String methodName = target.substring(methodIndex + 1, target.length())

                methodRef = "#${methodName}${sigRef}"
                target = target.substring(0, methodIndex)
                int classIndex = target.lastIndexOf('.')
                if(classIndex > -1) {
                    shortName = "${target.substring(classIndex+1, target.length())}.${methodName}(${sig})"
                }
                else {
                    shortName = target
                }
            }
            else {
                return null
            }
        }
        else {
            if(propIndex > -1) {
                propRef = target.substring(propIndex, target.length())
                target = target.substring(0, propIndex)
                shortName = propRef.substring(1)
            }
            else {

                int classIndex = target.lastIndexOf('.')
                if(classIndex > -1) {
                    shortName = target.substring(classIndex+1, target.length())
                }
                else {
                    shortName = target
                }
            }
        }

        String defaultPackage = getDefaultPackagePrefix()
        if(defaultPackage != null && !target.startsWith(defaultPackage)) {
            target = "${defaultPackage}${target}" // allow excluding io.micronaut
        }
        String baseUri = getBaseUri(parent.document.attributes)
        final Map options = [
                type: ':link',
                target: "${baseUri}/${target.replace('.','/')}.html${methodRef}${propRef}".toString()
        ] as Map<String, Object>
        options.target = options.target.replaceAll('\\$', '.')

        if (attributes.text) {
            shortName = attributes.text
        }

        // Prepend twitterHandle with @ as text link.
        final Inline apiLink = createInline(parent, 'anchor', formatShortName(shortName), attributes, options)

        // Convert to String value.
        return apiLink.convert()
    }

    protected String formatShortName(String shortName) {
        return shortName
    }

    protected String getBaseUri(Map<String, Object> attrs) {
        "../api"
    }

    protected String getDefaultPackagePrefix() {
        "io.micronaut."
    }
}
