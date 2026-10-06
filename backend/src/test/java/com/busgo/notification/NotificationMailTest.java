package com.busgo.notification;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationMailTest {
    @Test void emailDisabledOrIncompleteConfigurationStartsWithoutSmtp() {
        assertThat(new NotificationMailSender(false,"smtp.example.test",587,"","","busgo@example.test",true).configured()).isFalse();
        assertThat(new NotificationMailSender(true,"",587,"","","busgo@example.test",true).configured()).isFalse();
        assertThat(new NotificationMailSender(true,"smtp.example.test",587,"","","",true).configured()).isFalse();
        assertThat(new NotificationMailSender(true,"smtp.example.test",587,"","","busgo@example.test",true).configured()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"", "invalid", "name@example", "x@example.test\r\nBcc: y@example.test"})
    void rejectsInvalidEmailAndHeaderInjection(String email) { assertThat(NotificationService.validEmail(email)).isFalse(); }
    @Test void categoriesSupportIndependentEmailPreferences() {
        var p=new NotificationDtos.Preferences(false,true,false);
        assertThat(p.allows(NotificationType.PAYMENT_FAILED)).isFalse();
        assertThat(p.allows(NotificationType.BOOKING_MODIFIED)).isTrue();
        assertThat(p.allows(NotificationType.TRIP_REMINDER_2H)).isFalse();
    }
}
