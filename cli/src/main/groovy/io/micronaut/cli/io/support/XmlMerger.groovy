/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.io.support

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChildren
import groovy.xml.XmlUtil

/**
 * @author James Kleeh
 * @since 1.0
 */
class XmlMerger {

    List<String> appendNodes = ['dependencies', 'plugins', 'pluginRepositories', 'repositories']

    String merge(File from, File to) {
        GPathResult xmlFrom = new XmlSlurper(false, false).parse(from)
        GPathResult xmlTo = new XmlSlurper(false, false).parse(to)
        merge(xmlFrom, xmlTo)
        XmlUtil.serialize(xmlTo)
    }

    void merge(GPathResult from, GPathResult to) {
        from."*".each { childNode ->
            String name = childNode.name()
            NodeChildren query = to."${name}"
            if (query.isEmpty()) {
                to.appendNode(childNode)
            } else {
                if (appendNodes.contains(name)) {
                    List<Map> existingNodes = query."*".collect { toMap(it) }
                    childNode."*".each { GPathResult nestedNode ->
                        Map newNode = toMap(nestedNode)
                        if (!existingNodes.contains(newNode)) {
                            query.appendNode(nestedNode)
                        }
                    }
                } else {
                    merge(childNode, query)
                }
            }
        }
    }

    Map toMap(GPathResult xml) {
        Map data = [:]
        xml."*".each { node ->
            def children = node.childNodes()
            if (children.size() > 0) {
                data.putAll(toMap(node))
            } else {
                data.put(node.name(), node.text())
            }
        }
        [(xml.name()): data]
    }
}
