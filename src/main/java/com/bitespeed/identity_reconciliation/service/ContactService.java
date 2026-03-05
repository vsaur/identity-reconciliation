package com.bitespeed.identity_reconciliation.service;

import com.bitespeed.identity_reconciliation.dto.ContactResponse;
import com.bitespeed.identity_reconciliation.dto.IdentifyRequest;
import com.bitespeed.identity_reconciliation.dto.IdentifyResponse;
import com.bitespeed.identity_reconciliation.entity.Contact;
import com.bitespeed.identity_reconciliation.repository.ContactRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional
    public IdentifyResponse identify(IdentifyRequest request) {
        String email = request.getEmail();
        String phoneNumber = request.getPhoneNumber();

        List<Contact> contactByEmail = new ArrayList<>();
        List<Contact> contactByPhoneNumber = new ArrayList<>();

        if (email != null) {
            contactByEmail = contactRepository.findByEmail(email);
        }
        if (phoneNumber != null) {
            contactByPhoneNumber = contactRepository.findByPhoneNumber(phoneNumber);
        }

        List<Contact> matchedContacts = new ArrayList<>();
        matchedContacts.addAll(contactByEmail);
        matchedContacts.addAll(contactByPhoneNumber);

        if (matchedContacts.isEmpty()) {
            Contact newPrimary = new Contact();
            newPrimary.setEmail(email);
            newPrimary.setPhoneNumber(phoneNumber);
            newPrimary.setLinkedId(null);
            newPrimary.setLinkPrecedence("primary");

            Contact savedPrimary = contactRepository.save(newPrimary);

            IdentifyResponse response = new IdentifyResponse();
            ContactResponse contactResponse = new ContactResponse();

            contactResponse.setPrimaryContactId(savedPrimary.getId());

            List<String> emails = new ArrayList<>();
            if (email != null) emails.add(email);
            contactResponse.setEmails(emails);

            List<String> phones = new ArrayList<>();
            if (phoneNumber != null) phones.add(phoneNumber);
            contactResponse.setPhoneNumbers(phones);

            contactResponse.setSecondaryContactIds(new ArrayList<>());

            response.setContact(contactResponse);
            return response;
        }

        List<Contact> primaryCandidates = new ArrayList<>();

        for (Contact c : matchedContacts) {
            if ("primary".equalsIgnoreCase(c.getLinkPrecedence())) {
                if (!primaryCandidates.contains(c)) {
                    primaryCandidates.add(c);
                }
            } else {
                if (c.getLinkedId() != null) {
                    Contact primary = contactRepository.findById(c.getLinkedId()).orElse(null);
                    if (primary != null && !primaryCandidates.contains(primary)) {
                        primaryCandidates.add(primary);
                    }
                }
            }
        }

        Contact primaryContact = primaryCandidates.get(0);
        for (Contact p : primaryCandidates) {
            if (p.getCreatedAt() != null && primaryContact.getCreatedAt() != null
                    && p.getCreatedAt().isBefore(primaryContact.getCreatedAt())) {
                primaryContact = p;
            }
        }

        Long primaryId = primaryContact.getId();

        for (Contact p : primaryCandidates) {
            if (p.getId().equals(primaryId)) continue;

            if ("primary".equalsIgnoreCase(p.getLinkPrecedence())) {
                Long oldPrimaryId = p.getId();

                p.setLinkPrecedence("secondary");
                p.setLinkedId(primaryId);
                contactRepository.save(p);

                List<Contact> oldSecondaries = contactRepository.findByLinkedId(oldPrimaryId);
                for (Contact sec : oldSecondaries) {
                    sec.setLinkedId(primaryId);
                    contactRepository.save(sec);
                }
            }
        }

        List<Contact> linkedSecondaries = contactRepository.findByLinkedId(primaryId);

        List<Contact> fullGroup = new ArrayList<>();
        fullGroup.add(primaryContact);
        fullGroup.addAll(linkedSecondaries);

        LinkedHashSet<String> emailSet = new LinkedHashSet<>();
        LinkedHashSet<String> phoneSet = new LinkedHashSet<>();
        List<Long> secondaryIds = new ArrayList<>();

        if (primaryContact.getEmail() != null) emailSet.add(primaryContact.getEmail());
        if (primaryContact.getPhoneNumber() != null) phoneSet.add(primaryContact.getPhoneNumber());

        for (Contact c : fullGroup) {
            if (!c.getId().equals(primaryId)) {
                secondaryIds.add(c.getId());
            }
            if (c.getEmail() != null) emailSet.add(c.getEmail());
            if (c.getPhoneNumber() != null) phoneSet.add(c.getPhoneNumber());
        }

        boolean isNewEmail = (email != null) && !emailSet.contains(email);
        boolean isNewPhone = (phoneNumber != null) && !phoneSet.contains(phoneNumber);

        if (isNewEmail || isNewPhone) {
            Contact secondary = new Contact();
            secondary.setLinkedId(primaryId);
            secondary.setLinkPrecedence("secondary");

            secondary.setEmail(email);
            secondary.setPhoneNumber(phoneNumber);

            Contact savedSecondary = contactRepository.save(secondary);

            if (email != null) emailSet.add(email);
            if (phoneNumber != null) phoneSet.add(phoneNumber);
            secondaryIds.add(savedSecondary.getId());
        }


        ContactResponse contactResponse = new ContactResponse();
        contactResponse.setPrimaryContactId(primaryId);
        contactResponse.setEmails(new ArrayList<>(emailSet));
        contactResponse.setPhoneNumbers(new ArrayList<>(phoneSet));
        contactResponse.setSecondaryContactIds(secondaryIds);

        IdentifyResponse response = new IdentifyResponse();
        response.setContact(contactResponse);
        return response;
    }
}