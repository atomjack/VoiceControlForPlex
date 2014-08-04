package com.atomjack.vcfp.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;

import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
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

  private CastPlayerManager castPlayerManager;

  public PlexControlService() {
    super("Plex Control Service");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onHandleIntent(final Intent intent) {

    if(castPlayerManager == null)
      castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;

    if(intent.getAction() != null) {
      client = intent.getParcelableExtra(CLIENT);
      PlexMedia playingMedia = intent.getParcelableExtra(MEDIA);
      PlayerState currentState;
      Timeline t = null;

      if(client.isCastClient) {
        currentState = castPlayerManager.getCurrentState();
      } else {
        Header[] headers = {
                new BasicHeader(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getInstance().prefs.getUUID())
        };
        MediaContainer mc = PlexHttpClient.getSync(String.format("http://%s:%s/player/timeline/poll?commandID=0", client.address, client.port), headers);
        t = mc.getActiveTimeline();
        currentState = PlayerState.getState(t.state);
      }

      PlexResponse response = null;

      if (intent.getAction().equals(ACTION_PLAY)) {
        if(client.isCastClient) {
          castPlayerManager.play();
        } else {
          response = client.play();
          if (response.code.equals("200"))
            currentState = PlayerState.PLAYING;
        }
      } else if (intent.getAction().equals(ACTION_PAUSE)) {
        if(client.isCastClient) {
          castPlayerManager.pause();
        } else {
          response = client.pause();
          if (response.code.equals("200"))
            currentState = PlayerState.PAUSED;
        }
      } else if (intent.getAction().equals(ACTION_STOP)) {
        if(client.isCastClient) {
          castPlayerManager.stop();
        } else {
          response = client.stop();
          if (response.code.equals("200"))
            currentState = PlayerState.STOPPED;
        }
      }


      if (intent.getAction().equals(ACTION_REWIND)) {
        if(t != null) {
          int position = t.time;
          client.seekTo(position - 15000);
        }
      }

      Logger.d("state: %s", currentState);
      VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, playingMedia);
    }
  }
}
