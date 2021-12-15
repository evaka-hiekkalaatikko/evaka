// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

plugins {
    `java-platform`
}

object Version {
    const val bouncyCastle = "1.69"
    const val cxf = "3.4.5"
    const val flyingSaucer = "9.1.22"
    const val fuel = "2.3.1"
    const val mockito = "4.1.0"
}

repositories {
    mavenCentral()
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("com.auth0:java-jwt:3.18.2")
        api("com.github.kagkarlsson:db-scheduler:10.4")
        api("com.github.kittinunf.fuel:fuel:${Version.fuel}")
        api("com.github.kittinunf.fuel:fuel-jackson:${Version.fuel}")
        api("com.google.guava:guava:30.1.1-jre")
        api("com.zaxxer:HikariCP:5.0.0")
        api("dev.akkinoc.spring.boot:logback-access-spring-boot-starter:3.1.0")
        api("io.github.microutils:kotlin-logging-jvm:2.1.0")
        api("io.javalin:javalin:4.1.1")
        api("javax.annotation:javax.annotation-api:1.3.2")
        api("javax.jws:javax.jws-api:1.1")
        api("javax.xml.ws:jaxws-api:2.3.1")
        api("net.bytebuddy:byte-buddy:1.12.1")
        api("net.logstash.logback:logstash-logback-encoder:7.0.1")
        api("org.apache.cxf:cxf-tools-wsdlto-databinding-jaxb:${Version.cxf}") // not included in cxf-bom
        api("org.apache.commons:commons-pool2:2.11.1")
        api("org.apache.commons:commons-text:1.9")
        api("org.apache.santuario:xmlsec:2.2.3")
        api("org.apache.tika:tika-core:2.1.0")
        api("org.apache.wss4j:wss4j-ws-security-dom:2.3.0")
        api("org.bouncycastle:bcpkix-jdk15on:${Version.bouncyCastle}")
        api("org.bouncycastle:bcprov-jdk15on:${Version.bouncyCastle}")
        api("org.flywaydb:flyway-core:8.0.5")
        api("org.glassfish.jaxb:jaxb-runtime:2.3.2")
        api("org.jetbrains:annotations:23.0.0")
        api("org.mockito:mockito-core:${Version.mockito}")
        api("org.mockito:mockito-junit-jupiter:${Version.mockito}")
        api("org.mockito.kotlin:mockito-kotlin:4.0.0")
        api("org.postgresql:postgresql:42.3.1")
        api("org.skyscreamer:jsonassert:1.5.0")
        api("org.thymeleaf.extras:thymeleaf-extras-java8time:3.0.4.RELEASE")
        api("org.thymeleaf:thymeleaf:3.0.12.RELEASE")
        api("org.xhtmlrenderer:flying-saucer-core:${Version.flyingSaucer}")
        api("org.xhtmlrenderer:flying-saucer-pdf-openpdf:${Version.flyingSaucer}")
        api("redis.clients:jedis:3.7.0")
        api("org.apache.logging.log4j:log4j-api:2.16.0")
        api("org.apache.logging.log4j:log4j-to-slf4j:2.16.0")
    }

    api(platform("com.fasterxml.jackson:jackson-bom:2.13.0"))
    api(platform("org.apache.cxf:cxf-bom:${Version.cxf}"))
    api(platform("org.jdbi:jdbi3-bom:3.24.1"))
    api(platform("org.jetbrains.kotlin:kotlin-bom:1.6.0"))
    api(platform("org.junit:junit-bom:5.8.1"))
    api(platform("org.springframework.boot:spring-boot-dependencies:2.6.0"))
    api(platform("org.testcontainers:testcontainers-bom:1.16.2"))
    api(platform("software.amazon.awssdk:bom:2.17.86"))
    api(platform("io.netty:netty-bom:4.1.72.Final"))
}
