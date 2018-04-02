package micronaut

import grails.plugins.*
import org.grails.plugins.micronaut.HttpClientBeanProcessor

class MicronautGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.3.2 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Micronaut" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/micronaut"

    Closure doWithSpring() {
        { ->
            httpClientBeanProcessor HttpClientBeanProcessor
        }
    }
}
