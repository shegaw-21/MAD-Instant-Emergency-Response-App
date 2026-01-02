package com.example.guardiansos;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

public class SmsSenderService extends Service {

    private static final String TAG = "SmsSenderService";
    private static final String CHANNEL_ID = "SmsSenderServiceChannel";
    private static final String SENT_SMS_ACTION = "com.example.guardiansos.SMS_SENT_SERVICE";
    private static final String DELIVERED_SMS_ACTION = "com.example.guardiansos.SMS_DELIVERED_SERVICE";

    private BroadcastReceiver sentSmsReceiver;
    private BroadcastReceiver deliveredSmsReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String message = intent.getStringExtra("message");
        ArrayList<String> recipients = intent.getStringArrayListExtra("recipients");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuardianSOS")
                .setContentText("Sending emergency messages...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);

        sendSmsToRecipients(recipients, message);

        return START_NOT_STICKY;
    }

    private void sendSmsToRecipients(ArrayList<String> recipients, String message) {
        if (recipients == null || recipients.isEmpty() || message == null) {
            stopSelf();
            return;
        }

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> messageParts = smsManager.divideMessage(message);
        int requestCodeCounter = 0;

        for (String phone : recipients) {
            try {
                Intent sentIntent = new Intent(SENT_SMS_ACTION);
                sentIntent.putExtra("recipient", phone);
                PendingIntent sentPI = PendingIntent.getBroadcast(this, requestCodeCounter++, sentIntent, PendingIntent.FLAG_IMMUTABLE);

                Intent deliveredIntent = new Intent(DELIVERED_SMS_ACTION);
                deliveredIntent.putExtra("recipient", phone);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(this, requestCodeCounter++, deliveredIntent, PendingIntent.FLAG_IMMUTABLE);

                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                sentIntents.add(sentPI);

                ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
                deliveredIntents.add(deliveredPI);

                smsManager.sendMultipartTextMessage(phone, null, messageParts, sentIntents, deliveredIntents);
                Log.d(TAG, "SMS prepared for: " + phone);

            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS to " + phone, e);
            }
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, 5000);
    }

    private void registerReceivers() {
        sentSmsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String recipient = intent.getStringExtra("recipient");
                if (getResultCode() == Activity.RESULT_OK) {
                    Toast.makeText(context, "SMS Sent to " + recipient, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to send SMS to " + recipient, Toast.LENGTH_SHORT).show();
                }
            }
        };

        deliveredSmsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String recipient = intent.getStringExtra("recipient");
                Toast.makeText(context, "SMS Delivered to " + recipient, Toast.LENGTH_SHORT).show();
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(sentSmsReceiver, new IntentFilter(SENT_SMS_ACTION), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(deliveredSmsReceiver, new IntentFilter(DELIVERED_SMS_ACTION), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sentSmsReceiver, new IntentFilter(SENT_SMS_ACTION));
            registerReceiver(deliveredSmsReceiver, new IntentFilter(DELIVERED_SMS_ACTION));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sentSmsReceiver != null) unregisterReceiver(sentSmsReceiver);
        if (deliveredSmsReceiver != null) unregisterReceiver(deliveredSmsReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency SMS Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
