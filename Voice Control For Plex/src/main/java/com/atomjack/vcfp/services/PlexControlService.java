package com.atomjack.vcfp.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

public class PlexControlService extends IntentService {
  public static final String ACTION_PLAY = "com.atomjack.vcfp.action_play";
  public static final String ACTION_PAUSE = "com.atomjack.vcfp.action_pause";
  public static final String ACTION_STOP = "com.atomjack.vcfp.action_stop";
  public static final String ACTION_REWIND = "com.atomjack.vcfp.action_rewind";

  public static final String CLIENT = "com.atomjack.vcfp.mClient";
  public static final String MEDIA = "com.atomjack.vcfp.media";

  private PlexClient client;

  private static Serializer serial = new Persister();

  public PlexControlService() {
    super("Plex Control Service");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onHandleIntent(final Intent intent) {

    if(intent.getAction() != null) {
      client = intent.getParcelableExtra(CLIENT);
      PlexMedia playingMedia = intent.getParcelableExtra(MEDIA);

      Preferences.setContext(this);
      Header[] headers = {
        new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID())
      };
      MediaContainer mc = PlexHttpClient.getSync(String.format("http://%s:%s/player/timeline/poll?commandID=0", client.address, client.port), headers);
      Timeline t = mc.getActiveTimeline();
      PlayerState currentState = PlayerState.getState(t.state);

      PlexResponse response = null;

      if (intent.getAction().equals(ACTION_PLAY)) {
        response = client.play();
        if(response.code.equals("200"))
          currentState = PlayerState.PLAYING;
      } else if (intent.getAction().equals(ACTION_PAUSE)) {
        response = client.pause();
        if(response.code.equals("200"))
          currentState = PlayerState.PAUSED;
      } else if (intent.getAction().equals(ACTION_STOP)) {
        response = client.stop();
        if(response.code.equals("200"))
          currentState = PlayerState.STOPPED;
      }


      if (intent.getAction().equals(ACTION_REWIND)) {
        if(t != null) {
          int position = t.time;
          client.seekTo(position - 15000);
        }
      }

      Logger.d("state: %s", currentState);
      VoiceControlForPlexApplication.setNotification(getApplicationContext(), client, currentState, playingMedia);
    }
  }
}
