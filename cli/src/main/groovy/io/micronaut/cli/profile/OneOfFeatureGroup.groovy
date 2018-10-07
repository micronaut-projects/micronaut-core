package io.micronaut.cli.profile

class OneOfFeatureGroup {

    String name
    List<Map> oneOfFeaturesData
    List<OneOfFeature> oneOfFeatures

    private boolean initialized = false

    void initialize(List<Feature> features) {
        if (!initialized) {
            synchronized (this) {
                oneOfFeatures = []

                oneOfFeaturesData.each { Map map ->
                    Feature f = features.find { it.name == map.name }
                    if (f) {
                        oneOfFeatures.add(new OneOfFeature(feature: f, priority: (Integer) map.priority))
                    }
                }
                oneOfFeatures.sort { a, b -> a.priority <=> b.priority}
            }

            initialized = true
        }
    }
}
