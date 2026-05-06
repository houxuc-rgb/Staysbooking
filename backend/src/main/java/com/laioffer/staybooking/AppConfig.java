package com.laioffer.staybooking;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.maps.GeoApiContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class AppConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/**").permitAll()
                                .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public Storage storage(
            @Value("${staybooking.gcs.credentials-file:}") String credentialsFile,
            ResourceLoader resourceLoader) throws IOException {
        Credentials credentials;
        if (StringUtils.hasText(credentialsFile)) {
            Resource resource = resourceLoader.getResource(credentialsFile);
            try (InputStream inputStream = resource.getInputStream()) {
                credentials = ServiceAccountCredentials.fromStream(inputStream);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }
        return StorageOptions.newBuilder().setCredentials(credentials).build().getService();
    }

    @Bean
    public GeoApiContext geoApiContext(@Value("${staybooking.geocoding.key}") String apiKey) {
        return new GeoApiContext.Builder().apiKey(apiKey).build();
    }
}