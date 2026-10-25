package app.almanook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlmanookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlmanookApplication.class, args);
    }
}
