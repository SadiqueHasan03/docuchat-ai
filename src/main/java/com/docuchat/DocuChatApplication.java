package com.docuchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocuChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocuChatApplication.class, args);
    }
}
