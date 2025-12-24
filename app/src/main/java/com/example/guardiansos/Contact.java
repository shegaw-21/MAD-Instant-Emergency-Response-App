package com.example.guardiansos;

public class Contact {
    private String name;
    private String phoneNumber;
    private boolean isSelectedForSms; // For the checkbox (SMS)
    private boolean isPrimaryForCall; // For the switch (Call)

    public Contact(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.isSelectedForSms = false;
        this.isPrimaryForCall = false;
    }

    // Getters and Setters

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

    public boolean isPrimaryForCall() {
        return isPrimaryForCall;
    }

    public void setPrimaryForCall(boolean primaryForCall) {
        isPrimaryForCall = primaryForCall;
    }
}
