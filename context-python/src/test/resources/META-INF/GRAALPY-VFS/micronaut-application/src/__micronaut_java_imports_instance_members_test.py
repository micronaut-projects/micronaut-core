# A Java imports manifest as the Python compiler writes it, for the instance member names of
# GraalPyContextFactoryTest: the types are registered when a context first reads the manifests.
PACKAGES = {}
TYPES = {}
MEMBERS = {}
INSTANCE_MEMBERS = {
    "io.micronaut.context.python.GraalPyContextFactoryTest$InstanceMembers": {
        "failure": ["getFailure", "property"],
        "get_http_status": ["getHTTPStatus", "method"],
        "get_url": [],
        "http_status": ["getHTTPStatus", "property"],
        "literal_name": ["getLiteralName", "property"],
        "literal_value": ["literalValue", "method"],
        "name": ["getName", "property"],
        "port": ["getPort", "property"],
        "ready": ["isReady", "property"],
        "url": [],
        "with_value": ["withValue", "method"]
    },
    "io.micronaut.context.python.GraalPyContextFactoryTest$Missing": {
        "value": ["getValue", "property"]
    },
    "io.micronaut.http.HttpRequest": {
        "method_name": ["getMethodName", "property"],
        "path": ["getPath", "property"]
    },
    "io.micronaut.http.MutableHttpRequest": {
        "basic_auth": ["basicAuth", "method"],
        "path": []
    }
}
