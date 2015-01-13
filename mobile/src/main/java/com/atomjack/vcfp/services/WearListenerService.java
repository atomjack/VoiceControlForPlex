package com.atomjack.vcfp.services;


import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.speech.RecognizerIntent;

import com.atomjack.shared.Logger;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.UriSerializer;
import com.atomjack.shared.WearConstants;
import com.atomjack.shared.UriDeserializer;
import com.atomjack.vcfp.CastPlayerManager;
import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.activities.MainActivity;
import com.atomjack.vcfp.activities.NowPlayingActivity;
import com.atomjack.vcfp.activities.VCFPActivity;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Hashtable;

public class WearListenerService extends WearableListenerService {

  protected Gson gsonRead = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriDeserializer())
          .create();

  protected Gson gsonWrite = new GsonBuilder()
          .registerTypeAdapter(Uri.class, new UriSerializer())
          .create();

  GoogleApiClient googleApiClient;

  @Override
  public void onCreate() {
    super.onCreate();
    googleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build();
    googleApiClient.connect();
  }

//  @Override
//  public void onDataChanged(DataEventBuffer dataEvents) {
//    Logger.d("[WearListenerService] onDataChanged");
//    DataMap dataMap;
//    for (DataEvent event : dataEvents) {
//      dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
//      // Check the data type
//      if (event.getType() == DataEvent.TYPE_CHANGED) {
//        // Check the data path
//        String path = event.getDataItem().getUri().getPath();
//        if (path.equals(WearConstants.GET_PLAYBACK_STATE)) {
//          Logger.d("GET_PLAYBACK_STATE");
//        }
//      }
//    }
//
//  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    String message = messageEvent.getPath() == null ? "" : messageEvent.getPath();
    Logger.d("[WearListenerService] onMessageReceived: %s", message);
    if(!VoiceControlForPlexApplication.getInstance().hasWear() && !message.equals(WearConstants.PONG)) {
      // Wear support has not been purchased, so send a message back to the wear device, and show the purchase required
      // popup on the handheld. However, if the message is 'pong', a response to a 'ping', skip since we want to react to a pong
      // even if wear support has not been purchased (so we can alert the user to the option to purchase)
      new SendToDataLayerThread(WearConstants.WEAR_UNAUTHORIZED, this).start();
      Intent intent = new Intent(this, MainActivity.class);
      intent.setAction(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE_REQUIRED);
      intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
      intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      return;
    }
    if(messageEvent.getPath() != null) {
      final DataMap dataMap = new DataMap();

      PlexSubscription plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
      CastPlayerManager castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
      VCFPActivity listener = plexSubscription.getListener();
      PlexClient client = new PlexClient();
      if (plexSubscription.isSubscribed()) {
        client = plexSubscription.mClient;

      } else if (castPlayerManager.isSubscribed()) {
        // TODO: Handle chromecast clients
      }


      if(message.equals(WearConstants.SPEECH_QUERY)) {
        DataMap dataMap1 = DataMap.fromByteArray(messageEvent.getData());
        Logger.d("[WearListenerService] message received: %s", dataMap1);

        Intent sendIntent = new Intent(this, PlexSearchService.class);
        sendIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, dataMap1.getStringArrayList(WearConstants.SPEECH_QUERY));
        sendIntent.putExtra(WearConstants.FROM_WEAR, true);
        sendIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startService(sendIntent);
      } else if(message.equals(WearConstants.GET_PLAYBACK_STATE)) {
        Logger.d("[WearListenerService] get playback state");
//        PlexSubscription plexSubscription = VoiceControlForPlexApplication.getInstance().plexSubscription;
//        CastPlayerManager castPlayerManager = VoiceControlForPlexApplication.getInstance().castPlayerManager;
        if (plexSubscription.isSubscribed()) {
//          PlexClient client = plexSubscription.mClient;

          Header[] headers = {
                  new BasicHeader(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getInstance().prefs.getUUID())
          };
          MediaContainer mc = PlexHttpClient.getSync(String.format("http://%s:%s/player/timeline/poll?commandID=0", client.address, client.port), headers);
          Timeline t = mc.getActiveTimeline();
          PlayerState currentState = PlayerState.getState(t);
          Logger.d("[WearListenerService] current State: %s", currentState);


          dataMap.putString(WearConstants.CLIENT_NAME, client.name);
          dataMap.putString(WearConstants.PLAYBACK_STATE, currentState.name());



          if(listener != null && listener.getNowPlayingMedia() != null) {
            Logger.d("now playing: %s", listener.getNowPlayingMedia().title);
            dataMap.putString(WearConstants.MEDIA_TITLE, listener.getNowPlayingMedia().title);
            final PlexMedia media = plexSubscription.getListener().getNowPlayingMedia();
            VoiceControlForPlexApplication.getWearMediaImage(media, new BitmapHandler() {
              @Override
              public void onSuccess(Bitmap bitmap) {
                DataMap binaryDataMap = new DataMap();
                binaryDataMap.putAll(dataMap);
                binaryDataMap.putAsset(WearConstants.IMAGE, VoiceControlForPlexApplication.createAssetFromBitmap(bitmap));
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
          // TODO: Handle chromecast clients
        } else {
          dataMap.putString(WearConstants.PLAYBACK_STATE, PlayerState.STOPPED.name());
          new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
        }


      } else if(message.equals(WearConstants.GET_PLAYING_MEDIA)) {
        // Send an intent to NowPlayingActivity to tell it to forward on information about the currently playing media back to the wear device
        Intent intent = new Intent(this, NowPlayingActivity.class);
        intent.setAction(com.atomjack.shared.Intent.GET_PLAYING_MEDIA);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if(message.equals(WearConstants.PONG)) {
        // Received a pong back from the user, so show a popup allowing the user to purchase wear support.
        Logger.d("[WearListenerService] Received pong");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(com.atomjack.shared.Intent.SHOW_WEAR_PURCHASE);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if(message.equals(WearConstants.ACTION_PAUSE) || message.equals(WearConstants.ACTION_PLAY) || message.equals(WearConstants.ACTION_STOP)) {
        Intent intent = new android.content.Intent(this, PlexControlService.class);
        intent.setAction(message);
        intent.putExtra(PlexControlService.CLIENT, client);
        intent.putExtra(PlexControlService.MEDIA, listener.getNowPlayingMedia());
        startService(intent);
        Logger.d("[WearListenerService] Sent %s to %s", message, client.name);
      }
    }
  }


}
