plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.google.protobuf") version "0.9.4"
}

group = "asia.rgp.mock"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // gRPC
    implementation("io.grpc:grpc-netty-shaded:1.59.0")
    implementation("io.grpc:grpc-protobuf:1.59.0")
    implementation("io.grpc:grpc-stub:1.59.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    
    // ZMQ
    implementation("org.zeromq:jeromq:0.5.3")
    
    // Luigi common-data (for PuElementMessageFrame ZMQ parsing)
    implementation(files("libs/common-data-1.0.0.jar"))
    implementation("net.minidev:json-smart:2.5.0")
    
    // MessagePack
    implementation("org.msgpack:msgpack-core:0.9.6")
    implementation("org.msgpack:msgpack:0.6.12")
    
    // JWT (for game token validation)
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("wsproxy-mock.jar")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.59.0"
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
