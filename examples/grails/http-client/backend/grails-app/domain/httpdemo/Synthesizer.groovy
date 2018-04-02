package httpdemo

import grails.rest.Resource

@Resource(uri='/synths')
class Synthesizer {
    String manufacturer
    String model
    Boolean polyphonic
}

