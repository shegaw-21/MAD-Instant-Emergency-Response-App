package com.example.guardiansos;

public class Contact {
    private String name;
    private String phoneNumber;
    private boolean isSelectedForSms;
    private boolean isIncludedInCallQueue;

    public Contact(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.isSelectedForSms = false;
        this.isIncludedInCallQueue = false;
    }

    // --- Getters and Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isSelectedForSms() {
        return isSelectedForSms;
    }

    public void setSelectedForSms(boolean selectedForSms) {
        isSelectedForSms = selectedForSms;
    }

    public boolean isIncludedInCallQueue() {
        return isIncludedInCallQueue;
    }

    public void setIncludedInCallQueue(boolean includedInCallQueue) {
        isIncludedInCallQueue = includedInCallQueue;
    }
}
