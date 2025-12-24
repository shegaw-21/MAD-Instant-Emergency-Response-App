package com.example.guardiansos;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final String LOG_TAG = "GuardianSOS_Main";
    private static final String SENT_SMS_ACTION = "com.example.guardiansos.SMS_SENT";
    private static final String DELIVERED_SMS_ACTION = "com.example.guardiansos.SMS_DELIVERED";

    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferencesManager prefsManager;
    private ActivityResultLauncher<Intent> videoCaptureLauncher;

    private MediaRecorder mediaRecorder;
    private String currentAudioPath = "";
    private boolean isRecording = false;
    private Dialog recordDialog;
    private Handler timerHandler;
    private long startTime = 0L;

    private BroadcastReceiver sentSmsReceiver;
    private BroadcastReceiver deliveredSmsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefsManager = new SharedPreferencesManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        registerActivityLaunchers();
        setupClickListeners();
        checkAndRequestPermissions();
        registerSmsReceivers();

        if (getIntent().getBooleanExtra("SOS_TRIGGER", false)) {
            activateSOS();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister receivers to prevent memory leaks
        if (sentSmsReceiver != null) {
            unregisterReceiver(sentSmsReceiver);
        }
        if (deliveredSmsReceiver != null) {
            unregisterReceiver(deliveredSmsReceiver);
        }
    }

    private void registerSmsReceivers() {
        sentSmsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String contactName = intent.getStringExtra("contactName");
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Toast.makeText(context, "SMS Sent to " + contactName, Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        Toast.makeText(context, "Failed to send SMS to " + contactName, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

        deliveredSmsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String contactName = intent.getStringExtra("contactName");
                Toast.makeText(context, "SMS Delivered to " + contactName, Toast.LENGTH_SHORT).show();
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sentSmsReceiver, new IntentFilter(SENT_SMS_ACTION), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(deliveredSmsReceiver, new IntentFilter(DELIVERED_SMS_ACTION), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sentSmsReceiver, new IntentFilter(SENT_SMS_ACTION));
            registerReceiver(deliveredSmsReceiver, new IntentFilter(DELIVERED_SMS_ACTION));
        }
    }

    private void sendMessageToAll(String locationLink) {
        Map<String, String> contacts = prefsManager.getTrustedContacts();

        if (contacts.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No Trusted Contacts")
                    .setMessage("You have not selected any trusted contacts to receive an SMS. Please add contacts first.")
                    .setPositiveButton("Add Contacts", (dialog, which) -> {
                        startActivity(new Intent(this, TrustedContactsActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        String customMessage = prefsManager.getCustomSmsMessage();
        String finalMessage = customMessage;
        if (locationLink != null && !locationLink.isEmpty()) {
            finalMessage += "\nMy current location is: " + locationLink;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> messageParts = smsManager.divideMessage(finalMessage);
            int requestCode = 0;

            for (Map.Entry<String, String> contact : contacts.entrySet()) {
                String phone = contact.getKey();
                String name = contact.getValue();

                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                Intent sentIntent = new Intent(SENT_SMS_ACTION);
                sentIntent.putExtra("contactName", name);
                PendingIntent sentPI = PendingIntent.getBroadcast(this, requestCode++, sentIntent, PendingIntent.FLAG_IMMUTABLE);
                sentIntents.add(sentPI);

                ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
                Intent deliveredIntent = new Intent(DELIVERED_SMS_ACTION);
                deliveredIntent.putExtra("contactName", name);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(this, requestCode++, deliveredIntent, PendingIntent.FLAG_IMMUTABLE);
                deliveredIntents.add(deliveredPI);

                smsManager.sendMultipartTextMessage(phone, null, messageParts, sentIntents, deliveredIntents);
            }

        } catch (Exception e) {
            Toast.makeText(this, "SMS failed to send. Please check permissions.", Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "SMS sending failed", e);
        }
    }

    private void activateSOS() {
        Log.d(LOG_TAG, "SOS Protocol Activated!");

        if (!checkAndRequestPermissions()) {
            Toast.makeText(this, "Please grant permissions to use SOS.", Toast.LENGTH_LONG).show();
            return;
        }

        String primaryContactPhone = prefsManager.getPrimaryContactPhone();
        if (primaryContactPhone != null && !primaryContactPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + primaryContactPhone));
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent);
            }
        } else {
            Toast.makeText(this, "No primary emergency contact is set for the call.", Toast.LENGTH_LONG).show();
        }

        sendSMSToTrustedContacts();
    }

    private void sendSMSToTrustedContacts() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted. Cannot send location.", Toast.LENGTH_SHORT).show();
            sendMessageToAll(null);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            String locationLink = null;
            if (location != null) {
                locationLink = "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
            } else {
                Toast.makeText(MainActivity.this, "Could not get location. Sending SMS without it.", Toast.LENGTH_LONG).show();
            }
            sendMessageToAll(locationLink);
        });
    }

    // ... (The rest of your MainActivity methods remain largely the same) ...
    private void registerActivityLaunchers() {
        videoCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri videoUri = result.getData().getData();
                        if (videoUri != null) {
                            shareMedia(videoUri, "video/*");
                        }
                    }
                });
    }

    private void setupClickListeners() {
        findViewById(R.id.sos_button).setOnClickListener(v -> activateSOS());
        findViewById(R.id.siren_button).setOnClickListener(v -> toggleSiren());
        findViewById(R.id.trusted_contacts_link).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, TrustedContactsActivity.class))
        );
        findViewById(R.id.record_video_button).setOnClickListener(v -> recordVideo());
        findViewById(R.id.record_audio_button).setOnClickListener(v -> showRecordAudioDialog());
        findViewById(R.id.send_message_button).setOnClickListener(v -> sendSMSToTrustedContacts());
    }

    private void showRecordAudioDialog() {
        recordDialog = new Dialog(this);
        recordDialog.setContentView(R.layout.dialog_record_audio);
        recordDialog.setCancelable(false);

        TextView timerTextView = recordDialog.findViewById(R.id.text_view_timer);
        TextView statusTextView = recordDialog.findViewById(R.id.text_view_recording_status);
        ImageButton recordButton = recordDialog.findViewById(R.id.button_record_dialog);

        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording(timerTextView, statusTextView, recordButton);
            }
        });
        recordDialog.show();
    }

    private void startRecording(TextView timerTextView, TextView statusTextView, ImageButton recordButton) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio recording permission not granted.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File audioFile = createAudioFile();
            currentAudioPath = audioFile.getAbsolutePath();
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentAudioPath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            statusTextView.setText("Recording...");
            recordButton.setImageResource(android.R.drawable.ic_media_pause);
            startTime = System.currentTimeMillis();
            timerHandler = new Handler(Looper.getMainLooper());
            timerHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isRecording) {
                        long millis = System.currentTimeMillis() - startTime;
                        String time = String.format(Locale.US, "%02d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(millis),
                                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
                        );
                        timerTextView.setText(time);
                        timerHandler.postDelayed(this, 500);
                    }
                }
            });
        } catch (IOException e) {
            Log.e(LOG_TAG, "startRecording failed", e);
            Toast.makeText(this, "Recording failed to start.", Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "MediaRecorder stop failed: " + e.getMessage());
            }
            mediaRecorder = null;
        }
        isRecording = false;
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        if (recordDialog != null) {
            recordDialog.dismiss();
        }
        if (!currentAudioPath.isEmpty()) {
            File audioFile = new File(currentAudioPath);
            Uri audioUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", audioFile);
            shareMedia(audioUri, "audio/mp4");
            currentAudioPath = "";
        }
    }

    private File createAudioFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String audioFileName = "AUDIO_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        return File.createTempFile(audioFileName, ".mp4", storageDir);
    }

    private void shareMedia(Uri mediaUri, String mimeType) {
        String customMessage = prefsManager.getCustomSmsMessage();
        String finalMessage = customMessage;
        if (mimeType.startsWith("audio")) {
            finalMessage += "\n\nAttached is an emergency audio recording.";
        } else if (mimeType.startsWith("video")) {
            finalMessage += "\n\nAttached is an emergency video recording.";
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, finalMessage);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Emergency Alert from GuardianSOS");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooserIntent = Intent.createChooser(shareIntent, "Share Emergency Recording via...");
        startActivity(chooserIntent);
    }

    private void recordVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            videoCaptureLauncher.launch(takeVideoIntent);
        } else {
            Toast.makeText(this, "No app found to handle video recording.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        };
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void toggleSiren() {
        Intent sirenIntent = new Intent(this, SirenService.class);
        if (SirenService.IS_SIREN_RUNNING) {
            stopService(sirenIntent);
            Toast.makeText(this, "Siren Deactivated", Toast.LENGTH_SHORT).show();
        } else {
            Uri sirenUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.siren_sound);
            sirenIntent.setData(sirenUri);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(sirenIntent);
            } else {
                startService(sirenIntent);
            }
            Toast.makeText(this, "Siren Activated", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d(LOG_TAG, "All requested permissions granted.");
            } else {
                Toast.makeText(this, "Some permissions were denied. App functionality may be limited.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
