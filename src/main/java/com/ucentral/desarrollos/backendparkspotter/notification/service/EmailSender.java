package com.ucentral.desarrollos.backendparkspotter.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío de correos. Si no hay servidor SMTP configurado (spring.mail.host) o app.mail.enabled=false,
 * el correo solo se registra en el log: así el backend funciona en local y en la CI sin credenciales.
 */
@Slf4j
@Component
public class EmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${app.mail.enabled:false}") boolean enabled,
                       @Value("${app.mail.from:ParkSpotter <no-reply@parkspotter.co>}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    /** Devuelve true si el correo se entregó al servidor SMTP. Nunca lanza excepción. */
    public boolean send(String to, String subject, String text) {
        JavaMailSender sender = enabled ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.info("Correo no enviado (SMTP deshabilitado) para {}: {}", to, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            return true;
        } catch (MailException ex) {
            log.warn("No se pudo enviar el correo a {}: {}", to, ex.getMessage());
            return false;
        }
    }
}
