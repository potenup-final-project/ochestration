package com.pg.ochestration

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:contextdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "server.port=0",
        "gateway.toss-test.base-url=http://localhost",
        "gateway.toss-test.secret-key=test-secret",
        "gateway.toss-test.connect-timeout-ms=1000",
        "gateway.toss-test.read-timeout-ms=1000",
        "auth.api-key.pepper=test-pepper-that-is-32-characters-minimum",
        "auth.api-key.enabled=false"
    ]
)
class OchestrationApplicationTests {

    @Test
    fun contextLoads() {
    }
}
