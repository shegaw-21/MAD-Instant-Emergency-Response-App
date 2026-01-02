package com.example.guardiansos;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SharedPreferencesManager {

    private static final String PREFS_NAME = "GuardianSOS_Prefs";
    private static final String KEY_CONTACTS_LIST = "contacts_list";
    private static final String KEY_CUSTOM_SMS = "custom_sms_message";

    private final SharedPreferences sharedPreferences;
    private final Gson gson = new Gson();

    public SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Saves the ordered list of all contacts (including their selected states).
     */
    public void saveContacts(List<Contact> contacts) {
        String json = gson.toJson(contacts);
        sharedPreferences.edit().putString(KEY_CONTACTS_LIST, json).apply();
    }

    /**
     * Retrieves the ordered list of contacts.
     */
    public List<Contact> getContacts() {
        String json = sharedPreferences.getString(KEY_CONTACTS_LIST, null);
        if (json == null) {
            return new ArrayList<>(); // Return an empty list if nothing is saved
        }
        Type type = new TypeToken<ArrayList<Contact>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveCustomSmsMessage(String message) {
        sharedPreferences.edit().putString(KEY_CUSTOM_SMS, message).apply();
    }

    public String getCustomSmsMessage() {
        return sharedPreferences.getString(KEY_CUSTOM_SMS, "Emergency! I need help.");
    }
}
