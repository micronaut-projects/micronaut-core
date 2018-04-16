/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.cli.console.parsing

import io.micronaut.cli.util.NameUtils
import groovy.transform.CompileStatic

/**
 * @author Andres Almiray
 * @author Dierk Koenig
 * @author Graeme Rocher
 */
@CompileStatic
class ScriptNameResolver {
    /**
     * Matches a camelCase scriptName to a potential scriptFileName in canonical form.<p>
     * The following scriptNames match FooBar: FB, FoB, FBa
     */
    static boolean resolvesTo(String scriptName, String scriptFileName) {
        def scriptFileNameTokens = NameUtils.getNameFromScript( scriptFileName ).findAll(/[A-Z][a-z]+/)
        def scriptNameTokens = NameUtils.getNameFromScript(scriptName).findAll(/[A-Z][a-z]*/)

        if (scriptFileNameTokens.size() != scriptNameTokens.size()) return false
        for (int i = 0; i < scriptNameTokens.size(); i++) {
            String str = scriptNameTokens[i]
            if (!scriptFileNameTokens[i].startsWith(str)) return false
        }
        true
    }
}
