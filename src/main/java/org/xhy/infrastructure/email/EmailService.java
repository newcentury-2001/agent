package org.xhy.infrastructure.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.Properties;

@Service
public class EmailService {
    @Value("${mail.smtp.host}")
    private String host;

    @Value("${mail.smtp.port}")
    private int port;

    @Value("${mail.smtp.username}")
    private String username;

    @Value("${mail.smtp.password}")
    private String password;

    @Value("${mail.verification.subject}")
    private String verificationSubject;

    @Value("${mail.verification.template}")
    private String verificationTemplate;

    public void sendVerificationCode(String to, String code) {
        // 检查邮件配置是否完整
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new BusinessException("邮件服务未配置，无法发送验证码");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.required", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "false");
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(verificationSubject);
            message.setText(String.format(verificationTemplate, code));

            Transport.send(message);
        } catch (MessagingException e) {
            throw new BusinessException("发送邮件失败: " + e.getMessage(), e);
        }
    }
}