package com.atomjack.vcfp.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

public class PlexControlService extends Service {
  public static final String ACTION_PLAY = "com.atomjack.vcfp.action_play";
  public static final String ACTION_PAUSE = "com.atomjack.vcfp.action_pause";
  public static final String ACTION_STOP = "com.atomjack.vcfp.action_stop";
  public static final String ACTION_REWIND = "com.atomjack.vcfp.action_rewind";

  public static final String CLIENT = "com.atomjack.vcfp.client";

  private PlexClient client;

  private static Serializer serial = new Persister();

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {

    Logger.d("PlexControlService got new intent, action: %s", intent.getAction());
    if(intent.getAction() != null) {
      client = intent.getParcelableExtra(CLIENT);

      PlexHttpResponseHandler responseHandler = new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
        }

        @Override
        public void onFailure(Throwable error) {

        }
      };

      if (intent.getAction().equals(ACTION_PLAY)) {
        client.play(responseHandler);
      } else if (intent.getAction().equals(ACTION_PAUSE)) {
        client.pause(responseHandler);
      } else if (intent.getAction().equals(ACTION_STOP)) {
        client.stop(responseHandler);
      } else if (intent.getAction().equals(ACTION_REWIND)) {
        Header[] headers = {
          new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID())
        };
        PlexHttpClient.get(getApplicationContext(), String.format("http://%s:%s/player/timeline/poll?commandID=0", client.address, client.port), headers, new PlexHttpMediaContainerHandler() {
          @Override
          public void onSuccess(MediaContainer mediaContainer) {
            Timeline t = mediaContainer.getActiveTimeline();
            if(t != null) {
              int position = t.time;
              client.seekTo(position - 15000, null);
            }
          }

          @Override
          public void onFailure(Throwable error) {

          }
        });
      }
    }
    return START_STICKY;
  }
}
