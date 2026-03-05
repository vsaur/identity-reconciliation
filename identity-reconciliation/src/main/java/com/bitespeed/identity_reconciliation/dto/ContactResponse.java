package com.bitespeed.identity_reconciliation.dto;

import lombok.Data;

import java.util.List;

@Data
public class ContactResponse {

    private Long primaryContactId;

    private List<String> emails;

    private List<String> phoneNumbers;

    private List<Long> secondaryContactIds;
}
