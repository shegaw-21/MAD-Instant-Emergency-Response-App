package com.example.guardiansos;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrustedContactsActivity extends AppCompatActivity implements ContactAdapter.OnItemClickListener, ContactAdapter.OnPrimaryContactChangeListener {

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    private static final String LOG_TAG = "TrustedContacts";

    private List<Contact> contactList = new ArrayList<>();
    private ContactAdapter contactAdapter;
    private SharedPreferencesManager prefsManager;
    private RecyclerView recyclerView;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trusted_contacts);

        prefsManager = new SharedPreferencesManager(this);

        recyclerView = findViewById(R.id.contacts_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactAdapter = new ContactAdapter(contactList);
        recyclerView.setAdapter(contactAdapter);
        contactAdapter.setOnItemClickListener(this);
        contactAdapter.setOnPrimaryContactChangeListener(this);

        registerContactPickerLauncher();

        findViewById(R.id.button_add_manually).setOnClickListener(v -> showAddManualContactDialog());
        findViewById(R.id.fab_add).setOnClickListener(v -> pickSingleContact());
        findViewById(R.id.button_sync_contacts).setOnClickListener(v -> checkPermissionAndSyncContacts());
        findViewById(R.id.button_save).setOnClickListener(v -> saveAndExit());

        // On start, only load the contacts that were previously saved.
        loadSavedContacts();
    }

    private void registerContactPickerLauncher() {
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri contactUri = result.getData().getData();
                        addContactFromUri(contactUri);
                    }
                });
    }

    private void pickSingleContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void showAddManualContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Contact Manually");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        layout.addView(nameInput);
        final EditText phoneInput = new EditText(this);
        phoneInput.setHint("Phone Number");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(phoneInput);
        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();
            if (!name.isEmpty() && !phone.isEmpty()) {
                addContactToList(new Contact(name, phone), true);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void checkPermissionAndSyncContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
        } else {
            syncAllContacts();
        }
    }

    private void syncAllContacts() {
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                addContactToList(new Contact(name, phoneNumber), false);
            }
            cursor.close();
        }
        Toast.makeText(this, "All contacts have been synced!", Toast.LENGTH_SHORT).show();
    }

    private void messageSelectedContacts() {
        StringBuilder recipients = new StringBuilder();
        int selectedCount = 0;
        for (Contact contact : contactList) {
            if (contact.isSelectedForSms()) {
                recipients.append(contact.getPhoneNumber()).append(";");
                selectedCount++;
            }
        }
        if (selectedCount > 0) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", recipients.toString(), null));
            startActivity(intent);
        } else {
            Toast.makeText(this, "No contacts selected for messaging.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndExit() {
        Map<String, String> trustedSmsContacts = new HashMap<>();
        String primaryCallContact = null;
        for (Contact contact : contactList) {
            if (contact.isSelectedForSms()) {
                trustedSmsContacts.put(contact.getPhoneNumber(), contact.getName());
            }
            if (contact.isPrimaryForCall()) {
                primaryCallContact = contact.getPhoneNumber();
            }
        }
        prefsManager.saveTrustedContacts(trustedSmsContacts);
        prefsManager.savePrimaryContactPhone(primaryCallContact);
        Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void loadSavedContacts() {
        contactList.clear();
        Map<String, String> smsContacts = prefsManager.getTrustedContacts();
        String primaryCallPhone = prefsManager.getPrimaryContactPhone();
        Map<String, Contact> uniqueContacts = new HashMap<>();

        for (Map.Entry<String, String> entry : smsContacts.entrySet()) {
            Contact c = new Contact(entry.getValue(), entry.getKey());
            c.setSelectedForSms(true);
            uniqueContacts.put(c.getPhoneNumber(), c);
        }

        if (primaryCallPhone != null && !uniqueContacts.containsKey(primaryCallPhone)) {
            String name = getContactNameByNumber(primaryCallPhone);
            Contact primary = new Contact(name, primaryCallPhone);
            uniqueContacts.put(primary.getPhoneNumber(), primary);
        }

        if (primaryCallPhone != null && uniqueContacts.containsKey(primaryCallPhone)) {
            uniqueContacts.get(primaryCallPhone).setPrimaryForCall(true);
        }

        contactList.addAll(uniqueContacts.values());
        contactAdapter.notifyDataSetChanged();
    }

    private String getContactNameByNumber(String phoneNumber) {
        try {
            ContentResolver cr = getContentResolver();
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor == null) return phoneNumber;
            String contactName = phoneNumber;
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }
            if (!cursor.isClosed()) cursor.close();
            return contactName;
        } catch (Exception e) {
            return phoneNumber;
        }
    }

    private void addContactFromUri(Uri contactUri) {
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(contactUri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();
            addContactToList(new Contact(name, phoneNumber), true);
        }
    }

    private void addContactToList(Contact newContact, boolean selectByDefault) {
        for (int i = 0; i < contactList.size(); i++) {
            Contact existingContact = contactList.get(i);
            if (PhoneNumberUtils.compare(existingContact.getPhoneNumber(), newContact.getPhoneNumber())) {
                if (selectByDefault && !existingContact.isSelectedForSms()) {
                    existingContact.setSelectedForSms(true);
                    contactAdapter.notifyItemChanged(i);
                }
                recyclerView.smoothScrollToPosition(i);
                return;
            }
        }
        if (selectByDefault) {
            newContact.setSelectedForSms(true);
        }
        contactList.add(0, newContact);
        contactAdapter.notifyItemInserted(0);
        recyclerView.scrollToPosition(0);
    }

    @Override
    public void onPrimaryContactChanged(int position) {
        for (int i = 0; i < contactList.size(); i++) {
            contactList.get(i).setPrimaryForCall(i == position);
        }
        contactAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(int position) {
        View itemView = recyclerView.getLayoutManager().findViewByPosition(position);
        if (itemView != null) {
            showPopupMenu(itemView, position, contactList.get(position));
        }
    }

    private void showPopupMenu(View view, final int position, final Contact contact) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.contact_context_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_call) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.getPhoneNumber(), null)));
                return true;
            } else if (itemId == R.id.action_message) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", contact.getPhoneNumber(), null)));
                return true;
            } else if (itemId == R.id.action_edit) {
                showEditContactDialog(position, contact);
                return true;
            } else if (itemId == R.id.action_delete) {
                contactList.remove(position);
                contactAdapter.notifyItemRemoved(position);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditContactDialog(final int position, final Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Contact");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        final EditText nameInput = new EditText(this);
        nameInput.setText(contact.getName());
        layout.addView(nameInput);
        final EditText phoneInput = new EditText(this);
        phoneInput.setText(contact.getPhoneNumber());
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(phoneInput);
        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            String newPhone = phoneInput.getText().toString().trim();
            if (!newName.isEmpty() && !newPhone.isEmpty()) {
                contact.setName(newName);
                contact.setPhoneNumber(newPhone);
                contactAdapter.notifyItemChanged(position);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                syncAllContacts();
            } else {
                Toast.makeText(this, "Read contacts permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
