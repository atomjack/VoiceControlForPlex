package com.atomjack.vcfp;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.WearConstants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class WearApplication extends Application {
  private static WearApplication instance;
  private NotificationManagerCompat mNotifyMgr;
  private static final int NOTIFICATION_ID = 001;
  GoogleApiClient googleApiClient;
  DataMap nowPlayingMedia = new DataMap();
  public Preferences prefs;
  private Bitmap nowPlayingImage;

  public boolean notificationIsUp = false;

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    mNotifyMgr = NotificationManagerCompat.from(this);

    googleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build();
    googleApiClient.connect();

    prefs = new Preferences(getApplicationContext());
  }

  public static WearApplication getInstance() {
    return instance;
  }

  public void setNowPlayingMedia(DataMap dataMap) {
    nowPlayingMedia = dataMap;
  }

  private NotificationCompat.Action getPlayPauseAction() {
    Intent playPauseIntent = new Intent(this, PlexControlService.class);
    int playPauseIcon;
    Logger.d("state: %s", nowPlayingMedia.getString(WearConstants.PLAYBACK_STATE));
    if(PlayerState.getState(nowPlayingMedia.getString(WearConstants.PLAYBACK_STATE)) == PlayerState.PAUSED) {
      Logger.d("is paused");
      playPauseIntent.setAction(WearConstants.ACTION_PLAY);
      playPauseIcon = R.drawable.button_play;
    } else {
      Logger.d("is playing");
      playPauseIntent.setAction(WearConstants.ACTION_PAUSE);
      playPauseIcon = R.drawable.button_pause;
    }

    NotificationCompat.Action playPauseAction = new NotificationCompat.Action.Builder(playPauseIcon,
            "", PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT))
            .build();
    return playPauseAction;
  }

  private NotificationCompat.Action getVoiceInputAction() {
    String[] voiceInputExamples = getResources().getStringArray(R.array.voice_input_examples);

    RemoteInput remoteInput = new RemoteInput.Builder(WearConstants.SPEECH_QUERY)
            .setLabel(getResources().getString(R.string.voice_input_label))
            //.setChoices(voiceInputExamples)
            .build();

    Intent intent = new Intent(this, MainActivity.class);
    intent.setAction(MainActivity.RECEIVE_VOICE_INPUT);
    intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    NotificationCompat.Action voiceInputAction = new NotificationCompat.Action.Builder(R.drawable.mic,
            "", PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
            .addRemoteInput(remoteInput)
            .build();
    return voiceInputAction;
  }

  private NotificationCompat.Action getStopAction() {

    Intent stopIntent = new Intent(this, PlexControlService.class);
    stopIntent.setAction(WearConstants.ACTION_STOP);
    NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(R.drawable.button_stop,
            "", PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT))
            .build();
    return stopAction;
  }

  public void showNowPlaying() {
    Logger.d("[WearApplication] showNowPlaying: %s", nowPlayingMedia.getString(WearConstants.MEDIA_TITLE));
    Logger.d("[WearApplication] Media: %s", nowPlayingMedia);

    NotificationCompat.WearableExtender extender;
    if(nowPlayingImage != null) {
      extender = new NotificationCompat.WearableExtender()
              .addAction(getPlayPauseAction())
              .addAction(getVoiceInputAction())
              .addAction(getStopAction())
              .setContentAction(prefs.get(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, false) ? 1 : 0)
              .setHintHideIcon(true)
              .setBackground(nowPlayingImage);
    } else {
      extender = new NotificationCompat.WearableExtender()
              .addAction(getPlayPauseAction())
              .addAction(getVoiceInputAction())
              .addAction(getStopAction())
              .setContentAction(prefs.get(WearConstants.PRIMARY_FUNCTION_VOICE_INPUT, false) ? 1 : 0)
              .setHintHideIcon(true);
    }

    String title = nowPlayingMedia.getString(WearConstants.MEDIA_TITLE);
    String subtitle = nowPlayingMedia.getString(WearConstants.MEDIA_SUBTITLE);
    // Create the notification
    NotificationCompat.Builder notificationBuilder =
            new NotificationCompat.Builder(this)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .extend(extender);
    if(subtitle != null)
      notificationBuilder.setContentText(subtitle);

    // Build the notification and show it
    mNotifyMgr.cancel(NOTIFICATION_ID);
    mNotifyMgr.notify(NOTIFICATION_ID, notificationBuilder.build());
    notificationIsUp = true;
    Logger.d("[WearListenerService] now playing notification sent");
  }

  public void cancelNowPlaying() {
    mNotifyMgr.cancel(NOTIFICATION_ID);
    notificationIsUp = false;
  }

  public Bitmap loadBitmapFromAsset(Asset asset) {
    if (asset == null) {
      throw new IllegalArgumentException("Asset must be non-null");
    }
    ConnectionResult result =
            googleApiClient.blockingConnect(500, TimeUnit.MILLISECONDS);
    if (!result.isSuccess()) {
      return null;
    }
    // convert asset into a file descriptor and block until it's ready
    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
            googleApiClient, asset).await().getInputStream();
    googleApiClient.disconnect();

    if (assetInputStream == null) {
      Logger.d("Requested an unknown Asset.");
      return null;
    }
    // decode the stream into a bitmap
    return BitmapFactory.decodeStream(assetInputStream);
  }

  public void setNowPlayingImage(Asset image) {
    nowPlayingImage = loadBitmapFromAsset(image);
  }
}
