package com.laioffer.staybooking;


import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.maps.GeoApiContext;
import com.laioffer.staybooking.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;


import java.io.IOException;
import java.io.InputStream;


@Configuration
public class AppConfig {


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(HttpMethod.POST, "/auth/**").permitAll()
                                .requestMatchers("/bookings/**").hasAuthority("ROLE_GUEST")
                                .requestMatchers("/listings/search").hasAuthority("ROLE_GUEST")
                                .requestMatchers("/listings/**").hasAuthority("ROLE_HOST")
                                .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }


    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public Storage storage(
            @Value("${staybooking.gcs.credentials-file:}") String credentialsFile,
            ResourceLoader resourceLoader
    ) throws IOException {
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
