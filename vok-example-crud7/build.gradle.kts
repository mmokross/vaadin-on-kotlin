plugins {
    war
}

dependencies {
    compile("com.github.mvysny.karibudsl:karibu-dsl-v8compat7:${properties["karibudsl_version"]}")

    compile(project(":vok-framework-jpa-compat7"))
    compile("org.jetbrains.kotlin:kotlin-stdlib")
    testCompile("com.github.mvysny.dynatest:dynatest-engine:${properties["dynatest_version"]}")

    // logging
    compile("org.slf4j:slf4j-api:${properties["slf4j_version"]}")
    compile("org.slf4j:slf4j-simple:${properties["slf4j_version"]}")

    // Vaadin
    compile("com.vaadin:vaadin-compatibility-client-compiled:${properties["vaadin8_version"]}")
    compile("com.vaadin:vaadin-compatibility-server:${properties["vaadin8_version"]}")
    compile("com.vaadin:vaadin-push:${properties["vaadin8_version"]}")
    compile("com.vaadin:vaadin-themes:${properties["vaadin8_version"]}")
    providedCompile("javax.servlet:javax.servlet-api:3.1.0")

    // db
    compile("org.flywaydb:flyway-core:${properties["flyway_version"]}")
    compile("org.hibernate:hibernate-hikaricp:5.2.11.Final") {
        exclude(mapOf("group" to "javax.enterprise"))
    }
    compile("com.zaxxer:HikariCP:${properties["hikaricp_version"]}")
    compile("com.h2database:h2:${properties["h2_version"]}")

    // REST
    compile(project(":vok-rest"))
}
