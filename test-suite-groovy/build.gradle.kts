plugins {
    id("io.micronaut.build.internal.sourcegen-testsuite")
    id("io.micronaut.minimal.application")
    id("groovy")
}

micronaut {
    processing {
        // test incremental compile
        incremental(true)
    }
    version.set(libs.versions.micronaut.platform)
}

dependencies {
    compileOnly(mn.micronaut.inject.groovy)
    annotationProcessor(projects.sourcegenGeneratorJava)
    annotationProcessor(projects.testSuiteCustomGenerators)
    annotationProcessor(mnData.micronaut.data.processor)
    annotationProcessor(mnValidation.micronaut.validation.processor)

    implementation(mn.micronaut.inject.groovy)
    implementation(projects.sourcegenAnnotations)
    implementation(projects.testSuiteCustomAnnotations)
    implementation(mnValidation.micronaut.validation)
    implementation(mnData.micronaut.data.model)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
    // testImplementation(projects.testSuiteCustomGenerators)
    testImplementation(mn.micronaut.inject)

    testAnnotationProcessor(mn.micronaut.inject.groovy.test)
    testAnnotationProcessor(mn.micronaut.inject.java.test)
}

tasks {
//    compileTestGroovy {
//        groovyOptions.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
//    compileGroovy {
//        groovyOptions.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
}
