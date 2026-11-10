package com.devisri.reposcout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "reposcout.cors.allowed-origins=https://reposcout.example.com")
class RepoScoutApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void springAiUsesOurGroqErrorHandler(@Autowired org.springframework.web.client.ResponseErrorHandler handler) {
        assertThat(handler).isInstanceOf(com.devisri.reposcout.agent.GroqErrorHandler.class);
    }

    @Test
    void theFrontendMayCallTheApiFromItsOwnDomain() {
        ResponseEntity<String> response = preflight("https://reposcout.example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://reposcout.example.com");
    }

    @Test
    void otherWebsitesMayNot() {
        assertThat(preflight("https://evil.example.org").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(java.util.List.of("Content-Type"));
        return rest.exchange("/api/ask", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }
}
