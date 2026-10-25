package app.almanook.imagery.provider;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClient for fixed, trusted provider APIs (not for user-supplied URLs; those go through SafeHttpFetcher). */
final class ProviderHttp {

    static final String USER_AGENT = "Almanook/1.0 (https://github.com/anniigr/almanook)";

    private ProviderHttp() {}

    static RestClient client(String baseUrl) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(6));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }
}
