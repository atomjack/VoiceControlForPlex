package com.atomjack.vcfp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.widget.RemoteViews;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.services.PlexSearchService;

import java.math.BigInteger;
import java.security.SecureRandom;

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

			Intent serviceIntent = new Intent(context, PlexSearchService.class);
			SecureRandom random = new SecureRandom();
			serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
			PendingIntent resultsPendingIntent = PendingIntent.getService(context, 0, serviceIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

			Intent listenerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
			listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, context.getResources().getString(R.string.voice_prompt));

			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, listenerIntent, 0);
			remoteViews.setOnClickPendingIntent(R.id.listenButton, pendingIntent);
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
			Logger.d("Widget updated.");
		}
	}

}
