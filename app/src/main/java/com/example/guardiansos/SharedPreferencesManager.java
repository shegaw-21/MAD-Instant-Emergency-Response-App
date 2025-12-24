package com.example.guardiansos;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SharedPreferencesManager {

    private static final String PREFS_NAME = "GuardianSOS_Prefs";
    private static final String KEY_TRUSTED_CONTACTS = "trusted_contacts";
    private static final String KEY_CUSTOM_SMS = "custom_sms_message";
    private static final String KEY_PRIMARY_CONTACT = "primary_contact_phone";

    private final SharedPreferences sharedPreferences;
    private final Gson gson = new Gson();

    public SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveTrustedContacts(Map<String, String> contacts) {
        String json = gson.toJson(contacts);
        sharedPreferences.edit().putString(KEY_TRUSTED_CONTACTS, json).apply();
    }

    public Map<String, String> getTrustedContacts() {
        String json = sharedPreferences.getString(KEY_TRUSTED_CONTACTS, null);
        Type type = new TypeToken<HashMap<String, String>>() {}.getType();
        Map<String, String> contacts = gson.fromJson(json, type);
        return contacts != null ? contacts : new HashMap<>();
    }

    public void saveCustomSmsMessage(String message) {
        sharedPreferences.edit().putString(KEY_CUSTOM_SMS, message).apply();
    }

    public String getCustomSmsMessage() {
        return sharedPreferences.getString(KEY_CUSTOM_SMS, "Emergency! I need help.");
    }

    public void savePrimaryContactPhone(String phone) {
        sharedPreferences.edit().putString(KEY_PRIMARY_CONTACT, phone).apply();
    }

    public String getPrimaryContactPhone() {
        return sharedPreferences.getString(KEY_PRIMARY_CONTACT, null);
    }
}
