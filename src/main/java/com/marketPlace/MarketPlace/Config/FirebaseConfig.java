package com.marketPlace.MarketPlace.Config;
 
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
 
import java.io.IOException;
 
@Slf4j
@Configuration
public class FirebaseConfig {
 
    @Value("${firebase.credentials.path}")
    private Resource firebaseCredentialsResource;
 
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase app already initialized — returning existing instance");
            return FirebaseApp.getInstance();
        }
 
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(firebaseCredentialsResource.getInputStream());
 
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
 
        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("Firebase app initialized successfully");
        return app;
    }
 
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
 
