package com.atomjack.vcfp.services;


import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.speech.RecognizerIntent;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.activities.MainActivity;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WearListenerService extends WearableListenerService {

  GoogleApiClient googleApiClient;

  Handler handler = new Handler();
  // How many times we've had the wear request playback state.
  int playbackStateRetries = 0;

  @Override
  public void onCreate() {
    super.onCreate();
    googleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build();
    googleApiClient.connect();
  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    String message = messageEvent.getPath() == null ? "" : messageEvent.getPath();
    Logger.d("[WearListenerService] onMessageReceived: %s", message);

    if(!VoiceControlForPlexApplication.getInstance().getInventoryQueried() && !VoiceControlForPlexApplication.getInstance().hasWear() && message.equals(WearConstants.GET_PLAYBACK_STATE)) {
      // This message was received before we've had a chance to check with Google on whether or not Wear support
      // has been purchased. After a delay of 500ms, send a message back to the Wearable to get playback state again.
      // By then, it should have had time to see if Wear Support has been purchased.
      playbackStateRetries++;
      if(playbackStateRetries < 4) {
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            new SendToDataLayerThread(WearConstants.RETRY_GET_PLAYBACK_STATE, WearListenerService.this).start();
          }
        }, 500);
        return;
      }
    }
    if(!VoiceControlForPlexApplication.getInstance().hasWear() && !message.equals(WearConstants.PONG)) {
      // Wear support has not been purchased, so send a message back to the wear device, and show the purchase required
      // popup on the handheld. However, if the message is 'pong', a response to a 'ping', skip since we want to react to a pong
      // even if wear support has not been purchased (so we can alert the user to the option to purchase)
      new SendToDataLayerThread(WearConstants.WEAR_UNAUTHORIZED, this).start();
      Logger.d("Showing wear popup: %s", VoiceControlForPlexApplication.isApplicationVisible());
      if(VoiceControlForPlexApplication.isApplicationVisible()) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE_REQUIRED);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      }
      return;
    }
    if(messageEvent.getPath() != null) {
      final DataMap dataMap = new DataMap();
      final DataMap receivedDataMap = DataMap.fromByteArray(messageEvent.getData());

      PlexSubscription plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
      CastPlayerManager castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
      PlexClient client = new PlexClient();
      if(VoiceControlForPlexApplication.getInstance().localClientSubscription.subscribed) {
        client = PlexClient.getLocalPlaybackClient();
      } else if (plexSubscription.isSubscribed()) {
        client = plexSubscription.mClient;

      } else if (castPlayerManager.isSubscribed()) {
        client = castPlayerManager.mClient;
      }
      if(message.equals(WearConstants.SPEECH_QUERY)) {
        Logger.d("[WearListenerService] message received: %s", receivedDataMap);

        Intent sendIntent = new Intent(this, PlexSearchService.class);
        sendIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, receivedDataMap.getStringArrayList(WearConstants.SPEECH_QUERY));
        sendIntent.putExtra(WearConstants.FROM_WEAR, true);
        sendIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startService(sendIntent);
      } else if(message.equals(WearConstants.GET_PLAYBACK_STATE)) {
        Logger.d("[WearListenerService] get playback state");
        dataMap.putBoolean(WearConstants.LAUNCHED, receivedDataMap.getBoolean(WearConstants.LAUNCHED, false));
        if (VoiceControlForPlexApplication.getInstance().localClientSubscription.subscribed) {
          PlexMedia media = VoiceControlForPlexApplication.getInstance().localClientSubscription.media;
          if(media != null && !media.isMusic()) {
            // video is playing, get current state and send to wear device
            sendPlayingStatusToWear(media, client, VoiceControlForPlexApplication.getInstance().localClientSubscription.currentState, receivedDataMap);
          } else if(media == null) {
            dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
            new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
          }
        } else if (plexSubscription.isSubscribed()) {
          PlayerState currentState = plexSubscription.getCurrentState();
          Logger.d("[WearListenerService] current State: %s", currentState);

          dataMap.putString(WearConstants.CLIENT_NAME, client.name);
          dataMap.putString(WearConstants.PLAYBACK_STATE, currentState.name());

          if(plexSubscription.getNowPlayingMedia() != null) {
            PlexMedia media = plexSubscription.getNowPlayingMedia();
            Logger.d("now playing: %s", plexSubscription.getNowPlayingMedia().title);
            VoiceControlForPlexApplication.setWearMediaTitles(dataMap, media);
            dataMap.putString(WearConstants.MEDIA_TYPE, media.getType());
            VoiceControlForPlexApplication.getInstance().getWearMediaImage(media, new BitmapHandler() {
              @Override
              public void onSuccess(Bitmap bitmap) {
                DataMap binaryDataMap = new DataMap();
                binaryDataMap.putAll(dataMap);
                binaryDataMap.putAsset(WearConstants.IMAGE, VoiceControlForPlexApplication.createAssetFromBitmap(bitmap));
                binaryDataMap.putString(WearConstants.PLAYBACK_STATE, dataMap.getString(WearConstants.PLAYBACK_STATE));
                new SendToDataLayerThread(WearConstants.RECEIVE_MEDIA_IMAGE, binaryDataMap, WearListenerService.this).sendDataItem();
                new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, WearListenerService.this).start();
                Logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
              }
            });
          } else {
            dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
            new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
          }
        } else if (castPlayerManager.isSubscribed()) {
          PlayerState currentState = castPlayerManager.getCurrentState();
          dataMap.putString(WearConstants.CLIENT_NAME, client.name);
          dataMap.putString(WearConstants.PLAYBACK_STATE, currentState.name());

          if(castPlayerManager.getNowPlayingMedia() != null) {
            Logger.d("now playing: %s", castPlayerManager.getNowPlayingMedia().title);
            VoiceControlForPlexApplication.setWearMediaTitles(dataMap, castPlayerManager.getNowPlayingMedia());
            dataMap.putString(WearConstants.MEDIA_TYPE, castPlayerManager.getNowPlayingMedia().getType());
            VoiceControlForPlexApplication.getInstance().getWearMediaImage(castPlayerManager.getNowPlayingMedia(), new BitmapHandler() {
              @Override
              public void onSuccess(Bitmap bitmap) {
                DataMap binaryDataMap = new DataMap();
                binaryDataMap.putAll(dataMap);
                binaryDataMap.putAsset(WearConstants.IMAGE, VoiceControlForPlexApplication.createAssetFromBitmap(bitmap));
                binaryDataMap.putString(WearConstants.PLAYBACK_STATE, dataMap.getString(WearConstants.PLAYBACK_STATE));
                new SendToDataLayerThread(WearConstants.RECEIVE_MEDIA_IMAGE, binaryDataMap, WearListenerService.this).sendDataItem();
                new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, WearListenerService.this).start();
                Logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
              }
            });
          } else {
            dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
            new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
          }

        } else {
          // Not subscribed to a client
          dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
          new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
        }


      } else if(message.equals(WearConstants.GET_PLAYING_MEDIA)) {
        // Send an intent to MainActivity to tell it to forward on information about the currently playing media back to the wear device
        if(VoiceControlForPlexApplication.isApplicationVisible()) {
          Intent intent = new Intent(this, MainActivity.class);
          intent.setAction(com.atomjack.shared.Intent.GET_PLAYING_MEDIA);
          intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
          intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
        }
      } else if(message.equals(WearConstants.PONG)) {
        // Received a pong back from the user, so show a popup allowing the user to purchase wear support.
        Logger.d("[WearListenerService] Received pong");
        if(VoiceControlForPlexApplication.isApplicationVisible()) {
          Intent intent = new Intent(this, MainActivity.class);
          intent.setAction(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE);
          intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
          intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
        }
      } else if(message.equals(WearConstants.ACTION_PAUSE) || message.equals(WearConstants.ACTION_PLAY) || message.equals(WearConstants.ACTION_STOP)) {
        PlexMedia media = null;
        if(client.isLocalClient) {
          media = VoiceControlForPlexApplication.getInstance().localClientSubscription.media;
        } else if(castPlayerManager.isSubscribed())
          media = castPlayerManager.getNowPlayingMedia();
        else if(plexSubscription.isSubscribed())
          media = plexSubscription.getNowPlayingMedia();
        if(media != null) {
          Intent intent = new android.content.Intent(this, PlexControlService.class);
          intent.setAction(message);
          intent.putExtra(PlexControlService.CLIENT, client);
          intent.putExtra(PlexControlService.MEDIA, media);
          startService(intent);
          Logger.d("[WearListenerService] Sent %s to %s", message, client.name);
        }
      } else if(message.equals(WearConstants.GET_DEVICE_LOGS)) {
        Intent intent = new android.content.Intent(this, MainActivity.class);
        intent.setAction(message);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(WearConstants.LOG_CONTENTS, receivedDataMap.getString(WearConstants.LOG_CONTENTS));
        startActivity(intent);
      }
    }
  }

  private void sendPlayingStatusToWear(PlexMedia media, PlexClient client, PlayerState currentState, DataMap receivedDataMap) {
    final DataMap dataMap = new DataMap();
    dataMap.putBoolean(WearConstants.LAUNCHED, receivedDataMap.getBoolean(WearConstants.LAUNCHED, false));
    dataMap.putString(WearConstants.PLAYBACK_STATE, currentState.name());
    dataMap.putString(WearConstants.CLIENT_NAME, client.name);

    VoiceControlForPlexApplication.setWearMediaTitles(dataMap, media);

    VoiceControlForPlexApplication.getInstance().getWearMediaImage(media, new BitmapHandler() {
      @Override
      public void onSuccess(Bitmap bitmap) {
        DataMap binaryDataMap = new DataMap();
        binaryDataMap.putAll(dataMap);
        binaryDataMap.putAsset(WearConstants.IMAGE, VoiceControlForPlexApplication.createAssetFromBitmap(bitmap));
        binaryDataMap.putString(WearConstants.PLAYBACK_STATE, dataMap.getString(WearConstants.PLAYBACK_STATE));
        new SendToDataLayerThread(WearConstants.RECEIVE_MEDIA_IMAGE, binaryDataMap, WearListenerService.this).sendDataItem();
        new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, WearListenerService.this).start();
        Logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
      }
    });
  }
}
