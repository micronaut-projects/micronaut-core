package io.micronaut.kotlin.processing.visitor;

class PackagePrivateSuperclass {

    String packagePrivateProperty;

    String getPackagePrivateProperty() {
        return packagePrivateProperty;
    }

    private String privateProperty;

    private String getPrivateProperty() {
        return privateProperty;
    }

    public String publicProperty;

    public String getPublicProperty() {
        return publicProperty;
    }
}
