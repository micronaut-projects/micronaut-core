package io.micronaut.cli.profile.commands.templates

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class SimpleTemplate {
    String template
    
    public String render(Map<String, String> variables) {
        String result = template?:''
        variables.each { k, v ->
            result = result.replace("@${k}@".toString(), v?:'')
        }
        result
    }
}
