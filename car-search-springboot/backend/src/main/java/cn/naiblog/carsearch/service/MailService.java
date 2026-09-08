package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final SettingService settings;

    public MailService(SettingService settings) { this.settings = settings; }

    public boolean available() { return !settings.value("smtp_host", "").isBlank() && !settings.value("smtp_from_address", "").isBlank(); }

    public List<String> recipients() {
        return Arrays.stream(settings.value("email_recipients", "").split("[,;\\r\\n]+"))
                .map(String::trim).filter(s -> EMAIL.matcher(s).matches()).distinct().toList();
    }

    public void notifyAll(String subject, String html) {
        if (!available()) return;
        for (String recipient : recipients()) try { send(recipient, subject, html); }
        catch (Exception error) { log.warn("email notify failed recipient={} subject={} message={}", recipient, subject, error.getMessage()); }
    }

    public void testConnection() throws Exception { sender().testConnection(); }

    public void send(String to, String subject, String html) throws Exception {
        JavaMailSenderImpl sender = sender();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        String from = settings.value("smtp_from_address", "").trim();
        String name = settings.value("smtp_from_name", "车辆查询系统").trim();
        helper.setFrom(from, name.isBlank() ? "车辆查询系统" : name);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        sender.send(message);
    }

    public String testTemplate() {
        return layout("SMTP 测试邮件", "<p>邮件服务配置正确，测试邮件已成功发送。</p><p>发送时间：" + LocalDateTime.now() + "</p>");
    }

    public String orderTemplate(String title, Map<String, Object> order, String detail) {
        return layout(title, "<p>订单号：" + h(order.get("order_no")) + "</p><p>服务：" + h(order.get("service_name"))
                + "</p><p>金额：¥" + h(order.get("amount")) + "</p><p>状态说明：" + h(detail) + "</p>");
    }

    public String layout(String title, String body) {
        String site = h(settings.value("smtp_from_name", "车辆查询系统"));
        return "<!doctype html><html><body style=\"font-family:sans-serif;background:#f5f5f5;padding:20px\"><div style=\"max-width:600px;margin:auto;background:#fff;padding:28px\"><h2>"
                + h(title) + "</h2><small>" + site + "</small>" + body + "<hr><small>本邮件由系统自动发送，请勿直接回复</small></div></body></html>";
    }

    private JavaMailSenderImpl sender() {
        String host = settings.value("smtp_host", "").trim();
        String from = settings.value("smtp_from_address", "").trim();
        if (host.isBlank() || from.isBlank()) throw new IllegalStateException("邮件服务未配置完整");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(parsePort(settings.value("smtp_port", "465")));
        sender.setUsername(settings.value("smtp_username", ""));
        sender.setPassword(settings.secure("smtp_password", ""));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", String.valueOf(!sender.getUsername().isBlank()));
        properties.setProperty("mail.smtp.connectiontimeout", "15000");
        properties.setProperty("mail.smtp.timeout", "15000");
        properties.setProperty("mail.smtp.writetimeout", "15000");
        String encryption = settings.value("smtp_encryption", "ssl").toLowerCase(Locale.ROOT);
        properties.setProperty("mail.smtp.ssl.enable", String.valueOf("ssl".equals(encryption)));
        properties.setProperty("mail.smtp.starttls.enable", String.valueOf("tls".equals(encryption)));
        properties.setProperty("mail.smtp.starttls.required", String.valueOf("tls".equals(encryption)));
        return sender;
    }

    private int parsePort(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return 465; } }
    private String h(Object value) { return String.valueOf(value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
