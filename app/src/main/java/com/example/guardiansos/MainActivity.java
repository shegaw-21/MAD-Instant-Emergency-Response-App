package com.example.guardiansos;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
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
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
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
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final String LOG_TAG = "GuardianSOS_Main";

    private FusedLocationProviderClient fusedLocationProviderClient;
    private SharedPreferencesManager prefsManager;
    private ActivityResultLauncher<Intent> videoCaptureLauncher;

    // Media & UI
    private MediaRecorder mediaRecorder;
    private String currentAudioPath = "";
    private boolean isRecording = false;
    private Dialog recordDialog;
    private Handler timerHandler;
    private long startTime = 0L;

    // Sequential Calling
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private List<String> callQueue;
    private int currentCallIndex;
    private boolean isMakingSosCall = false;
    private long callStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefsManager = new SharedPreferencesManager(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        callQueue = new ArrayList<>();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        registerActivityLaunchers();
        setupClickListeners();
        checkAndRequestPermissions();
        setupPhoneStateListener();

        if (getIntent().getBooleanExtra("SOS_TRIGGER", false)) {
            activateSOS("General Emergency (from Widget)");
        }
    }

    private void activateSOS(String emergencyType) {
        Log.d(LOG_TAG, "SOS Protocol Activated! Type: " + emergencyType);
        if (!checkAndRequestPermissions()) {
            Toast.makeText(this, "Please grant all required permissions to use SOS.", Toast.LENGTH_LONG).show();
            return;
        }

        buildCallQueue();
        if (callQueue.isEmpty()) {
            Toast.makeText(this, "No trusted contacts set for calling.", Toast.LENGTH_LONG).show();
        } else {
            isMakingSosCall = true;
            currentCallIndex = 0;
            makeNextCall();
        }

        sendSMSToTrustedContacts(emergencyType);
    }

    private void sendSMSToTrustedContacts(String emergencyType) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            startSmsService(emergencyType, null);
            return;
        }

        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, location -> {
            String locationLink = location != null ? "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude() : null;
            startSmsService(emergencyType, locationLink);
        });
    }

    private void startSmsService(String emergencyType, String locationLink) {
        List<Contact> contacts = prefsManager.getContacts();
        ArrayList<String> recipients = new ArrayList<>();
        for (Contact contact : contacts) {
            if (contact.isSelectedForSms()) {
                recipients.add(contact.getPhoneNumber());
            }
        }

        if (recipients.isEmpty()) {
            if (!isMakingSosCall) {
                Toast.makeText(this, "No contacts selected for SMS.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String customMessage = prefsManager.getCustomSmsMessage();
        String finalMessage = customMessage;
        if (emergencyType != null && !emergencyType.isEmpty()) {
            finalMessage += "\n\nEmergency Type: " + emergencyType;
        }
        if (locationLink != null && !locationLink.isEmpty()) {
            finalMessage += "\nMy current location is: " + locationLink;
        }

        Intent serviceIntent = new Intent(this, SmsSenderService.class);
        serviceIntent.putExtra("recipients", recipients);
        serviceIntent.putExtra("message", finalMessage);
        
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void sendImSafeMessage() {
        List<Contact> contacts = prefsManager.getContacts();
        ArrayList<String> recipients = new ArrayList<>();
        for (Contact contact : contacts) {
            if (contact.isSelectedForSms()) {
                recipients.add(contact.getPhoneNumber());
            }
        }

        if (recipients.isEmpty()) {
            Toast.makeText(this, "No SMS contacts to notify.", Toast.LENGTH_SHORT).show();
            return;
        }

        String safeMessage = "The emergency is over. I am safe now.";
        Intent serviceIntent = new Intent(this, SmsSenderService.class);
        serviceIntent.putExtra("recipients", recipients);
        serviceIntent.putExtra("message", safeMessage);
        
        ContextCompat.startForegroundService(this, serviceIntent);
        Toast.makeText(this, "Sending 'I am safe' message...", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private void setupPhoneStateListener() {
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                if (!isMakingSosCall) return;

                // --- THIS IS THE FIX: Only check for IDLE state ---
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                    long callDuration = System.currentTimeMillis() - callStartTime;

                    if (callDuration < 15000) { // Heuristic for unanswered calls
                        currentCallIndex++;
                        if (currentCallIndex < callQueue.size()) {
                            showCallNextContactDialog();
                        } else {
                            Toast.makeText(MainActivity.this, "End of emergency call list.", Toast.LENGTH_LONG).show();
                            isMakingSosCall = false;
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Emergency call completed.", Toast.LENGTH_LONG).show();
                        isMakingSosCall = false;
                    }
                }
            }
        };
    }

    private void showCallNextContactDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Call Unsuccessful")
                .setMessage("The previous contact did not answer. Call the next person?")
                .setPositiveButton("Yes, Call Next", (dialog, which) -> makeNextCall())
                .setNegativeButton("No, Stop", (dialog, which) -> {
                    isMakingSosCall = false;
                    Toast.makeText(MainActivity.this, "Emergency call sequence stopped.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void makeNextCall() {
        if (callQueue.isEmpty() || currentCallIndex >= callQueue.size()) {
            Toast.makeText(this, "No more contacts to call.", Toast.LENGTH_SHORT).show();
            isMakingSosCall = false;
            return;
        }
        String phoneNumber = callQueue.get(currentCallIndex);
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            isMakingSosCall = false;
            return;
        }

        // --- THIS IS THE FIX: Start the timer before making the call ---
        callStartTime = System.currentTimeMillis();
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        startActivity(callIntent);
        Toast.makeText(this, "Calling " + phoneNumber, Toast.LENGTH_LONG).show();
    }

    private void buildCallQueue() {
        callQueue.clear();
        List<Contact> contacts = prefsManager.getContacts();
        for (Contact contact : contacts) {
            if (contact.isIncludedInCallQueue()) {
                callQueue.add(contact.getPhoneNumber());
            }
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.sos_button).setOnClickListener(v -> activateSOS("General Emergency"));
        findViewById(R.id.siren_button).setOnClickListener(v -> toggleSiren());
        findViewById(R.id.trusted_contacts_link).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, TrustedContactsActivity.class)));
        findViewById(R.id.record_video_button).setOnClickListener(v -> recordVideo());
        findViewById(R.id.record_audio_button).setOnClickListener(v -> showRecordAudioDialog());
        findViewById(R.id.send_message_button).setOnClickListener(v -> showEmergencyTypeDialog());
        findViewById(R.id.im_safe_button).setOnClickListener(v -> sendImSafeMessage());
    }

    private void showEmergencyTypeDialog() {
        final CharSequence[] items = {"Traffic", "Health", "Conflict", "Other"};
        new AlertDialog.Builder(this)
                .setTitle("Select Emergency Type")
                .setItems(items, (dialog, item) -> sendSMSToTrustedContacts(items[item].toString()))
                .show();
    }
    
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
            isRecording = false;
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (RuntimeException e) {
                // ignore
            }
            mediaRecorder = null;
        }
        isRecording = false;
        if (timerHandler != null) timerHandler.removeCallbacksAndMessages(null);
        if (recordDialog != null) recordDialog.dismiss();
        if (!currentAudioPath.isEmpty()) {
            File audioFile = new File(currentAudioPath);
            Uri audioUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", audioFile);
            shareMedia(audioUri, "audio/mp4");
            currentAudioPath = "";
        }
    }

    private File createAudioFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return File.createTempFile("AUDIO_" + timeStamp + "_", ".mp4", getExternalFilesDir(Environment.DIRECTORY_MUSIC));
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
        startActivity(Intent.createChooser(shareIntent, "Share Emergency Recording via..."));
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
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.READ_PHONE_STATE};
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
        } else {
            Uri sirenUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.siren_sound);
            sirenIntent.setData(sirenUri);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(sirenIntent);
            } else {
                startService(sirenIntent);
            }
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
            if (!allGranted) {
                Toast.makeText(this, "Some permissions were denied. App functionality may be limited.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
