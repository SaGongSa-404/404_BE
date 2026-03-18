plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":modules:chore"))
    implementation(project(":modules:adjustment"))
    implementation(project(":modules:notification"))
    implementation(project(":modules:review"))
    implementation(project(":modules:shared"))

    implementation(project(":packages:db"))
    implementation(project(":packages:events"))
    implementation(project(":packages:push-client"))
    implementation(project(":packages:cache"))
    implementation(project(":packages:logger"))
    implementation(project(":packages:observability"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":packages:testing"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from(rootProject.file("db/migrations")) {
        into("db/migration")
    }
}
