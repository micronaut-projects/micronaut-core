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
