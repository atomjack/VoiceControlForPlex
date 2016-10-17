package com.atomjack.vcfp.services;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import com.atomjack.shared.NewLogger;
import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.FetchMediaImageTask;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.interfaces.MusicServiceListener;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.receivers.RemoteControlReceiver;

import java.util.ArrayList;

public class LocalMusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {
  private NewLogger logger;

  private MediaPlayer player;
  private PlexTrack track;
  private ArrayList<PlexTrack> playlist;
  private int currentSongIdx;
  private PlayerState currentState = PlayerState.STOPPED;
  private Handler handler;

  private final IBinder musicBind = new MusicBinder();
  private MusicServiceListener musicServiceListener;

  AudioManager audioManager;
  ComponentName remoteControlReceiver;
  private MediaSessionCompat mediaSession;

  private long headsetDownTime = 0;
  private long headsetUpTime = 0;

  @Override
  @SuppressWarnings("deprecation")
  public void onCreate() {
    super.onCreate();
    logger = new NewLogger(this);
    logger.d("onCreate");
    player = new MediaPlayer();
    currentSongIdx = 0;
    handler = new Handler();
    initMusicPlayer();


    remoteControlReceiver = new ComponentName(getPackageName(), RemoteControlReceiver.class.getName());

    mediaSession = new MediaSessionCompat(this, "VCFPRemoteControlReceiver", remoteControlReceiver, null);
    mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
    PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_STOP)
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1, SystemClock.elapsedRealtime())
            .build();
    mediaSession.setPlaybackState(state);

    Intent intent = new Intent(this, RemoteControlReceiver.class);
    PendingIntent pintent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    mediaSession.setMediaButtonReceiver(pintent);
    mediaSession.setActive(false);

    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
      @Override
      public void onAudioFocusChange(int focusChange) {
        logger.d("audio focus changed");
      }
    }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
      audioManager.registerMediaButtonEventReceiver(remoteControlReceiver);
  }

  private Runnable notPurchasedDisconnectTimer = new Runnable() {
    @Override
    public void run() {
      logger.d("auto stopping");
      doStop();
    }
  };

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
//    logger.d("onStartCommand: %s", intent.getAction());

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
      } else if(intent.getAction().equals(com.atomjack.shared.Intent.ACTION_MEDIA_BUTTON)) {
        handleMediaButton((KeyEvent)intent.getParcelableExtra(com.atomjack.shared.Intent.KEY_EVENT));
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

  @SuppressWarnings("unchecked")
  public void setPlaylist(ArrayList<? extends PlexMedia> playlist) {
    this.playlist = (ArrayList<PlexTrack>)playlist;
  }

  public void setTrack(PlexTrack t) {
    playlist = null;
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
    if(playlist != null)
      track = playlist.get(currentSongIdx);
    logger.d("Playing Track: %s", track.getTitle());
    if(track != null) {
      track.server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(Connection connection) {
          String url = String.format("%s%s", connection.uri, getTrackUrl(track));
          try {
            player.setDataSource(getApplicationContext(), Uri.parse(url));
            player.prepareAsync();
            musicServiceListener.onTrackChange(track);
            VoiceControlForPlexApplication.getInstance().setNotification(PlexClient.getLocalPlaybackClient(), currentState, track, playlist, mediaSession);

            new FetchMediaImageTask(track, 500, 500, track.getNotificationThumb(PlexMedia.IMAGE_KEY.WEAR_BACKGROUND), track.getImageKey(PlexMedia.IMAGE_KEY.WEAR_BACKGROUND), new BitmapHandler() {
              @Override
              public void onSuccess(Bitmap bitmap) {
                  mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                          .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                          .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                          .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getAlbum())
                          .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                          .build()
                  );
                  mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
                  handler.post(new Runnable() {
                    @Override
                    public void run() {
                    registerMediaSessionCallback();
                    mediaSession.setActive(true);
                    }
                  });
              }
            }).execute();
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
    if(currentSongIdx+1 < playlist.size()) {
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
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
      mediaSession.setActive(false);
    handler.removeCallbacks(notPurchasedDisconnectTimer);
    stopSelf();
  }

  public void seek(int time) {
    player.seekTo(time);
  }

  public void doPlay() {
    logger.d("doPlay");
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
    VoiceControlForPlexApplication.getInstance().setNotification(PlexClient.getLocalPlaybackClient(), currentState, track, playlist, mediaSession);
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
    logger.d("onBind: %s", intent.getAction());
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
    logger.d("onCompletion, current: %d, size: %d", currentSongIdx, playlist.size());
    if(currentSongIdx == playlist.size()) {
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
    logger.d("onPrepared");
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
        logger.d("Audio is playing");
        currentState = PlayerState.PLAYING;
        VoiceControlForPlexApplication.getInstance().setNotification(PlexClient.getLocalPlaybackClient(), currentState, track, playlist, mediaSession);
        handler.postDelayed(playerProgressUpdater, 1000);
        if(!VoiceControlForPlexApplication.getInstance().hasLocalmedia()) {
          handler.removeCallbacks(notPurchasedDisconnectTimer);
          handler.postDelayed(notPurchasedDisconnectTimer, 1000*60); // disconnect in 60 seconds
        }
      } else {
        handler.postDelayed(musicIsPlayingCheck, 100);
      }
    }
  };


  public PlexTrack getTrack() {
    return track;
  }

  public ArrayList<PlexTrack> getPlaylist() {
    return playlist;
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onDestroy() {
    super.onDestroy();
    logger.d("onDestroy");
    mediaSession.release();
    if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      audioManager.unregisterMediaButtonEventReceiver(remoteControlReceiver);
    }
  }

  // Do play/pause 501ms after headset button is clicked. If it is doubleclicked, this task will get canceled, and doNext() will be called instead
  private Runnable handleSingleClick = new Runnable() {
    @Override
    public void run() {
      logger.d("Single click");
      doPlayPause();
    }
  };

  public void handleMediaButton(KeyEvent event) {
    switch (event.getKeyCode()) {
      /*
       * one click => play/pause
       * double click => next
       */
      case KeyEvent.KEYCODE_HEADSETHOOK:
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
        long time = SystemClock.uptimeMillis();
        switch (event.getAction()) {
          case KeyEvent.ACTION_DOWN:
            if (event.getRepeatCount() > 0)
              break;
            headsetDownTime = time;
            break;
          case KeyEvent.ACTION_UP:
            if (time - headsetUpTime <= 250) {
              handler.removeCallbacks(handleSingleClick);
              doNext();
            } else {
              handler.postDelayed(handleSingleClick, 251);
            }
            headsetUpTime = time;
            break;
        }
        break;
      case KeyEvent.KEYCODE_MEDIA_PLAY:
        doPlay();
        break;
      case KeyEvent.KEYCODE_MEDIA_PAUSE:
        doPause();
        break;
      case KeyEvent.KEYCODE_MEDIA_STOP:
        doStop();
        break;
      case KeyEvent.KEYCODE_MEDIA_NEXT:
        doNext();
        break;
      case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
        doPrevious();
        break;
    }
  }

  private void registerMediaSessionCallback() {
    mediaSession.setCallback(new MediaSessionCompat.Callback() {
      @Override
      public void onPlay() {
        doPlay();
      }

      @Override
      public void onPause() {
        doPause();
      }

      @Override
      public void onSkipToNext() {
        doNext();
      }

      @Override
      public void onSkipToPrevious() {
        doPrevious();
      }

      @Override
      public void onStop() {
        doStop();
      }
    });
  }

}
