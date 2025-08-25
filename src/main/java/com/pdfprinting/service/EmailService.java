package com.pdfprinting.service;

import com.pdfprinting.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendVerificationEmail(User user) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Sending verification email to {} (attempt {}/{})", user.getEmail(), attempt, MAX_RETRIES);
                
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                
                helper.setFrom(fromEmail, "PDF Printing System");
                helper.setTo(user.getEmail());
                helper.setSubject("Welcome to PDF Printing System - Please Verify Your Email");
                
                // Create template context
                Context context = new Context();
                context.setVariable("user", user);
                context.setVariable("verificationUrl", baseUrl + "/verify-email?token=" + user.getVerificationToken());
                context.setVariable("baseUrl", baseUrl);
                
                // Process HTML template
                String htmlContent = templateEngine.process("email/verification", context);
                helper.setText(htmlContent, true);
                
                mailSender.send(message);
                logger.info("Verification email sent successfully to {}", user.getEmail());
                return;
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Failed to send verification email to {} (attempt {}/{}): {}", 
                          user.getEmail(), attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Email sending interrupted for {}", user.getEmail());
                        return;
                    }
                }
            }
        }
        
        logger.error("Failed to send verification email to {} after {} attempts: {}", 
                    user.getEmail(), MAX_RETRIES, 
                    lastException != null ? lastException.getMessage() : "Unknown error");
    }

    public void sendBatchProcessedNotification(String batchName, int fileCount, List<String> studentEmails) {
        try {
            logger.info("Sending batch processed notification for {} with {} files", batchName, fileCount);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "PDF Printing System");
            helper.setTo(fromEmail); // Send to admin
            helper.setSubject("Batch Processed: " + batchName + " (" + fileCount + " files)");
            
            // Create template context
            Context context = new Context();
            context.setVariable("batchName", batchName);
            context.setVariable("fileCount", fileCount);
            context.setVariable("studentEmails", studentEmails);
            context.setVariable("processedAt", java.time.LocalDateTime.now());
            
            // Process HTML template
            String htmlContent = templateEngine.process("email/batch-processed", context);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            logger.info("Batch processed notification sent successfully for {}", batchName);
            
        } catch (Exception e) {
            logger.error("Failed to send batch processed notification for {}: {}", batchName, e.getMessage());
        }
    }

    public void sendWelcomeEmail(User user) {
        try {
            logger.info("Sending welcome email to {}", user.getEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "PDF Printing System");
            helper.setTo(user.getEmail());
            helper.setSubject("Welcome to PDF Printing System - Account Activated!");
            
            // Create template context
            Context context = new Context();
            context.setVariable("user", user);
            context.setVariable("loginUrl", baseUrl + "/login");
            context.setVariable("baseUrl", baseUrl);
            
            // Process HTML template
            String htmlContent = templateEngine.process("email/welcome", context);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            logger.info("Welcome email sent successfully to {}", user.getEmail());
            
        } catch (Exception e) {
            logger.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        try {
            logger.info("Sending password reset email to {}", user.getEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "PDF Printing System");
            helper.setTo(user.getEmail());
            helper.setSubject("PDF Printing System - Password Reset Request");
            
            // Create template context
            Context context = new Context();
            context.setVariable("user", user);
            context.setVariable("resetUrl", baseUrl + "/reset-password?token=" + resetToken);
            context.setVariable("baseUrl", baseUrl);
            
            // Process HTML template
            String htmlContent = templateEngine.process("email/password-reset", context);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            logger.info("Password reset email sent successfully to {}", user.getEmail());
            
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public boolean testEmailConfiguration() {
        try {
            logger.info("Testing email configuration...");
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(fromEmail); // Send test email to self
            message.setSubject("PDF Printing System - Email Configuration Test");
            message.setText("This is a test email to verify that the email configuration is working correctly.\n\n" +
                          "If you receive this email, the email system is properly configured.\n\n" +
                          "Timestamp: " + java.time.LocalDateTime.now());
            
            mailSender.send(message);
            logger.info("Test email sent successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("Email configuration test failed: {}", e.getMessage());
            return false;
        }
    }

    public void sendSystemNotification(String subject, String content) {
        try {
            logger.info("Sending system notification: {}", subject);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(fromEmail); // Send to admin
            message.setSubject("PDF Printing System - " + subject);
            message.setText(content + "\n\nTimestamp: " + java.time.LocalDateTime.now());
            
            mailSender.send(message);
            logger.info("System notification sent successfully");
            
        } catch (Exception e) {
            logger.error("Failed to send system notification: {}", e.getMessage());
        }
    }
}
