package com.atomjack.vcfp.activities;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.MediaOptionsDialog;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VideoControllerView;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.interfaces.GenericHandler;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.services.PlexSearchService;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.SecureRandom;

public class VideoPlayerActivity extends AppCompatActivity
        implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnPreparedListener,
        SurfaceHolder.Callback,
        VideoControllerView.MediaPlayerControl {

  public final static String ACTION_PLAY_LOCAL = "com.atomjack.vcfp.action_play_local";
  private PlexMedia media;
  private boolean resume = false;
  private String transientToken;
  private String session;

  private PlayerState currentState = PlayerState.STOPPED;

  SurfaceView videoSurface;
  MediaPlayer player;
  VideoControllerView controller;

  int screenWidth, screenHeight;

  private Handler handler;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("[VideoPlayerActivity] onCreate");
    session = VoiceControlForPlexApplication.generateRandomString();

    final View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);


    final DisplayMetrics metrics = new DisplayMetrics();
    Display display = getWindowManager().getDefaultDisplay();
    Method mGetRawH = null, mGetRawW = null;
    try {
      // For JellyBean 4.2 (API 17) and onward
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
        display.getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
      } else {
        mGetRawH = Display.class.getMethod("getRawHeight");
        mGetRawW = Display.class.getMethod("getRawWidth");

        try {
          screenWidth = (Integer) mGetRawW.invoke(display);
          screenHeight = (Integer) mGetRawH.invoke(display);
        } catch (IllegalArgumentException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } catch (NoSuchMethodException e3) {
      e3.printStackTrace();
    }


//    DisplayMetrics displaymetrics = new DisplayMetrics();
//    getWindowManager().getDefaultDisplay().getRealMetrics(displaymetrics);
//    screenWidth = displaymetrics.widthPixels;
//    screenHeight = displaymetrics.heightPixels;

    handler = new Handler();

    if(getIntent().getAction() != null && getIntent().getAction().equals(ACTION_PLAY_LOCAL)) {
      media = getIntent().getParcelableExtra(com.atomjack.shared.Intent.EXTRA_MEDIA);
      transientToken = getIntent().getStringExtra(com.atomjack.shared.Intent.EXTRA_TRANSIENT_TOKEN);
      resume = getIntent().getBooleanExtra(com.atomjack.shared.Intent.EXTRA_RESUME, false);

      setContentView(R.layout.video_player_loading);

      VoiceControlForPlexApplication.getInstance().fetchMediaThumb(media, screenWidth, screenHeight, media.art, media.getImageKey(PlexMedia.IMAGE_KEY.LOCAL_VIDEO_BACKGROUND), new BitmapHandler() {
        @Override
        public void onSuccess(Bitmap bitmap) {
          final BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
          final View loadingLayout = findViewById(R.id.videoPlayerLoadingBackground);
          handler.post(new Runnable() {
            @Override
            public void run() {
              // TODO: Force the loading image to attempt to load before starting playback?
              if(loadingLayout != null) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                  loadingLayout.setBackgroundDrawable(bitmapDrawable);
                } else {
                  loadingLayout.setBackground(bitmapDrawable);
                }
              }
            }
          });
        }
      });


      media.server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(Connection connection) {
          String url = getTranscodeUrl(media, connection, transientToken, 0);
          Logger.d("Using url %s", url);

          setContentView(R.layout.video_player);

          videoSurface = (SurfaceView) findViewById(R.id.videoSurface);
          SurfaceHolder videoHolder = videoSurface.getHolder();
          videoHolder.addCallback(VideoPlayerActivity.this);

          player = new MediaPlayer();
          controller = new VideoControllerView(VideoPlayerActivity.this);

          setControllerPoster();

          try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(VideoPlayerActivity.this, Uri.parse(url));
            player.setOnPreparedListener(VideoPlayerActivity.this);

          } catch (Exception e) {
            e.printStackTrace();
            // TODO: Handle
          }
        }

        @Override
        public void onFailure(int statusCode) {
          // TODO: Handle failure. Feedback?
        }
      });
    } else {
      finish();
    }

  }

  private void setControllerPoster() {
    VoiceControlForPlexApplication.getInstance().fetchMediaThumb(media,
            Utils.convertDpToPixel(this, getResources().getDimension(R.dimen.video_player_poster_width)),
            Utils.convertDpToPixel(this, getResources().getDimension(R.dimen.video_player_poster_height)),
            media.thumb,
            media.getImageKey(PlexMedia.IMAGE_KEY.LOCAL_VIDEO_THUMB),
            new BitmapHandler() {
      @Override
      public void onSuccess(Bitmap bitmap) {
        controller.setPoster(bitmap);
      }
    });
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if(controller != null && event.getAction() == MotionEvent.ACTION_UP) {
      if(controller.isShowing())
        controller.hide();
      else
        controller.show();
    }
    return false;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Logger.d("[VideoPlayerActivity] onNewIntent: %s", intent.getAction());
  }

  private String getTranscodeUrl(PlexMedia media, Connection connection, String transientToken, int offset) {
    return getTranscodeUrl(media, connection, transientToken, offset, false);
  }

  private String getTranscodeUrl(PlexMedia media, Connection connection, String transientToken, int offset, boolean subtitles) {
    Logger.d("getTranscodeUrl, offset: %d", offset);
    String url = connection.uri;
    url += String.format("/%s/:/transcode/universal/%s?", (media instanceof PlexVideo ? "video" : "audio"), (subtitles ? "subtitles" : "start.m3u8"));
    QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", media.key));
    qs.add("mediaIndex", "0");
    qs.add("partIndex", "0");

    qs.add("subtitles", "auto");
    qs.add("copyts", "1");
    qs.add("subtitleSize", "100");
    qs.add("Accept-Language", "en");
    qs.add("X-Plex-Client-Profile-Extra", "");
    qs.add("X-Plex-Chunked", "1");

    qs.add("protocol", "http");
    qs.add("offset", Integer.toString(offset));
    qs.add("fastSeek", "1");
    qs.add("directPlay", "0");
    qs.add("directStream", "1");
    qs.add("videoQuality", "60");
    qs.add("maxVideoBitrate", VoiceControlForPlexApplication.videoQualityOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.LOCAL_VIDEO_QUALITY_LOCAL : Preferences.LOCAL_VIDEO_QUALITY_REMOTE))[0]);
    qs.add("videoResolution", VoiceControlForPlexApplication.videoQualityOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.LOCAL_VIDEO_QUALITY_LOCAL : Preferences.LOCAL_VIDEO_QUALITY_REMOTE))[1]);
    qs.add("audioBoost", "100");
    qs.add("session", session);
    qs.add(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getInstance().prefs.getUUID());
    qs.add(PlexHeaders.XPlexProduct, VoiceControlForPlexApplication.getInstance().getString(R.string.app_name));
    qs.add(PlexHeaders.XPlexDevice, android.os.Build.MODEL);
    qs.add(PlexHeaders.XPlexPlatform, "Android");
    qs.add("protocol", "hls");

    if(transientToken != null)
      qs.add(PlexHeaders.XPlexToken, transientToken);

    qs.add(PlexHeaders.XPlexPlatformVersion, "1.0");
    try {
      qs.add(PlexHeaders.XPlexVersion, VoiceControlForPlexApplication.getInstance().getPackageManager().getPackageInfo(VoiceControlForPlexApplication.getInstance().getPackageName(), 0).versionName);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null)
      qs.add(PlexHeaders.XPlexUsername, VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
    return url + qs.toString();
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    Logger.d("[VideoPlayerActivity] onCompletion");
    finish();
  }

  @Override
  public boolean onInfo(MediaPlayer mp, int what, int extra) {
    Logger.d("[VideoPlayerActivity] onInfo: %d, %d", what, extra);
    return false;
  }

  // Implement MediaPlayer.OnPreparedListener
  @Override
  public void onPrepared(MediaPlayer mp) {
    Logger.d("[VideoPlayerActivity] onPrepared");
    currentState = PlayerState.BUFFERING;

    controller.setMediaPlayer(this);
    controller.setAnchorView((FrameLayout) findViewById(R.id.videoSurfaceContainer));


    SurfaceView videoSurface = (SurfaceView)findViewById(R.id.videoSurface);
    int videoWidth = player.getVideoWidth();
    int videoHeight = player.getVideoHeight();
    float videoProportion = (float) videoWidth / (float) videoHeight;
    float screenProportion = (float) screenWidth / (float) screenHeight;
    android.view.ViewGroup.LayoutParams lp = videoSurface.getLayoutParams();

    if (videoProportion > screenProportion) {
      lp.width = screenWidth;
      lp.height = (int) ((float) screenWidth / videoProportion);
    } else {
      lp.width = (int) (videoProportion * (float) screenHeight);
      lp.height = screenHeight;
    }
    Logger.d("Setting width/height to %d/%d", lp.width, lp.height);
    Logger.d("Setting screen width/height to %d/%d", screenWidth, screenHeight);
    videoSurface.setLayoutParams(lp);

    if(resume && media.viewOffset != null) {
      Logger.d("Seeking to %d before playing", Integer.parseInt(media.viewOffset) / 1000);
      player.seekTo(Integer.parseInt(media.viewOffset));
    }
    player.start();
    player.setOnCompletionListener(this);
    player.setOnInfoListener(this);
    videoIsPlayingCheck.run();
  }
  // End MediaPlayer.OnPreparedListener

  @Override
  protected void onStop() {
    super.onStop();
    player.stop();
    player.release();
    Logger.d("Stopping transcoder");
    PlexHttpClient.stopTranscoder(media.server, session, "video");
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    player.setDisplay(holder);
    player.prepareAsync();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {

  }

  // Implement VideoMediaController.MediaPlayerControl
  @Override
  public void start() {
    currentState = PlayerState.PLAYING;
    player.start();
  }

  @Override
  public void pause() {
    currentState = PlayerState.PAUSED;
    player.pause();
  }

  @Override
  public int getDuration() {
    try {
      return player.getDuration();
    } catch (Exception e) {}
    return 0;
  }

  @Override
  public int getCurrentPosition() {
    try {
      return player.getCurrentPosition();
    } catch (Exception e) {}
    return 0;
  }

  @Override
  public void seekTo(int pos) {
    player.seekTo(pos);
  }

  @Override
  public boolean isPlaying() {
    try {
      return player.isPlaying();
    } catch (Exception e) {}
    return false;
  }

  @Override
  public int getBufferPercentage() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return true;
  }

  @Override
  public boolean canSeekBackward() {
    return true;
  }

  @Override
  public boolean canSeekForward() {
    return true;
  }

  @Override
  public boolean isFullScreen() {
    return false;
  }

  @Override
  public void toggleFullScreen() {

  }

  @Override
  public void doMic() {
    if(media.server != null) {
      pause();

      android.content.Intent serviceIntent = new android.content.Intent(this, PlexSearchService.class);

      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, VoiceControlForPlexApplication.gsonWrite.toJson(media.server));
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, VoiceControlForPlexApplication.gsonWrite.toJson(PlexClient.getLocalPlaybackClient()));
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, resume);
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_FROM_MIC, true);

      SecureRandom random = new SecureRandom();
      serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
      PendingIntent resultsPendingIntent = PendingIntent.getService(this, 0, serviceIntent, PendingIntent.FLAG_ONE_SHOT);

      android.content.Intent listenerIntent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
      listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.voice_prompt));

      startActivity(listenerIntent);
    }
  }

  @Override
  public void doMediaOptions() {
    MediaOptionsDialog mediaOptionsDialog = new MediaOptionsDialog(this, media, PlexClient.getLocalPlaybackClient());
    mediaOptionsDialog.setLocalStreamChangeListener(new MediaOptionsDialog.LocalStreamChangeListener() {
      @Override
      public void setStream(final Stream stream) {
        Logger.d("Setting stream %s", stream.getTitle());
        PlexHttpClient.setStreamActive(media, stream, new Runnable() {
          @Override
          public void run() {
            media.server.findServerConnection(new ActiveConnectionHandler() {
              @Override
              public void onSuccess(final Connection connection) {
                media.setActiveStream(stream);
                handler.removeCallbacks(playerProgressRunnable);
                player.stop();
                PlexHttpClient.stopTranscoder(media.server, session, "video", new GenericHandler() {
                  @Override
                  public void onSuccess() {
                    String url = getTranscodeUrl(media, connection, transientToken, 0);
                    try {
                      player.reset();
                      player.setDataSource(VideoPlayerActivity.this, Uri.parse(url));
                      player.setOnPreparedListener(VideoPlayerActivity.this);
                      player.prepareAsync();
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  }

                  @Override
                  public void onFailure() {

                  }
                });
              }

              @Override
              public void onFailure(int statusCode) {

              }
            });
          }
        });
      }
    });
    mediaOptionsDialog.show();
  }

  @Override
  public void onPosterContainerTouch(View v, MotionEvent event) {
    onTouchEvent(event);
  }
  // End VideoMediaController.MediaPlayerControl

  private Runnable videoIsPlayingCheck = new Runnable() {
    @Override
    public void run() {
      if(getDuration() > 0) {
        Logger.d("Video is playing");
        currentState = PlayerState.PLAYING;
        handler.postDelayed(playerProgressRunnable, 1000);
      } else {
        handler.postDelayed(videoIsPlayingCheck, 100);
      }
    }
  };

  private Runnable playerProgressRunnable = new Runnable() {
    @Override
    public void run() {
      PlexHttpClient.reportProgressToServer(media, getCurrentPosition(), currentState);
      if(getCurrentPosition() == 0) {
        Logger.d("[VideoPlayerActivity] setting viewoffset to 0!");
      }
      media.viewOffset = Integer.toString(getCurrentPosition());
      handler.postDelayed(playerProgressRunnable, 1000);
    }
  };

  @Override
  public void onBackPressed() {
    handler.removeCallbacks(playerProgressRunnable);
    PlexHttpClient.reportProgressToServer(media, getCurrentPosition(), PlayerState.STOPPED);
    player.stop();
    super.onBackPressed();
  }
}
