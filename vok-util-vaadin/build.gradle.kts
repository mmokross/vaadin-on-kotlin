dependencies {
    api(project(":vok-framework"))

    api("com.github.mvysny.vokdataloader:vok-dataloader:${properties["vok_dataloader_version"]}")

    // Vaadin
    api("com.github.mvysny.karibudsl:karibu-dsl-v23:${properties["karibudsl_version"]}")
    api("com.vaadin:vaadin-core:${properties["vaadin_version"]}")
    api("javax.servlet:javax.servlet-api:4.0.1")

    // testing
    testImplementation("com.github.mvysny.dynatest:dynatest:${properties["dynatest_version"]}")
    testImplementation("com.github.mvysny.kaributesting:karibu-testing-v23:${properties["kaributesting_version"]}")
    testImplementation("org.slf4j:slf4j-simple:${properties["slf4j_version"]}")
}

kotlin {
    explicitApi()
}

val configureBintray = ext["configureBintray"] as (artifactId: String, description: String) -> Unit
configureBintray("vok-util-vaadin", "VOK: Basic utility classes for Vaadin 10")
