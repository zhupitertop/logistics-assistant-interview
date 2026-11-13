package interview.logistics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {
    @Autowired
    private TestRestTemplate client;

    @Test
    void servesHomepageAndLocalAssets() {
        var page = client.getForEntity("/", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("物流检索与异常排查助手", "/app.js", "/style.css");
        assertThat(client.getForEntity("/app.js", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity("/style.css", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
