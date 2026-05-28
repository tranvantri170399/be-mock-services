plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.google.protobuf") version "0.9.4"
}

repositories {
    mavenCentral()
}

// Force all Netty modules to the same version
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "io.netty") {
                useVersion("4.1.118.Final")
            }
        }
    }
}

group = "asia.rgp.mock"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    runtimeOnly("com.h2database:h2")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // gRPC
    implementation("io.grpc:grpc-netty-shaded:1.63.0")
    implementation("io.grpc:grpc-protobuf:1.63.0")
    implementation("io.grpc:grpc-stub:1.63.0")
    implementation("com.google.protobuf:protobuf-java:4.28.3")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("wallet-mock.jar")
}

// Protobuf configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

// Add generated proto source to source sets
sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
        }
    }
}
