package backend

import httpdemo.Synthesizer

class BootStrap {

    def init = { servletContext ->
        new Synthesizer(manufacturer: 'Dave Smith Instruments', model: 'Prophet 08', polyphonic: true).save()
        new Synthesizer(manufacturer: 'Dave Smith Instruments', model: 'Rev 2', polyphonic: true).save()
        new Synthesizer(manufacturer: 'Dave Smith Instruments', model: 'Pro 2', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Moog', model: 'Mother 32', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Moog', model: 'Subsequent 37', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Moog', model: 'Subsequent 37 CV', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Arturia', model: 'MiniBrute', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Arturia', model: 'MicroBrute', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Arturia', model: 'MatrixBrute', polyphonic: false).save()
        new Synthesizer(manufacturer: 'Korg', model: 'Minilogue', polyphonic: true).save()
        new Synthesizer(manufacturer: 'Korg', model: 'Monologue', polyphonic: false).save()
    }
    def destroy = {
    }
}
