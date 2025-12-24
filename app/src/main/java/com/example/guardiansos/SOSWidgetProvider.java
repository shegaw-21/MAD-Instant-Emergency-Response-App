package com.example.guardiansos;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SOSWidgetProvider extends AppWidgetProvider {

    // *** THIS IS THE CORRECTED LINE ***
    // This action MUST match the one declared in the AndroidManifest.xml
    public static final String ACTION_SOS_FROM_WIDGET = "com.example.guardiansos.ACTION_SOS_FROM_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Intent intent = new Intent(context, SOSWidgetProvider.class);
            intent.setAction(ACTION_SOS_FROM_WIDGET);

            // Use FLAG_IMMUTABLE for newer Android versions
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.sos_widget);
            views.setOnClickPendingIntent(R.id.sos_widget_layout, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_SOS_FROM_WIDGET.equals(intent.getAction())) {
            // Start MainActivity and pass a flag to trigger SOS
            Intent mainActivityIntent = new Intent(context, MainActivity.class);
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mainActivityIntent.putExtra("SOS_TRIGGER", true);
            context.startActivity(mainActivityIntent);
        }
    }
}
