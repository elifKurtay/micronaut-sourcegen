plugins {
    id("io.micronaut.build.internal.sourcegen-base")
    id("io.micronaut.build.internal.docs")
    id("io.micronaut.build.internal.quality-reporting")
}

if (System.getenv("SONAR_TOKEN") != null) {
    tasks.named("testCodeCoverageReport") { enabled = false }
}

afterEvaluate {
    configurations.javadocAggregatorBase.configure {
        dependencies.removeIf {
            it.name.startsWith("test-suite")
        }
    }
}
