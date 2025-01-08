plugins {
    id("io.micronaut.build.internal.sourcegen-testsuite")
    alias(mn.plugins.kotlin.jvm)
    alias(mn.plugins.kotlin.allopen)
    alias(mn.plugins.ksp)
}

dependencies {
    ksp(mn.micronaut.inject.kotlin)
    ksp(projects.sourcegenGeneratorKotlin)
    ksp(projects.testSuiteCustomGenerators)
    ksp(mnValidation.micronaut.validation.processor)

    implementation(mnValidation.micronaut.validation)
    implementation(mn.kotlin.stdlib)
    implementation(mn.micronaut.inject.kotlin)
    implementation(projects.sourcegenAnnotations)
    implementation(projects.testSuiteCustomAnnotations)

    testImplementation(mnTest.micronaut.test.junit5)

    testRuntimeOnly(mnTest.junit.jupiter.engine)
}
