package com.example.guardiansos;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private EditText editTextCustomSms;
    private Button buttonSaveSms;
    private SharedPreferencesManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefsManager = new SharedPreferencesManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editTextCustomSms = findViewById(R.id.edit_text_custom_sms);
        buttonSaveSms = findViewById(R.id.button_save_sms);

        loadCustomMessage();

        buttonSaveSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCustomMessage();
            }
        });
    }

    private void loadCustomMessage() {
        String currentMessage = prefsManager.getCustomSmsMessage();
        editTextCustomSms.setText(currentMessage);
    }

    private void saveCustomMessage() {
        String newMessage = editTextCustomSms.getText().toString().trim();

        if (newMessage.isEmpty()) {
            newMessage = "Emergency! I need help.";
            // Update the text field to show the user the default message was restored
            editTextCustomSms.setText(newMessage);
            prefsManager.saveCustomSmsMessage(newMessage);
            Toast.makeText(this, "Message field was empty. Default message has been saved.", Toast.LENGTH_LONG).show();
        } else {
            prefsManager.saveCustomSmsMessage(newMessage);
            Toast.makeText(this, "Emergency message saved!", Toast.LENGTH_SHORT).show();
        }
        // The finish() call has been removed, so the user will stay on this screen.
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
