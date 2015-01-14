package com.atomjack.wear;

import android.app.NotificationManager;
import android.content.Intent;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearListenerService extends WearableListenerService {
  private NotificationManager mNotifyMgr;
  private static final int NOTIFICATION_ID = 001;

  DataMap nowPlayingMedia = new DataMap();

  @Override
  public void onCreate() {
    super.onCreate();

    mNotifyMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    Logger.d("[WearListenerService] onDataChanged");
    DataMap dataMap;
    for (DataEvent event : dataEvents) {
      // Check the data type
      if (event.getType() == DataEvent.TYPE_CHANGED) {
        // Check the data path
        String path = event.getDataItem().getUri().getPath();
        if (path.equals(WearConstants.RECEIVE_MEDIA_IMAGE)) {
          dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
          Logger.d("Got asset! %s", dataMap.getAsset(WearConstants.IMAGE));
          Logger.d("DataMap received on watch: " + dataMap);
          PlayerState state = PlayerState.getState(dataMap.getString(WearConstants.PLAYBACK_STATE));
//          nowPlayingMedia.putAsset(WearConstants.IMAGE, dataMap.getAsset(WearConstants.IMAGE));
          WearApplication.getInstance().setNowPlayingImage(dataMap.getAsset(WearConstants.IMAGE));
          Logger.d("state: %s", state);
          if(state != PlayerState.STOPPED) {
            if(dataMap.getAsset(WearConstants.IMAGE) != null && WearApplication.getInstance().nowPlayingMedia != null) {
              WearApplication.getInstance().showNowPlaying();
            }
          }
        }
      }
    }
  }


  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    if(messageEvent.getPath() != null) {
      Logger.d("[WearListenerService] received message: %s", messageEvent.getPath());
      String message = messageEvent.getPath();
      DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
      Logger.d("[WearListenerService] data: %s", dataMap);

      if(message.equals(WearConstants.GET_PLAYBACK_STATE)) {

        PlayerState state = PlayerState.getState(dataMap.getString(WearConstants.PLAYBACK_STATE));
        Logger.d("[WearListenerService] state: %s", state);
        if(state == PlayerState.STOPPED) {
          Intent intent = new Intent(this, MainActivity.class);
          intent.setAction(MainActivity.START_SPEECH_RECOGNITION);
          intent.putExtra(WearConstants.CLIENT_NAME, nowPlayingMedia.getString(WearConstants.CLIENT_NAME));
          intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
          intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);

          Logger.d("[WearListenerService] Intent sent to MainActivity to start speech recognition");
        } else {
          nowPlayingMedia.putAll(dataMap);
          Logger.d("nowPlayingMedia: %s", nowPlayingMedia);
          WearApplication.getInstance().setNowPlayingMedia(nowPlayingMedia);
          WearApplication.getInstance().showNowPlaying();
          finishMain();
        }
      } else if(message.equals(WearConstants.PING)) {
        new SendToDataLayerThread(WearConstants.PONG, this).start();
      } else if(message.equals(WearConstants.WEAR_UNAUTHORIZED)) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.SHOW_WEAR_UNAUTHORIZED);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if(message.equals(WearConstants.WEAR_PURCHASED)) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.START_SPEECH_RECOGNITION);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if(message.equals(WearConstants.MEDIA_STOPPED)) {
        nowPlayingMedia = new DataMap();
        mNotifyMgr.cancel(NOTIFICATION_ID);
      } else if(message.equals(WearConstants.MEDIA_PLAYING) || message.equals(WearConstants.MEDIA_PAUSED)) {
        if(dataMap.getBoolean(WearConstants.LAUNCHED, false)) {
          finishMain();
        }
        // First, remove previous title and subtitle, if there are any
        nowPlayingMedia.remove(WearConstants.MEDIA_TITLE);
        nowPlayingMedia.remove(WearConstants.MEDIA_SUBTITLE);
        nowPlayingMedia = WearApplication.getInstance().nowPlayingMedia;
        nowPlayingMedia.putAll(dataMap);
        nowPlayingMedia.putString(WearConstants.PLAYBACK_STATE, message.equals(WearConstants.MEDIA_PLAYING) ? PlayerState.PLAYING.name() : PlayerState.PAUSED.name());
        WearApplication.getInstance().setNowPlayingMedia(nowPlayingMedia);
        WearApplication.getInstance().showNowPlaying();
      } else if(message.equals(WearConstants.SET_WEAR_OPTIONS)) {
        WearApplication.getInstance().prefs.put(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, dataMap.getBoolean(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT));
        if(WearApplication.getInstance().notificationIsUp) {
          WearApplication.getInstance().showNowPlaying();
        }
      } else if(message.equals(WearConstants.DISCONNECTED)) {
        nowPlayingMedia.clear();
        WearApplication.getInstance().cancelNowPlaying();
      } else if(message.equals(WearConstants.SPEECH_QUERY_RESULT)) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(WearConstants.SPEECH_QUERY_RESULT);
        intent.putExtra(WearConstants.SPEECH_QUERY_RESULT, dataMap.getBoolean(WearConstants.SPEECH_QUERY_RESULT, false));
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if(message.equals(WearConstants.SET_INFO)) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(WearConstants.SET_INFO);
        intent.putExtra(WearConstants.INFORMATION, dataMap.getString(WearConstants.INFORMATION));
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if(message.equals(WearConstants.FINISH)) {
        finishMain();
      }
    }
  }

  private void finishMain() {
    Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.setAction(MainActivity.FINISH);
    startActivity(intent);
  }
}
