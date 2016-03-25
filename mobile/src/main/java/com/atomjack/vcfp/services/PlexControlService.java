package com.atomjack.vcfp.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.WearConstants;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.activities.VideoPlayerActivity;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexResponse;

import java.util.ArrayList;

public class PlexControlService extends IntentService {
  public static final String CLIENT = "com.atomjack.vcfp.mClient";
  public static final String MEDIA = "com.atomjack.vcfp.media";
  public static final String PLAYLIST = "com.atomjack.vcfp.playlist";

  private PlexClient client;

  private CastPlayerManager castPlayerManager;
  private PlexSubscription plexSubscription;
  private VoiceControlForPlexApplication.LocalClientSubscription localClientSubscription;

  private LocalMusicService localMusicService;

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
    localClientSubscription = VoiceControlForPlexApplication.getInstance().localClientSubscription;

    if(intent.getAction() != null) {
      client = intent.getParcelableExtra(CLIENT);
      PlexMedia playingMedia = intent.getParcelableExtra(MEDIA);
      ArrayList<? extends PlexMedia> playlist = intent.getParcelableArrayListExtra(PLAYLIST);
      PlayerState currentState;
      Timeline t = null;

      if(client.isLocalClient)
        currentState = localClientSubscription.currentState;
      else if(client.isCastClient) {
        currentState = castPlayerManager.getCurrentState();
      } else {
        t = plexSubscription.getCurrentTimeline();
        currentState = plexSubscription.getCurrentState();
      }

      if(client.isLocalClient) {
        Intent localVideoIntent = new Intent(this, VideoPlayerActivity.class);
        localVideoIntent.setAction(com.atomjack.shared.Intent.ACTION_VIDEO_DO_PLAYBACK);
        if(intent.getAction().equals(WearConstants.ACTION_PLAY))
          localVideoIntent.putExtra(com.atomjack.shared.Intent.ACTION_VIDEO_COMMAND, com.atomjack.shared.Intent.ACTION_PLAY);
        else if(intent.getAction().equals(WearConstants.ACTION_PAUSE))
          localVideoIntent.putExtra(com.atomjack.shared.Intent.ACTION_VIDEO_COMMAND, com.atomjack.shared.Intent.ACTION_PAUSE);
        else if(intent.getAction().equals(WearConstants.ACTION_STOP))
          localVideoIntent.putExtra(com.atomjack.shared.Intent.ACTION_VIDEO_COMMAND, com.atomjack.shared.Intent.ACTION_STOP);
        localVideoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(localVideoIntent);
      } else {
        PlexResponse response;

        if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PLAY)) {
          if (client.isLocalClient) {

          } else if (client.isCastClient) {
            castPlayerManager.play();
          } else {
            response = client.play();
            if (response.code == 200)
              currentState = PlayerState.PLAYING;
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PAUSE)) {
          if (client.isLocalClient) {

          } else if (client.isCastClient) {
            castPlayerManager.pause();
          } else {
            response = client.pause();
            if (response.code == 200)
              currentState = PlayerState.PAUSED;
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_STOP)) {
          if (client.isLocalClient) {

          } else if (client.isCastClient) {
            castPlayerManager.stop();
          } else {
            response = client.stop();
            if (response.code == 200)
              currentState = PlayerState.STOPPED;
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_REWIND)) {
          if (client.isCastClient) {
            castPlayerManager.seekTo(castPlayerManager.getPosition() - 15);
          } else {
            if (t != null) {
              int position = t.time;
              client.seekTo(position - 15000);
            }
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PREVIOUS)) {
          if (client.isCastClient) {
            castPlayerManager.doPrevious();
          } else {
            client.next(null);
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_FORWARD)) {
          if (client.isCastClient) {
            castPlayerManager.seekTo(castPlayerManager.getPosition() + 30);
          } else {
            if (t != null) {
              int position = t.time;
              client.seekTo(position + 30000);
            }
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_NEXT)) {
          if (client.isCastClient) {
            castPlayerManager.doNext();
          } else {
            client.next(null);
          }
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_DISCONNECT)) {
          if (client.isCastClient) {
            castPlayerManager.unsubscribe();
          } else {
            plexSubscription.unsubscribe();
          }
        }
      }

      Logger.d("[PlexControlService] state: %s", currentState);
      if(currentState != PlayerState.STOPPED)
        VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, playingMedia, playlist);
    }
  }
}
