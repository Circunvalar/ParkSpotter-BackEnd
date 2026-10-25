package com.ucentral.desarrollos.backendparkspotter.payment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;

public record SandboxResultRequest(@NotNull PaymentStatus status) {

    @AssertTrue(message = "status debe ser APPROVED, DECLINED o ERROR")
    public boolean isSupportedStatus() {
        return status == null || status == PaymentStatus.APPROVED || status == PaymentStatus.DECLINED || status == PaymentStatus.ERROR;
    }
}
