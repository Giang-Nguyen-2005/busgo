package com.busgo.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Optional SMTP configuration. Constructing the sender never connects to the server. */
@Component
public class NotificationMailSender {
    private final boolean enabled;
    private final String from;
    private final JavaMailSenderImpl sender;
    public NotificationMailSender(@Value("${busgo.mail.enabled:false}") boolean enabled,
            @Value("${busgo.mail.host:}") String host, @Value("${busgo.mail.port:587}") int port,
            @Value("${busgo.mail.username:}") String username, @Value("${busgo.mail.password:}") String password,
            @Value("${busgo.mail.from:}") String from, @Value("${busgo.mail.starttls:true}") boolean tls) {
        this.enabled=enabled && !host.isBlank() && NotificationService.validEmail(from);
        this.from=from;
        sender=new JavaMailSenderImpl(); sender.setHost(host); sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        if(!username.isBlank()) { sender.setUsername(username); sender.setPassword(password); }
        var p=sender.getJavaMailProperties();
        p.setProperty("mail.smtp.auth",Boolean.toString(!username.isBlank()));
        p.setProperty("mail.smtp.starttls.enable",Boolean.toString(tls));
        p.setProperty("mail.smtp.starttls.required",Boolean.toString(tls));
        p.setProperty("mail.smtp.connectiontimeout","5000");
        p.setProperty("mail.smtp.timeout","5000");
        p.setProperty("mail.smtp.writetimeout","5000");
    }
    public boolean configured() { return enabled; }
    public void send(String destination,String title,String body) {
        if(!configured()) throw new IllegalStateException("Email disabled");
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(destination); message.setSubject(title); message.setText(body);
        sender.send(message);
    }
}
