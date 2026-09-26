package com.cellbank.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String senderAddress;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderAddress) {

        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
    }

    public void send(String recipient, String subject, String body) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("Cellbank <" + senderAddress + ">");
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}
