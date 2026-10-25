package app.almanook.imagery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("almanook.images")
public record ImageryProperties(Wikimedia wikimedia, Unsplash unsplash) {

    public record Wikimedia(
            @DefaultValue("true") boolean enabled,

            @DefaultValue("https://commons.wikimedia.org/w/api.php")
            String apiUrl) {}

    public record Unsplash(
            String accessKey,
            @DefaultValue("https://api.unsplash.com") String apiUrl,
            @DefaultValue("almanook") String appName) {

        public boolean enabled() {
            return accessKey != null && !accessKey.isBlank();
        }
    }
}
