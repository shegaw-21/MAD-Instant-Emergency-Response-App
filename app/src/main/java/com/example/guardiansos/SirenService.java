package com.example.guardiansos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class SirenService extends Service {

    private static final String LOG_TAG = "SirenService";
    public static final String CHANNEL_ID = "SirenServiceChannel";

    private MediaPlayer mediaPlayer;
    public static boolean IS_SIREN_RUNNING = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("GuardianSOS Siren Activated")
                    .setContentText("Emergency siren is running.")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();

            startForeground(1, notification);

            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            mediaPlayer = MediaPlayer.create(this, R.raw.siren_sound);

            if (mediaPlayer != null) {
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(LOG_TAG, "MediaPlayer Error! What: " + what + " Extra: " + extra);
                    stopSelf();
                    return true;
                });

                // The app's minSdk is 24, which is higher than Lollipop (21).
                // Therefore, the old setAudioStreamType() method is not needed.
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .build()
                );

                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                IS_SIREN_RUNNING = true;
                Log.d(LOG_TAG, "Siren started successfully.");
            } else {
                Log.e(LOG_TAG, "Error: MediaPlayer could not be created. Check if 'siren_sound' exists in res/raw.");
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "An unexpected error occurred in SirenService.", e);
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        IS_SIREN_RUNNING = false;
        Log.d(LOG_TAG, "Siren service destroyed.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Siren Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
