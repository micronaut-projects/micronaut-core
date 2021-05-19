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
package io.micronaut.context.env.yaml;

import io.micronaut.core.annotation.Internal;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.util.List;
import java.util.Map;

/**
 * Yaml constructor to create containers with sensible
 * default array bounds.
 *
 * @author James Kleeh
 * @since 2.5.5
 */
@Internal
class CustomSafeConstructor extends SafeConstructor {

    @Override
    protected Map<Object, Object> newMap(MappingNode node) {
        return createDefaultMap(node.getValue().size());
    }

    @Override
    protected List<Object> newList(SequenceNode node) {
        return createDefaultList(node.getValue().size());
    }
}
