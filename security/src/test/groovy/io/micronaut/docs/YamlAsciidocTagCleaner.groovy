package io.micronaut.docs

trait YamlAsciidocTagCleaner {

    String cleanYamlAsciidocTag(String str) {
        str.replaceAll('//tag::yamlconfig\\[]', '').replaceAll('//end::yamlconfig\\[]', '').trim()
    }
}