package io.micronaut.docs

trait YamlAsciidocTagCleaner {

    String cleanYamlAsciidocTag(String str) {
        str.replaceAll('//tag::yamlconfig\\[]', '').replaceAll('//end::yamlconfig\\[]', '').trim()
    }

    Map flatten(Map m, String separator = '.') {
        m.collectEntries { k, v ->  v instanceof Map ? flatten(v, separator).collectEntries { q, r ->  [(k + separator + q): r] } : [(k):v] }
    }
}