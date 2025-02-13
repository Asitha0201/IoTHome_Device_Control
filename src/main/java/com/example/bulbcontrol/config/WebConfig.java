package com.example.bulbcontrol.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.example.bulbcontrol.client.SessionClient;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Bean
    public HttpSessionListener httpSessionListener() {
        return new HttpSessionListener() {
            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                SessionClient client = (SessionClient) se.getSession()
                        .getAttribute("scopedTarget.sessionClient");
                if (client != null) {
                    System.out.println("Session destroyed for client: " + client.getClientId());
                    client.stop();
                }
            }
        };
    }
}