dependencies {
    implementation(project(":domain"))
    implementation(libs.spring.boot.starter.web)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.1")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.junit)
}

tasks.bootJar {
    enabled = true
}

tasks.getByName("jar") {
    enabled = false
}
