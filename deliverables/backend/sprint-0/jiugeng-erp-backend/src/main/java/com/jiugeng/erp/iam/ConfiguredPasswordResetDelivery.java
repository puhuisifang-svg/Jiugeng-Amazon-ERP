package com.jiugeng.erp.iam;

import com.jiugeng.erp.iam.IamTypes.RecoveryChannel;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class ConfiguredPasswordResetDelivery implements PasswordResetDelivery {
    private final JavaMailSender mailSender;
    private final RestClient restClient;
    private final String smtpHost;
    private final String emailFrom;
    private final URI smsWebhook;
    private final String smsWebhookToken;

    ConfiguredPasswordResetDelivery(ObjectProvider<JavaMailSender> mailSender,
            RestClient.Builder restClientBuilder,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${app.iam.password-reset.email-from:}") String emailFrom,
            @Value("${app.iam.password-reset.sms-webhook-url:}") String smsWebhookUrl,
            @Value("${app.iam.password-reset.sms-webhook-token:}") String smsWebhookToken) {
        this.mailSender = mailSender.getIfAvailable();
        this.restClient = restClientBuilder.build();
        this.smtpHost = smtpHost.trim();
        this.emailFrom = emailFrom.trim();
        this.smsWebhook = parseHttpsUri(smsWebhookUrl);
        this.smsWebhookToken = smsWebhookToken;
    }

    @Override
    public boolean supports(RecoveryChannel channel) {
        return switch (channel) {
            case EMAIL -> mailSender != null && !smtpHost.isBlank() && !emailFrom.isBlank();
            case SMS -> smsWebhook != null && !smsWebhookToken.isBlank();
        };
    }

    @Override
    public void send(RecoveryChannel channel, String destination, String code, Duration validFor) {
        if (!supports(channel)) throw new IllegalStateException("Password reset delivery provider is not configured");
        if (channel == RecoveryChannel.EMAIL) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(destination);
            message.setSubject("九更 ERP 密码重置验证码");
            message.setText("验证码：" + code + "。" + validFor.toMinutes() + " 分钟内有效，请勿转发。");
            mailSender.send(message);
            return;
        }
        restClient.post().uri(smsWebhook).header("Authorization", "Bearer " + smsWebhookToken)
                .body(Map.of("phone", destination, "code", code, "expiresMinutes", validFor.toMinutes()))
                .retrieve().toBodilessEntity();
    }

    private static URI parseHttpsUri(String value) {
        if (value == null || value.isBlank()) return null;
        URI uri = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("SMS webhook URL must use HTTPS and include a host");
        }
        return uri;
    }
}
