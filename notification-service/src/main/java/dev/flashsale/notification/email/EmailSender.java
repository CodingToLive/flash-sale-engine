package dev.flashsale.notification.email;

public interface EmailSender {
    void send(String toAddress, NotificationPayload payload);
}
