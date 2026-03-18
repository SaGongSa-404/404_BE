dependencies {
    implementation(project(":modules:auth"))
    implementation(project(":modules:shared"))
    implementation(project(":modules:house"))
    implementation(project(":modules:chore"))
    implementation(project(":packages:events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}

