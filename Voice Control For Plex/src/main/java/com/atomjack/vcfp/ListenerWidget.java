package com.atomjack.vcfp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.RemoteViews;

public class ListenerWidget extends AppWidgetProvider {

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,int[] appWidgetIds) {
		// Get all ids
		ComponentName thisWidget = new ComponentName(context,
						ListenerWidget.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		for (int widgetId : allWidgetIds) {
			RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
							R.layout.widget_layout);

			Intent activityIntent = new Intent(context, PlayMediaActivity.class);
			PendingIntent resultsPendingIntent = PendingIntent.getActivity(context, 0, activityIntent, 0);

			Intent listenerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
			listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voice Control for Plex is listening");

			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, listenerIntent, 0);
			remoteViews.setOnClickPendingIntent(R.id.listenButton, pendingIntent);
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
			Logger.d("Widget updated.");
		}
	}
}
