package com.jiugeng.erp.iam;

import com.jiugeng.erp.iam.IamTypes.RecoveryChannel;
import java.time.Duration;

interface PasswordResetDelivery {
    boolean supports(RecoveryChannel channel);

    void send(RecoveryChannel channel, String destination, String code, Duration validFor);
}
