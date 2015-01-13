package com.atomjack.vcfp.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexResponse;

public class PlexControlService extends IntentService {
  public static final String ACTION_PLAY = "com.atomjack.vcfp.action_play";
  public static final String ACTION_PAUSE = "com.atomjack.vcfp.action_pause";
  public static final String ACTION_STOP = "com.atomjack.vcfp.action_stop";
  public static final String ACTION_REWIND = "com.atomjack.vcfp.action_rewind";
  public static final String ACTION_FORWARD = "com.atomjack.vcfp.action_forward";
  public static final String ACTION_DISCONNECT = "com.atomjack.vcfp.action_disconnect";

  public static final String CLIENT = "com.atomjack.vcfp.mClient";
  public static final String MEDIA = "com.atomjack.vcfp.media";

  private PlexClient client;

  private CastPlayerManager castPlayerManager;
  private PlexSubscription plexSubscription;

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
    if(plexSubscription == null)
      plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;

    if(intent.getAction() != null) {
      client = intent.getParcelableExtra(CLIENT);
      PlexMedia playingMedia = intent.getParcelableExtra(MEDIA);
      PlayerState currentState;
      Timeline t = null;

      if(client.isCastClient) {
        currentState = castPlayerManager.getCurrentState();
      } else {
        t = plexSubscription.getCurrentTimeline();
        currentState = plexSubscription.getCurrentState();
      }

      PlexResponse response;

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
      } else if (intent.getAction().equals(ACTION_REWIND)) {
        if(client.isCastClient) {

        } else {
          if (t != null) {
            int position = t.time;
            client.seekTo(position - 15000);
          }
        }
      } else if(intent.getAction().equals(ACTION_FORWARD)) {
        if(client.isCastClient) {

        } else {
          if (t != null) {
            int position = t.time;
            client.seekTo(position + 30000);
          }
        }
      } else if(intent.getAction().equals(ACTION_DISCONNECT)) {
        if(client.isCastClient) {

        } else {
          plexSubscription.unsubscribe();
        }
      }

      Logger.d("[PlexControlService] state: %s", currentState);
      if(currentState != PlayerState.STOPPED)
        VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, playingMedia);
    }
  }
}
