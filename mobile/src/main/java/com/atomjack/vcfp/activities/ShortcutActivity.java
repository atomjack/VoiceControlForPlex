package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import com.atomjack.shared.UriDeserializer;
import com.atomjack.shared.UriSerializer;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.services.PlexSearchService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;

public class ShortcutActivity extends Activity {
  protected Gson gsonRead = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriDeserializer())
          .create();

  protected Gson gsonWrite = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriSerializer())
          .create();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
    try {
      Intent serviceIntent = new Intent(getApplicationContext(), PlexSearchService.class);

      // Shortcuts created before multiple connections were supported will not have any connections at all. So let's add one to the server
      // this shortcut was created for, composed of the server's address and port.
      PlexServer server = gsonRead.fromJson(getIntent().getStringExtra(com.atomjack.shared.Intent.EXTRA_SERVER), PlexServer.class);
      if (server != null) {
        if (server.connections.size() == 0) {
          server.connections = new ArrayList<Connection>();
          server.connections.add(new Connection("http", server.address, server.port));
        }
      }

      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, gsonWrite.toJson(server));
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, getIntent().getStringExtra(com.atomjack.shared.Intent.EXTRA_CLIENT));
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, getIntent().getBooleanExtra(com.atomjack.shared.Intent.EXTRA_RESUME, false));

      SecureRandom random = new SecureRandom();
      serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
      PendingIntent resultsPendingIntent = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

      Intent listenerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
      listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.voice_prompt));

      startActivity(listenerIntent);
    } catch(ActivityNotFoundException e) {
      Intent browserIntent = new Intent(Intent.ACTION_VIEW,   Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.voicesearch"));
      startActivity(browserIntent);
    }
		finish();
	}
}
