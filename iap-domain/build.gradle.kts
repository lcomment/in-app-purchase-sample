dependencies {
    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.junit)
}

tasks.bootJar {
    enabled = false
}

tasks.getByName("jar") {
    enabled = true
}
