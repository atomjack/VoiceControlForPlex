package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import com.atomjack.vcfp.services.PlexSearchService;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexServer;
import com.google.gson.Gson;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;

public class ShortcutActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent serviceIntent = new Intent(getApplicationContext(), PlexSearchService.class);

		// Shortcuts created before multiple connections were supported will not have any connections at all. So let's add one to the server
		// this shortcut was created for, composed of the server's address and port.
		Gson gson = new Gson();
		PlexServer server = gson.fromJson(getIntent().getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER), PlexServer.class);
		if(server != null) {
			if (server.connections.size() == 0) {
				server.connections = new ArrayList<Connection>();
				server.connections.add(new Connection("http", server.address, server.port));
			}
		}

		serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER, gson.toJson(server));
		serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, getIntent().getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT));
		serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_RESUME, getIntent().getBooleanExtra(VoiceControlForPlexApplication.Intent.EXTRA_RESUME, false));

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
