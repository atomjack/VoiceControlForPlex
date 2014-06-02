package com.atomjack.vcfp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import java.math.BigInteger;
import java.security.SecureRandom;

public class ShortcutActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent serviceIntent = new Intent(getApplicationContext(), PlexSearch.class);

		serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER, getIntent().getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER));
		serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, getIntent().getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT));


		SecureRandom random = new SecureRandom();
		serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
		PendingIntent resultsPendingIntent = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

		Intent listenerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
		listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
		listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
		listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.voice_prompt));

		startActivity(listenerIntent);
		finish();
	}
}
