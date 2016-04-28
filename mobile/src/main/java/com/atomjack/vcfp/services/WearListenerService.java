package com.atomjack.vcfp.services;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;

import com.atomjack.shared.NewLogger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.CastPlayerManager;
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

public class WearListenerService extends WearableListenerService implements ServiceConnection {
  private NewLogger logger;

  GoogleApiClient googleApiClient;

  Handler handler = new Handler();
  // How many times we've had the wear request playback state.
  int playbackStateRetries = 0;

  private SubscriptionService subscriptionService;
  private boolean subscriptionServiceIsBound = false;
  private Runnable subscriptionServiceOnConnected = null;

  @Override
  public void onCreate() {
    super.onCreate();
    logger = new NewLogger(this);

    logger.d("[WearListenerService] onCreate");
    googleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build();
    googleApiClient.connect();



    Intent subscriptionServiceIntent = new Intent(getApplicationContext(), SubscriptionService.class);
    getApplicationContext().bindService(subscriptionServiceIntent, this, Context.BIND_AUTO_CREATE);
    getApplicationContext().startService(subscriptionServiceIntent);
  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    if(!subscriptionServiceIsBound) {
      // If the subscription service isn't bound yet, wait until it is and then call this method again
      subscriptionServiceOnConnected = () -> {
        onMessageReceived(messageEvent);
      };
      return;
    }

    String message = messageEvent.getPath() == null ? "" : messageEvent.getPath();
    logger.d("[WearListenerService] onMessageReceived: %s", message);

    if(!VoiceControlForPlexApplication.getInstance().getInventoryQueried() && !VoiceControlForPlexApplication.getInstance().hasWear() && message.equals(WearConstants.GET_PLAYBACK_STATE)) {
      // This message was received before we've had a chance to check with Google on whether or not Wear support
      // has been purchased. After a delay of 500ms, send a message back to the Wearable to get playback state again.
      // By then, it should have had time to see if Wear Support has been purchased.
      playbackStateRetries++;
      if(playbackStateRetries < 4) {
        handler.postDelayed(() -> new SendToDataLayerThread(WearConstants.RETRY_GET_PLAYBACK_STATE, WearListenerService.this).start(), 500);
        return;
      }
    }
    if(!VoiceControlForPlexApplication.getInstance().hasWear() && !message.equals(WearConstants.PONG)) {
      // Wear support has not been purchased, so send a message back to the wear device, and show the purchase required
      // popup on the handheld. However, if the message is 'pong', a response to a 'ping', skip since we want to react to a pong
      // even if wear support has not been purchased (so we can alert the user to the option to purchase)
      new SendToDataLayerThread(WearConstants.WEAR_UNAUTHORIZED, this).start();
      logger.d("Showing wear popup: %s", VoiceControlForPlexApplication.isApplicationVisible());
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

      CastPlayerManager castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;

      PlexClient client = subscriptionService.getClient();

      switch (message) {
        case WearConstants.SPEECH_QUERY:
          logger.d("[WearListenerService] message received: %s", receivedDataMap);

          Intent sendIntent = new Intent(this, PlexSearchService.class);
          sendIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, receivedDataMap.getStringArrayList(WearConstants.SPEECH_QUERY));
          sendIntent.putExtra(WearConstants.FROM_WEAR, true);
          sendIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
          sendIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startService(sendIntent);
          break;
        case WearConstants.GET_PLAYBACK_STATE:
          logger.d("[WearListenerService] get playback state");
          dataMap.putBoolean(WearConstants.LAUNCHED, receivedDataMap.getBoolean(WearConstants.LAUNCHED, false));

          // TODO: Handle local playback & chromecast
          if(subscriptionService.isSubscribed()) {
            PlexMedia media = subscriptionService.getNowPlayingMedia();
            PlayerState currentState = subscriptionService.getCurrentState();

            dataMap.putString(WearConstants.CLIENT_NAME, client.name);
            dataMap.putString(WearConstants.PLAYBACK_STATE, currentState.name());

            if (media != null) {
              logger.d("now playing: %s", media.title);
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
                  logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
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


          /*
          if (VoiceControlForPlexApplication.getInstance().localClientSubscription.subscribed) {
            PlexMedia media = VoiceControlForPlexApplication.getInstance().localClientSubscription.media;
            if (media != null && !media.isMusic()) {
              // video is playing, get current state and send to wear device
              sendPlayingStatusToWear(media, client, VoiceControlForPlexApplication.getInstance().localClientSubscription.currentState, receivedDataMap);
            } else if (media == null) {
              dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
              new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
            }
          } else if (plexSubscription.isSubscribed()) {
            PlayerState currentState = plexSubscription.getCurrentState();
            logger.d("[WearListenerService] current State: %s", currentState);

            dataMap.putString(WearConstants.CLIENT_NAME, client.name);
            dataMap.putString(WearConstants.PLAYBACK_STATE, currentState.name());

            if (plexSubscription.getNowPlayingMedia() != null) {
              PlexMedia media = plexSubscription.getNowPlayingMedia();
              logger.d("now playing: %s", plexSubscription.getNowPlayingMedia().title);
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
                  logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
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

            if (castPlayerManager.getNowPlayingMedia() != null) {
              logger.d("now playing: %s", castPlayerManager.getNowPlayingMedia().title);
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
                  logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
                }
              });
            } else {
              dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
              new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
            }

          }
          */


          break;
        case WearConstants.GET_PLAYING_MEDIA:
          // Send an intent to MainActivity to tell it to forward on information about the currently playing media back to the wear device
          if (VoiceControlForPlexApplication.isApplicationVisible()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(com.atomjack.shared.Intent.GET_PLAYING_MEDIA);
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
          }
          break;
        case WearConstants.PONG:
          // Received a pong back from the user, so show a popup allowing the user to purchase wear support.
          logger.d("[WearListenerService] Received pong");
          if (VoiceControlForPlexApplication.isApplicationVisible()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE);
            intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
          }
          break;
        case WearConstants.ACTION_PAUSE:
        case WearConstants.ACTION_PLAY:
        case WearConstants.ACTION_STOP:
          PlexMedia media = subscriptionService.getNowPlayingMedia();
          if (media != null) {
            Intent intent = new Intent(this, SubscriptionService.class);
            intent.setAction(message);
            intent.putExtra(SubscriptionService.CLIENT, client);
            intent.putExtra(SubscriptionService.MEDIA, media);
            startService(intent);
            logger.d("[WearListenerService] Sent %s to %s", message, client.name);
          }
          break;
        case WearConstants.GET_DEVICE_LOGS:
          Intent intent = new Intent(this, MainActivity.class);
          intent.setAction(message);
          intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
          intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          intent.putExtra(WearConstants.LOG_CONTENTS, receivedDataMap.getString(WearConstants.LOG_CONTENTS));
          startActivity(intent);
          break;
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
        logger.d("[WearListenerService] sent is playing status (%s) to wearable.", dataMap.getString(WearConstants.PLAYBACK_STATE));
      }
    });
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    SubscriptionService.SubscriptionBinder binder = (SubscriptionService.SubscriptionBinder)service;
    subscriptionService = binder.getService();
    logger.d("got subscription service");
    subscriptionServiceIsBound = true;
    if(subscriptionServiceOnConnected != null)
      subscriptionServiceOnConnected.run();
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {
    subscriptionServiceIsBound = false;
  }
}
