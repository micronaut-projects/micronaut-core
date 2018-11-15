package io.micronaut.docs

trait ValueAtAttributes {
    /**
     * Given a map such as  ['text':'version="1.0.1", groupId="io.micronaut"']
     * for name = 'version' it returns '1.0.1'
     */
    String valueAtAttributes(String name, Map<String, Object> attributes) {
        if (attributes.containsKey('text')) {
            String text = attributes['text']
            if (text.contains("${name}=\"")) {
                String partial = text.substring(text.indexOf("${name}=\"") + "${name}=\"".length())
                if ( partial.contains('"')) {
                    return partial.substring(0, partial.indexOf('"'))
                }
                return partial
            }
        }
        null
    }
}