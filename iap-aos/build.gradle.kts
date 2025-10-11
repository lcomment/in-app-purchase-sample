dependencies {
    implementation(project(":domain"))
    implementation(libs.spring.boot.starter.web)
    implementation("com.google.apis:google-api-services-androidpublisher:v3-rev20250724-2.0.0")
    implementation("com.google.api-client:google-api-client:2.7.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.29.0")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.junit)
}

tasks.bootJar {
    enabled = true
}

tasks.getByName("jar") {
    enabled = false
}
