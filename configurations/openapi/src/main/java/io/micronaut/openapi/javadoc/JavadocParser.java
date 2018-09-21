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

package io.micronaut.openapi.javadoc;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import java.util.Set;

/**
 * Very simple javadoc parser that can used to parse out the first paragraph description and parameter / return descriptions.
 * Most other tags are simply stripped and ignored.
 *
 * @author graemerocher
 * @since 1.0
 */
@Experimental
public class JavadocParser {

    private static final Set<String> IGNORED = CollectionUtils.setOf("see", "since", "author", "version", "deprecated", "throws");
    private static final int TEXT = 1;
    private static final int TAG_START = 2;
    private static final int DOCLET_START = 4;
    private static final int PARAM_NAME = 6;
    private static final int PARAM_DESC = 7;
    private static final int RETURN_DESC = 8;
    private static final int IGNORE = 9;

    private int previousState = TEXT;

    /**
     * Parse the javadoc in a {@link JavadocDescription}.
     *
     * @param text The text
     * @return The description
     */
    public JavadocDescription parse(String text) {
        StringBuilder description = new StringBuilder();
        int state = TEXT;
        JavadocDescription javadocDescription = new JavadocDescription();

        if (StringUtils.isNotEmpty(text)) {

            char[] chars = text.toCharArray();
            StringBuilder currentParam = new StringBuilder();
            StringBuilder currentDoclet = new StringBuilder();
            StringBuilder currentDescription = description;

            for (char c : chars) {
                switch (state) {
                    case RETURN_DESC:
                        if (c == '\n') {
                            state = TEXT;
                            previousState = TEXT;
                            javadocDescription.setReturnDescription(currentDescription.toString());
                            currentDescription = description;
                        }
                    case PARAM_DESC:
                        if (state == PARAM_DESC) {
                            javadocDescription.getParameters().put(currentParam.toString(), currentDescription.toString().trim());
                        }

                    case TEXT:
                        if (c == '{' || c == '@') {
                            currentDoclet.delete(0, currentDoclet.length());
                            state = DOCLET_START;
                        } else if (c == '<') {
                            state = TAG_START;
                        } else if (c != '}' && c != '>') {
                            currentDescription.append(c);
                        }
                    continue;
                    case IGNORE:
                        if (c == '\n') {
                            state = previousState;
                            currentDescription = description;
                        }
                    continue;
                    case DOCLET_START:
                        if (c == ' ') {
                            state = previousState;
                            String docletName = currentDoclet.toString();
                            if (IGNORED.contains(docletName)) {
                                state = IGNORE;
                            } else {
                                if (docletName.equals("param")) {
                                    currentParam.delete(0, currentParam.length());
                                    state = PARAM_NAME;
                                }
                                if (docletName.equals("return")) {
                                    currentDescription = new StringBuilder();
                                    state = RETURN_DESC;
                                    previousState = RETURN_DESC;
                                }
                            }
                        } else {
                            currentDoclet.append(c);
                        }
                        continue;
                    case PARAM_NAME:
                        if (c == ' ') {
                            currentDescription = new StringBuilder();
                            state = PARAM_DESC;
                            previousState = PARAM_DESC;
                        } else {
                            currentParam.append(c);
                        }
                    continue;
                    case TAG_START:
                        if (c == '>') {
                            state = TEXT;
                        }
                    default:

                }
            }

        }


        javadocDescription.setMethodDescription(description.toString().trim());
        return javadocDescription;
    }
}
