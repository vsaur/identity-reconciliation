package com.bitespeed.identity_reconciliation.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Data
public class IdentifyRequest {

    private String email;
    private String phoneNumber;

    @AssertTrue(message = "Either email or phoneNumber must be provided")
    public boolean isEmailorPhonePresent(){
        return (email != null && !email.isBlank()) || (phoneNumber != null && !phoneNumber.isBlank());
    }
}
