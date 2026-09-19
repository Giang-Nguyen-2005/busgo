package com.busgo;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class MySqlConnectionIT extends JwtTestSupport {
    @Autowired DataSource dataSource;
    @Autowired TestRestTemplate http;

    @Test
    void realMySqlConnectionAndApplicationStartup() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
        var response = http.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"data\":{\"status\":\"UP\"}}");
    }
}
