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
package org.particleframework.docs

import org.asciidoctor.extension.*
import org.asciidoctor.ast.*

import groovy.transform.CompileStatic
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

        // Define options for an 'anchor' element.
        String shortName
        int i = target.lastIndexOf('.')
        if(i > -1) {
            shortName = target.substring(i+1, target.length())
        }
        else {
            shortName = target
        }
        final Map options = [
                type: ':link',
                target: "../api/${target.replace('.','/')}.html".toString()
        ] as Map<String, Object>

        // Prepend twitterHandle with @ as text link.
        final Inline apiLink = createInline(parent, 'anchor', shortName, attributes, options)

        // Convert to String value.
        return apiLink.convert()
    }
}
