plugins {
    id("io.micronaut.build.internal.sourcegen-testsuite")
    id("groovy")
}

dependencies {
    compileOnly(mn.micronaut.inject.groovy)
    compileOnly(projects.sourcegenGenerator)
    // compileOnly(projects.sourcegenGeneratorJava)

    implementation(mn.micronaut.inject.groovy)
    implementation(projects.sourcegenGenerator)
    implementation(projects.sourcegenGeneratorJava)
    implementation(projects.sourcegenAnnotations)
    implementation(projects.testSuiteCustomGenerators)
    implementation(projects.testSuiteCustomAnnotations)

    annotationProcessor(mn.micronaut.inject.groovy)
    annotationProcessor(projects.sourcegenGeneratorJava)
    annotationProcessor(projects.sourcegenGenerator)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
}
