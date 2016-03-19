package com.atomjack.vcfp.services;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.MusicServiceListener;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.net.PlexHttpClient;

public class LocalMusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {
  public static final int MSG_TIME_UPDATE = 0;

  private MediaPlayer player;
  private MediaContainer mediaContainer;
  private PlexTrack track;
  private int currentSongIdx;
  private PlayerState currentState = PlayerState.STOPPED;
  private Handler handler;

  private final IBinder musicBind = new MusicBinder();
  private MusicServiceListener musicServiceListener;

  @Override
  public void onCreate() {
    super.onCreate();
    Logger.d("[LocalMusicService] onCreate");
    player = new MediaPlayer();
    currentSongIdx = 0;
    handler = new Handler();
    initMusicPlayer();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Logger.d("[LocalMusicService] onStartCommand: %s", intent.getAction());

    if(intent.getAction() != null) {
      if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PLAY) || intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PAUSE)) {
        doPlayPause();
      } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_STOP)) {
        doStop();
      } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PREVIOUS)) {
        doPrevious();
      } else if(intent.getAction().equals(com.atomjack.shared.Intent.ACTION_NEXT)) {
        doNext();
      } else if(intent.getAction().equals(com.atomjack.shared.Intent.ACTION_DISCONNECT)) {
        doStop();
      }
    }
    return Service.START_NOT_STICKY;

  }

  private void initMusicPlayer() {
    player.setWakeMode(getApplicationContext(),
            PowerManager.PARTIAL_WAKE_LOCK);
    player.setAudioStreamType(AudioManager.STREAM_MUSIC);

    player.setOnPreparedListener(this);
    player.setOnCompletionListener(this);
    player.setOnErrorListener(this);
  }

  public void setMediaContainer(MediaContainer container) {
    mediaContainer = container;
  }

  public void setTrack(PlexTrack t) {
    mediaContainer = null;
    track = t;
  }

  public class MusicBinder extends Binder {
    public LocalMusicService getService() {
      return LocalMusicService.this;
    }

    public void setListener(MusicServiceListener listener) {
      musicServiceListener = listener;
    }
  }

  public PlayerState getCurrentState() {
    return currentState;
  }

  public void playSong() {
    player.reset();
    if(mediaContainer != null)
      track = mediaContainer.tracks.get(currentSongIdx);
    Logger.d("Playing Track: %s", track.getTitle());
    if(track != null) {
      VoiceControlForPlexApplication.getInstance().setNotification(PlexClient.getLocalPlaybackClient(), currentState, track);
      track.server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(Connection connection) {
          String url = String.format("%s%s", connection.uri, getTrackUrl(track));
          try {
            player.setDataSource(getApplicationContext(), Uri.parse(url));
            player.prepareAsync();
            musicServiceListener.onTrackChange(track);
            VoiceControlForPlexApplication.getInstance().setNotification(PlexClient.getLocalPlaybackClient(), currentState, track);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onFailure(int statusCode) {

        }
      });

    }
  }

  public void reset() {
    currentSongIdx = 0;
  }

  public void doPrevious() {
    if(currentSongIdx > 0) {
      player.stop();
      currentSongIdx--;
      playSong();
    }
  }

  public void doNext() {
    if(currentSongIdx+1 < mediaContainer.tracks.size()) {
      player.stop();
      currentSongIdx++;
      playSong();
    }
  }

  public void doStop() {
    player.stop();
    handler.removeCallbacks(playerProgressUpdater);
    PlexHttpClient.reportProgressToServer(track, player.getCurrentPosition(), PlayerState.STOPPED);
    VoiceControlForPlexApplication.getInstance().cancelNotification();
    musicServiceListener.onFinished();
    stopSelf();
  }

  public void seek(int time) {
    player.seekTo(time);
  }

  public void doPlay() {
    Logger.d("[LocalMusicService] doPlay");
    player.start();
    currentState = PlayerState.PLAYING;
  }

  public void doPause() {
    player.pause();
    currentState = PlayerState.PAUSED;
  }

  public void doPlayPause() {
    if(currentState == PlayerState.PLAYING) {
      player.pause();
      currentState = PlayerState.PAUSED;
    } else {
      player.start();
      currentState = PlayerState.PLAYING;
    }
    VoiceControlForPlexApplication.getInstance().setNotification(PlexClient.getLocalPlaybackClient(), currentState, track);
  }

  public boolean isPlaying() {
    return player.isPlaying();
  }

  public void setSong(int idx) {
    currentSongIdx = idx;
  }

  private String getTrackUrl(PlexTrack track) {
    // /library/parts/83301/file.mp3?X-Plex-Token=xxxxxxxxxxxxxxxxxxxx
    String key = track.getPart().key;
    if(track.server.accessToken != null)
      key += String.format("?%s=%s", PlexHeaders.XPlexToken, track.server.accessToken);
    return key;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    Logger.d("[LocalMusicService] onBind: %s", intent.getAction());
    return musicBind;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    player.stop();
    player.release();
    return false;
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    currentSongIdx++;
    Logger.d("[LocalMusicService] onCompletion, current: %d, size: %d", currentSongIdx, mediaContainer.tracks.size());
    if(currentSongIdx == mediaContainer.tracks.size()) {
      // Last song
      musicServiceListener.onFinished();
    } else {
      playSong();
    }
  }

  @Override
  public boolean onError(MediaPlayer mp, int what, int extra) {
    return false;
  }

  @Override
  public void onPrepared(MediaPlayer mp) {
    Logger.d("[LocalMusicService] onPrepared");
    currentState = PlayerState.BUFFERING;
    player.start();
    musicIsPlayingCheck.run();
  }

  private Runnable playerProgressUpdater = new Runnable() {
    @Override
    public void run() {
      PlexHttpClient.reportProgressToServer(track, player.getCurrentPosition(), currentState);
      musicServiceListener.onTimeUpdate(currentState, player.getCurrentPosition());
      handler.postDelayed(this, 1000);
    }
  };

  private Runnable musicIsPlayingCheck = new Runnable() {
    @Override
    public void run() {
      if(player.getDuration() > 0) {
        Logger.d("Audio is playing");
        currentState = PlayerState.PLAYING;
        handler.postDelayed(playerProgressUpdater, 1000);
      } else {
        handler.postDelayed(musicIsPlayingCheck, 100);
      }
    }
  };


  public PlexTrack getTrack() {
    return track;
  }

  public MediaContainer getMediaContainer() {
    return mediaContainer;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Logger.d("[LocalMusicService] onDestroy");
  }
}
