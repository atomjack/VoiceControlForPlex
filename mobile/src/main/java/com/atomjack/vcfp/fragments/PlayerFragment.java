package com.atomjack.vcfp.fragments;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atomjack.shared.Intent;
import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.MediaOptionsDialog;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.activities.MainActivity;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.ActivityListener;
import com.atomjack.vcfp.interfaces.InputStreamHandler;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.services.PlexSearchService;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;
import cz.fhucho.android.util.SimpleDiskCache;

public abstract class PlayerFragment extends Fragment
        implements SeekBar.OnSeekBarChangeListener {
  protected PlexMedia nowPlayingMedia;
  protected MediaContainer mediaContainer;
  protected int currentMediaIndex = 0;
  protected PlexClient client;

  private View mainView;

  protected Feedback feedback;

  protected PlayerState currentState = PlayerState.STOPPED;

  protected int position = -1;

  SimpleDiskCache simpleDiskCache;

  // UI Elements
  protected boolean resumePlayback;
//  @Bind(R.id.playButton)
  protected ImageButton playButton;
//  @Bind(R.id.pauseButton)
  protected ImageButton pauseButton;
//  @Bind(R.id.playPauseSpinner)
  protected ProgressBar playPauseSpinner;
  protected boolean isSeeking = false;
  protected SeekBar seekBar;
  protected TextView currentTimeDisplay;
  protected TextView durationDisplay;
  protected ImageView nowPlayingPoster;

  protected boolean fromWear = false;

  protected GestureDetectorCompat mDetector;

  protected MediaOptionsDialog mediaOptionsDialog;

  private int layout = -1;

  private LayoutInflater inflater;

  protected ActivityListener activityListener;

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    if(savedInstanceState != null) {
      Logger.d("[PlayerFragment] onSavedInstanceState is not null");
      nowPlayingMedia = savedInstanceState.getParcelable(Intent.EXTRA_MEDIA);
      mediaContainer = savedInstanceState.getParcelable(Intent.EXTRA_ALBUM);
      fromWear = savedInstanceState.getBoolean(WearConstants.FROM_WEAR, false);
      layout = savedInstanceState.getInt(Intent.EXTRA_LAYOUT);
      client = savedInstanceState.getParcelable(Intent.EXTRA_CLIENT);
      currentState = (PlayerState) savedInstanceState.getSerializable(Intent.EXTRA_CURRENT_STATE);
      Logger.d("got current state: %s", currentState);
    }
    Logger.d("[PlayerFragment] layout: %d", layout);

    if(layout == -1) { // Layout can't be found, so alert activity something went wrong so it closes fragment out
      mainView = inflater.inflate(R.layout.player_fragment, container, false);
      activityListener.onLayoutNotFound();
    } else {
      mainView = inflater.inflate(layout, container, false);

      ButterKnife.bind(getActivity(), mainView);

      this.inflater = inflater;

      showNowPlaying();
      setState(currentState); // Call this right away so that the play/pause spinner gets changed to the appropriate button, when orientation is changing while playing or paused
      seekBar = (SeekBar) mainView.findViewById(R.id.seekBar);
      seekBar.setOnSeekBarChangeListener(this);
      seekBar.setMax(nowPlayingMedia.duration / 1000);
      if(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false))
        seekBar.setProgress(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
      else
        seekBar.setProgress(0);

      setCurrentTimeDisplay(getOffset(nowPlayingMedia));
      durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
    }
    return mainView;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(Intent.EXTRA_MEDIA, nowPlayingMedia);
    outState.putParcelable(Intent.EXTRA_ALBUM, mediaContainer);
    outState.putInt(Intent.EXTRA_LAYOUT, layout);
    outState.putParcelable(Intent.EXTRA_CLIENT, client);
    outState.putSerializable(Intent.EXTRA_CURRENT_STATE, currentState);
  }


  public PlayerFragment() {
    simpleDiskCache = VoiceControlForPlexApplication.getInstance().mSimpleDiskCache;
  }

  public void init(int layout, PlexClient client, PlexMedia media, MediaContainer mc, boolean fromWear) {
    this.layout = layout;
    this.client = client;
    nowPlayingMedia = media;
    mediaContainer = mc;
    this.fromWear = fromWear;
    currentMediaIndex = 0;
  }

  public void mediaChanged(PlexMedia media) {
    nowPlayingMedia = media;
    if(mainView != null) { // Can't do anything with UI elements until mainView is defined and set
      showNowPlaying();
      setCurrentTimeDisplay(getOffset(nowPlayingMedia));
      seekBar.setMax(nowPlayingMedia.duration / 1000);
      seekBar.setProgress(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
      durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
    }
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    feedback = ((MainActivity)context).feedback;
    try {
      activityListener = (ActivityListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString()
              + " must implement ActivityListener");
    }
  }

  public PlexMedia getNowPlayingMedia() {
    return nowPlayingMedia;
  }

  public void subtitlesOn() {
    if(nowPlayingMedia.getStreams(Stream.SUBTITLE).size() > 0) {
      client.setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
      nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
      feedback.m(String.format(getString(R.string.subtitle_active), nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1).getTitle()));
    }
  }

  public void subtitlesOff() {
    if(nowPlayingMedia.getStreams(Stream.SUBTITLE).size() > 0) {
      client.setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
      nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
      feedback.m(R.string.subtitles_off);
    }
  }

  public void cycleStreams(final int streamType) {
    Stream newStream = nowPlayingMedia.getNextStream(streamType);
    client.setStream(newStream);
    nowPlayingMedia.setActiveStream(newStream);
    if(streamType == Stream.SUBTITLE) {
      if(newStream.id.equals("0")) {
        feedback.m(R.string.subtitles_off);
      } else {
        feedback.m(String.format(getString(R.string.subtitle_active), newStream.getTitle()));
      }
    } else {
      feedback.m(String.format(getString(R.string.audio_track_active), newStream.getTitle()));
    }
  }

  public void showNowPlaying() {
    if(mainView == null)
      return;
    if (nowPlayingMedia instanceof PlexVideo) {
      PlexVideo video = (PlexVideo)nowPlayingMedia;
      if(video.isMovie() || video.isClip()) {
        TextView title = (TextView) mainView.findViewById(R.id.nowPlayingTitle);
        title.setText(video.title);
        TextView genre = (TextView) mainView.findViewById(R.id.nowPlayingGenre);
        genre.setText(video.getGenres());
        TextView year = (TextView) mainView.findViewById(R.id.nowPlayingYear);
        year.setText(video.year);
        TextView duration = (TextView) mainView.findViewById(R.id.nowPlayingDuration);
        duration.setText(video.getDuration());
//        TextView summary = (TextView) mainView.findViewById(R.id.nowPlayingSummary);
//        if(summary != null) {
//          summary.setText(video.summary);
//        }
      } else {
        TextView showTitle = (TextView)mainView.findViewById(R.id.nowPlayingShowTitle);
        showTitle.setText(video.grandparentTitle);
        TextView episodeTitle = (TextView)mainView.findViewById(R.id.nowPlayingEpisodeTitle);
        episodeTitle.setText(String.format("%s (s%02de%02d)", video.title, Integer.parseInt(video.parentIndex), Integer.parseInt(video.index)));
        TextView year = (TextView)mainView.findViewById(R.id.nowPlayingYear);
        year.setText(video.year);
        TextView duration = (TextView)mainView.findViewById(R.id.nowPlayingDuration);

        duration.setText(video.getDuration());
//        TextView summary = (TextView)mainView.findViewById(R.id.nowPlayingSummary);
//        if(summary != null)
//          summary.setText(video.summary);

      }

    } else if (nowPlayingMedia instanceof PlexTrack) {
      PlexTrack track = (PlexTrack)nowPlayingMedia;

      TextView artist = (TextView)mainView.findViewById(R.id.nowPlayingArtist);
      Logger.d("Setting artist to %s", track.grandparentTitle);
      artist.setText(track.grandparentTitle);
      TextView album = (TextView)mainView.findViewById(R.id.nowPlayingAlbum);
      album.setText(track.parentTitle);
      TextView title = (TextView)mainView.findViewById(R.id.nowPlayingTitle);
      title.setText(track.title);
    }
    TextView nowPlayingOnClient = (TextView)mainView.findViewById(R.id.nowPlayingOnClient);
    nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

    // Hide stream options on chromecast, for now
    if (mainView.findViewById(R.id.mediaOptionsButton) != null && nowPlayingMedia.getStreams(Stream.SUBTITLE).size() == 0 && nowPlayingMedia.getStreams(Stream.AUDIO).size() == 0) {
      mainView.findViewById(R.id.mediaOptionsButton).setVisibility(View.GONE);
    }

    Logger.d("[PlayerFragment] Setting thumb in showNowPlaying");
    attachUIElements();

    final FrameLayout nowPlayingPosterContainer = (FrameLayout)mainView.findViewById(R.id.nowPlayingPosterContainer);
    Logger.d("nowPlayingPosterContainer: %s", nowPlayingPosterContainer);
    if(nowPlayingPosterContainer != null) {
      ViewTreeObserver vto = nowPlayingPosterContainer.getViewTreeObserver();
      vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            nowPlayingPosterContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          int height = nowPlayingPosterContainer.getMeasuredHeight();
          int width = nowPlayingPosterContainer.getMeasuredWidth();
          Logger.d("Found dimensions: %d/%d", width, height);
          setThumb(width, height);
        }
      });
    }
  }

  @Override
  public void onResume() {
    Logger.d("[PlayerFragment] onResume");

    super.onResume();
  }

  private void setThumb(final byte[] bytes) {
    Logger.d("Setting thumb width %d bytes", bytes.length);

    if(getActivity() != null) {
      getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Bitmap posterBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
          nowPlayingPoster.setImageBitmap(posterBitmap);
        }
      });
    }
  }

  protected void setThumb(int maxWidth, int maxHeight) {
    String thumb = nowPlayingMedia.thumb;

    Logger.d("setThumb: %s", thumb);
    if(nowPlayingMedia instanceof PlexVideo) {
      PlexVideo video = (PlexVideo)nowPlayingMedia;
      thumb = video.isMovie() || video.isClip() ? video.thumb : video.grandparentThumb;
      Logger.d("orientation: %s, type: %s", getOrientation(), video.type);
      if(video.isClip()) {

      }

      if(getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
        thumb = video.art;
      }
    } else if(nowPlayingMedia instanceof PlexTrack) {
      PlexTrack track = (PlexTrack)nowPlayingMedia;
      if(getOrientation() == Configuration.ORIENTATION_LANDSCAPE)
        thumb = track.art;
      else
        thumb = track.thumb;
    }

    if(thumb != null && thumb.equals("")) {
      thumb = null;
    }
    Logger.d("thumb: %s", thumb);

    SimpleDiskCache.InputStreamEntry thumbEntry = null;
    try {
      thumbEntry = simpleDiskCache.getInputStream(nowPlayingMedia.getCacheKey(thumb != null ? thumb : nowPlayingMedia.key));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    if (thumbEntry != null) {
      Logger.d("Using cached thumb: %s", nowPlayingMedia.getCacheKey(thumb));
      try {
        setThumb(IOUtils.toByteArray(thumbEntry.getInputStream()));
      } catch (Exception e) { e.printStackTrace(); }
    } else {
      Logger.d("Downloading thumb");
      getThumb(maxWidth, maxHeight, thumb, nowPlayingMedia);
    }
  }

  private void getThumb(final int maxWidth, final int maxHeight, final String thumb, final PlexMedia media) {
    if(thumb == null) {
      InputStream is = getResources().openRawResource(+ R.drawable.ic_launcher);
      try {
        InputStream iss = new ByteArrayInputStream(IOUtils.toByteArray(is));
        iss.reset();
        simpleDiskCache.put(media.getCacheKey(media.key), iss);
        setThumb(IOUtils.toByteArray(iss));
      } catch (IOException e) {
        Logger.d("Exception getting/saving thumb");
        e.printStackTrace();
      }
    } else {
      media.server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(Connection connection) {
          String path = String.format("/photo/:/transcode?width=%d&height=%d&url=%s", maxWidth, maxHeight, Uri.encode(String.format("http://127.0.0.1:32400%s", thumb)));
          String url = media.server.buildURL(path);
          Logger.d("thumb url: %s", url);

          PlexHttpClient.getThumb(url, new InputStreamHandler() {
            @Override
            public void onSuccess(InputStream is) {
              try {
                simpleDiskCache.put(media.getCacheKey(thumb), is);
                setThumb(maxWidth, maxHeight);
              } catch (Exception e) {
                Logger.d("Exception getting/saving thumb");
                e.printStackTrace();
              }
            }
          });
        }

        @Override
        public void onFailure(int statusCode) {

        }
      });
    }
  }

  private void attachUIElements() {
    nowPlayingPoster = (ImageView) mainView.findViewById(R.id.nowPlayingPoster);

    ImageButton rewindButton = (ImageButton)mainView.findViewById(R.id.rewindButton);
    if(rewindButton != null)
      rewindButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doRewind();
      }
    });

    ImageButton forwardButton = (ImageButton)mainView.findViewById(R.id.forwardButton);
    if(forwardButton != null)
      forwardButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doForward();
      }
    });

    ImageButton previousButton = (ImageButton)mainView.findViewById(R.id.previousButton);
    if(previousButton != null) {
      if(mediaContainer.tracks.size() == 1 || mediaContainer.videos.size() == 1) {
        previousButton.setVisibility(View.GONE);
      } else {
        previousButton.setAlpha(1.0f);
        previousButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            doPrevious();
          }
        });
      }
    }

    playButton = (ImageButton)mainView.findViewById(R.id.playButton);
    pauseButton = (ImageButton)mainView.findViewById(R.id.pauseButton);
    playPauseSpinner = (ProgressBar)mainView.findViewById(R.id.playPauseSpinner);

    ImageButton nextButton = (ImageButton)mainView.findViewById(R.id.nextButton);
    if(nextButton != null) {
      if(mediaContainer.tracks.size() == 1 || mediaContainer.videos.size() == 1) {
        nextButton.setVisibility(View.GONE);
      } else {
        nextButton.setAlpha(1.0f);
        nextButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            doNext();
          }
        });
      }
    }

    List<? extends PlexMedia> list = null;
    if(mediaContainer.videos.size() > 0) {
      list = mediaContainer.videos;
    } else if(mediaContainer.tracks.size() > 0) {
      list = mediaContainer.tracks;
    }
    if(list != null) {
      int index = 0;
      for(PlexMedia m : list) {
        if(m.key.equals(nowPlayingMedia.key))
          break;
        index++;
      }
      Logger.d("Index: %d", index);
      if(index == 0)
        previousButton.setAlpha(0.4f);
      else if(index+1 == list.size())
        nextButton.setAlpha(0.4f);
    }

    ImageButton mediaOptionsButton = (ImageButton)mainView.findViewById(R.id.mediaOptionsButton);
    if(mediaOptionsButton != null) {
      mediaOptionsButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          doMediaOptions();
        }
      });
    }

    ImageButton micButton = (ImageButton)mainView.findViewById(R.id.micButton);
    if(micButton != null) {
      micButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          doMic();
        }
      });
    }

    ImageButton stopButton = (ImageButton)mainView.findViewById(R.id.stopButton);
    stopButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doStop();
      }
    });

    currentTimeDisplay = (TextView)mainView.findViewById(R.id.currentTimeView);
    durationDisplay = (TextView)mainView.findViewById(R.id.durationView);

    mDetector = new GestureDetectorCompat(getActivity(), new TouchGestureListener());
    LinearLayout target = (LinearLayout)mainView.findViewById(R.id.nowPlayingTapTarget);
    if(target != null) {
      target.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
          return mDetector.onTouchEvent(event);
        }
      });
    }
  }


  class TouchGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      Logger.d("Single tap.");
      if(currentState == PlayerState.PLAYING)
        doPause();
      else
        doPlay();
      return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      float SWIPE_SPEED_THRESHOLD = 2000;

      try {
        float diffY = e2.getY() - e1.getY();
        float diffX = e2.getX() - e1.getX();
        if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(velocityX) >= SWIPE_SPEED_THRESHOLD) {

          if (diffX > 0) {
            Logger.d("Doing forward via fling right");
            doForward();
          } else {
            Logger.d("Doing back via fling left");
            doRewind();
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      return true;
    }
  }

  protected int getOrientation() {

    try {
      return getResources().getConfiguration().orientation;
    } catch (Exception e) {}
    return Configuration.ORIENTATION_PORTRAIT;
  }

  // The follow methods are defined in the PlexPlayerFragment and CastPlayerFragment subclasses
  protected abstract void doRewind();
  protected abstract void doForward();
  @OnClick(R.id.playButton)
  protected abstract void doPlay();
  @OnClick(R.id.pauseButton)
  protected abstract void doPause();
  protected abstract void doStop();
  protected abstract void doNext();
  protected abstract void doPrevious();

  protected void doMic() {
    if(nowPlayingMedia.server != null) {
      android.content.Intent serviceIntent = new android.content.Intent(getActivity(), PlexSearchService.class);

      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, VoiceControlForPlexApplication.gsonWrite.toJson(nowPlayingMedia.server));
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, VoiceControlForPlexApplication.gsonWrite.toJson(client));
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, resumePlayback);
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_FROM_MIC, true);

      SecureRandom random = new SecureRandom();
      serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
      PendingIntent resultsPendingIntent = PendingIntent.getService(getActivity(), 0, serviceIntent, PendingIntent.FLAG_ONE_SHOT);

      android.content.Intent listenerIntent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
      listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
      listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.voice_prompt));

      startActivity(listenerIntent);
    }
  }


  protected void setStream(Stream stream) {
    client.setStream(stream);
  }

  protected void doMediaOptions() {
    Logger.d("[PlayerFragment] doMediaOptions!!!");

    if(nowPlayingMedia == null) {
      return;
    }
    mediaOptionsDialog = new MediaOptionsDialog(getActivity(), nowPlayingMedia, client);
    mediaOptionsDialog.show();
  }

  protected void setCurrentTimeDisplay(long seconds) {
    currentTimeDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(seconds));
  }

  protected int getOffset(PlexMedia media) {
    Logger.d("getting offset, mediaoffset: %s", media.viewOffset);
    if((VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false) || resumePlayback) && media.viewOffset != null)
      return Integer.parseInt(media.viewOffset) / 1000;
    else
      return 0;
  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    setCurrentTimeDisplay(progress);
  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    isSeeking = true;
  }

  public void setState(PlayerState newState) {
    currentState = newState;
    if(playPauseSpinner != null && playButton != null && pauseButton != null) {
      Logger.d("[PlayerFragment] setState: %s", newState);
      playPauseSpinner.setVisibility(View.INVISIBLE);
      playButton.setVisibility(View.INVISIBLE);
      pauseButton.setVisibility(View.INVISIBLE);
      if (currentState == PlayerState.PAUSED) {
        playButton.setVisibility(View.VISIBLE);
      } else if (currentState == PlayerState.PLAYING) {
        pauseButton.setVisibility(View.VISIBLE);
      } else if (currentState == PlayerState.BUFFERING) {
        playPauseSpinner.setVisibility(View.VISIBLE);
      }
    }
  }

  public void setPosition(int position) {
    if(!isSeeking) {
//      if(position != this.position)
//        Logger.d("[PlayerFragment] setting position to %d", position);
      this.position = position;
      if (seekBar != null)
        seekBar.setProgress(position);
      else
        Logger.d("Seekbar is null");
      if (currentTimeDisplay != null)
        setCurrentTimeDisplay(position);
    }
  }
}
