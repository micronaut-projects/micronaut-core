package ui

import httpdemo.SynthClient
import org.springframework.beans.factory.annotation.Autowired

class SynthesizerController {

    @Autowired
    SynthClient synthClient

    def index() {
        respond synthClient.synthesizers
    }
}
