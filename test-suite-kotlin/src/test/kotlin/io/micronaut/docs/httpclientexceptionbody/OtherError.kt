package io.micronaut.docs.httpclientexceptionbody

import groovy.transform.CompileStatic

@CompileStatic
class OtherError internal constructor(var status: Int?, var error: String?, var message: String?, var path: String?)
