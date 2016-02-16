package com.atomjack.vcfp.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.atomjack.shared.Intent;
import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.activities.NewMainActivity;
import com.atomjack.vcfp.adapters.StreamAdapter;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.ActivityListener;
import com.atomjack.vcfp.interfaces.InputStreamHandler;
import com.atomjack.vcfp.interfaces.PlayerFragmentListener;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import cz.fhucho.android.util.SimpleDiskCache;

public abstract class PlayerFragment extends Fragment
        implements SeekBar.OnSeekBarChangeListener {
  protected PlayerFragmentListener playerFragmentListener;
  protected PlexMedia nowPlayingMedia;
  protected PlexClient client;

  private View mainView;

  protected boolean continuing = false;

  protected Feedback feedback;

  protected PlayerState currentState = PlayerState.STOPPED;

  protected int position = -1;

  SimpleDiskCache simpleDiskCache;

  // UI Elements
  protected boolean resumePlayback;
  protected ImageButton playPauseButton;
  protected boolean isSeeking = false;
  protected SeekBar seekBar;
  protected TextView currentTimeDisplay;
  protected TextView durationDisplay;

  protected GestureDetectorCompat mDetector;

  protected Dialog mediaOptionsDialog;

  private int layout = -1;

  private LayoutInflater inflater;

  protected ActivityListener activityListener;

  protected PlexSubscriptionListener plexSubscriptionListener;

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    if(savedInstanceState != null) {
      Logger.d("[PlayerFragment] onSavedInstanceState is not null");
      nowPlayingMedia = savedInstanceState.getParcelable(Intent.EXTRA_MEDIA);
      layout = savedInstanceState.getInt(Intent.EXTRA_LAYOUT);
      client = savedInstanceState.getParcelable(Intent.EXTRA_CLIENT);
    }
    Logger.d("[PlayerFragment] layout: %d", layout);

    // TODO: If layout can't be found, alert activity something went wrong so it closes fragment out
    if(layout == -1)
      mainView = inflater.inflate(R.layout.player_fragment, container, false);
    else
      mainView = inflater.inflate(layout, container, false);

    this.inflater = inflater;

    showNowPlaying();
    seekBar = (SeekBar) mainView.findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    seekBar.setMax(nowPlayingMedia.duration/1000);
    seekBar.setProgress(Integer.parseInt(nowPlayingMedia.viewOffset)/1000);

    setCurrentTimeDisplay(getOffset(nowPlayingMedia));
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));

    return mainView;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(Intent.EXTRA_MEDIA, nowPlayingMedia);
    outState.putInt(Intent.EXTRA_LAYOUT, layout);
    outState.putParcelable(Intent.EXTRA_CLIENT, client);
  }


  public PlayerFragment() {
    simpleDiskCache = VoiceControlForPlexApplication.getInstance().mSimpleDiskCache;
  }

  public void init(int layout, PlexClient client, PlexMedia media, PlexSubscriptionListener plexSubscriptionListener) {
    this.layout = layout;
    this.client = client;
    nowPlayingMedia = media;
    this.plexSubscriptionListener = plexSubscriptionListener;
  }

  public void test() {
    Logger.d("[PlayerFragment] test");
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    feedback = ((NewMainActivity)context).feedback;
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

  protected void getPlayingMedia(final PlexServer server, final Timeline timeline) {
    Logger.d("[PlayerFragment] getPlayingMedia: %s", timeline.key);
    // TODO: Find out why server can sometimes be null
    PlexHttpClient.get(server, timeline.key, new PlexHttpMediaContainerHandler() {
      @Override
      public void onSuccess(MediaContainer mediaContainer) {
        if(timeline.type.equals("video"))
          nowPlayingMedia = mediaContainer.videos.get(0);
        else if(timeline.type.equals("music"))
          nowPlayingMedia = mediaContainer.tracks.get(0);
        else {
          // TODO: Handle failure
          Logger.d("Failed to get media with type %s", timeline.type);
        }

        if(nowPlayingMedia != null) {
          nowPlayingMedia.server = server;

          VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, nowPlayingMedia);
          if (timeline.continuing != null && timeline.continuing.equals("1"))
            continuing = true;
          onMediaChange();
          sendWearPlaybackChange();
        }
      }

      @Override
      public void onFailure(Throwable error) {
        // TODO: Handle failure
      }
    });
  }

  // TODO: Implement
  protected void sendWearPlaybackChange() {

  }

  protected void onMediaChange() {

  }

  public void showNowPlaying() {
    showNowPlaying(true);
  }

  // This will be called with setView=false when a new track starts playing. We don't need to set the view again (and it seems to mess up the display of the album cover)
  public void showNowPlaying(boolean setView) {
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
        TextView summary = (TextView) mainView.findViewById(R.id.nowPlayingSummary);
        summary.setText(video.summary);
      } else {
        TextView showTitle = (TextView)mainView.findViewById(R.id.nowPlayingShowTitle);
        showTitle.setText(video.grandparentTitle);
        TextView episodeTitle = (TextView)mainView.findViewById(R.id.nowPlayingEpisodeTitle);
        episodeTitle.setText(String.format("%s (s%02de%02d)", video.title, Integer.parseInt(video.parentIndex), Integer.parseInt(video.index)));
        TextView year = (TextView)mainView.findViewById(R.id.nowPlayingYear);
        year.setText(video.year);
        TextView duration = (TextView)mainView.findViewById(R.id.nowPlayingDuration);
        duration.setText(video.getDuration());
        TextView summary = (TextView)mainView.findViewById(R.id.nowPlayingSummary);
        summary.setText(video.summary);
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

    Logger.d("[PlayerActivity] Setting thumb in showNowPlaying");
    setThumb();
    attachUIElements();
  }

  private void setContentView(int layout) {
    LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mainView = inflater.inflate(layout, null);
    ViewGroup rootView = (ViewGroup) getView();
    rootView.removeAllViews();
    rootView.addView(mainView);
  }

  @SuppressWarnings("deprecation")
  private void setThumb(InputStream is) {
    Logger.d("Setting thumb: %s", is);
    final View layout;
    View backgroundLayout = mainView.findViewById(R.id.background);
    View musicLayout = mainView.findViewById(R.id.nowPlayingMusicCover);
    if(nowPlayingMedia instanceof PlexTrack && getOrientation() == Configuration.ORIENTATION_PORTRAIT) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
        backgroundLayout.setBackground(null);
      else
        backgroundLayout.setBackgroundDrawable(null);
      layout = musicLayout;
    } else {
      if(musicLayout != null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
          musicLayout.setBackground(null);
        else
          musicLayout.setBackgroundDrawable(null);
      layout = backgroundLayout;
    }

    try {
      is.reset();
    } catch (IOException e) {
//      e.printStackTrace();
    }

    if(layout == null)
      return;

    final Drawable d = Drawable.createFromStream(is, "thumb");
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
          layout.setBackground(d);
        else
          layout.setBackgroundDrawable(d);
      }
    });
  }

  protected void setThumb() {
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
      thumb = track.thumb;
    }

    if(thumb != null && thumb.equals("")) {
      thumb = null;
    }
    Logger.d("thumb: %s", thumb);

    if(thumb != null || true) {
      SimpleDiskCache.InputStreamEntry thumbEntry = null;
      try {
        Logger.d("Server: %s", nowPlayingMedia.server);
        thumbEntry = simpleDiskCache.getInputStream(nowPlayingMedia.getCacheKey(thumb != null ? thumb : nowPlayingMedia.key));
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      if (thumbEntry != null) {
        Logger.d("Using cached thumb: %s", nowPlayingMedia.getCacheKey(thumb));
        setThumb(thumbEntry.getInputStream());
      } else {
        Logger.d("Downloading thumb");
        getThumb(thumb, nowPlayingMedia);
      }
    } else {
      Logger.d("Couldn't find a background");
    }
  }

  private void getThumb(final String thumb, final PlexMedia media) {
    if(thumb == null) {
      InputStream is = getResources().openRawResource(+ R.drawable.ic_launcher);
      try {
        InputStream iss = new ByteArrayInputStream(IOUtils.toByteArray(is));
        iss.reset();
        simpleDiskCache.put(media.getCacheKey(media.key), iss);
        setThumb(iss);
      } catch (IOException e) {
        Logger.d("Exception getting/saving thumb");
        e.printStackTrace();
      }
    } else {
      media.server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(Connection connection) {
          String url = String.format("http://%s:%s%s", connection.address, connection.port, thumb);
          if (media.server.accessToken != null)
            url += String.format("?%s=%s", PlexHeaders.XPlexToken, media.server.accessToken);

          PlexHttpClient.getThumb(url, new InputStreamHandler() {
            @Override
            public void onSuccess(InputStream is) {
              try {
                InputStream iss = new ByteArrayInputStream(IOUtils.toByteArray(is));
                iss.reset();
                simpleDiskCache.put(media.getCacheKey(thumb), iss);
                setThumb(iss);
              } catch (IOException e) {
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
    playPauseButton = (ImageButton)mainView.findViewById(R.id.playPauseButton);
    playPauseButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doPlayPause();
      }
    });


    ImageButton rewindButton = (ImageButton)mainView.findViewById(R.id.rewindButton);
    rewindButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doRewind();
      }
    });

    ImageButton forwardButton = (ImageButton)mainView.findViewById(R.id.forwardButton);
    forwardButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doForward();
      }
    });

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
    micButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doMic();
      }
    });

    ImageButton stopButton = (ImageButton)mainView.findViewById(R.id.stopButton);
    stopButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        doStop();
      }
    });

    currentTimeDisplay = (TextView)mainView.findViewById(R.id.currentTimeView);
    durationDisplay = (TextView)mainView.findViewById(R.id.durationView);
//    mDetector = new GestureDetectorCompat(getActivity(), new TouchGestureListener());
//    View nowPlayingScrollView = mainView.findViewById(R.id.nowPlayingScrollView);
//    nowPlayingScrollView.setOnTouchListener(new View.OnTouchListener() {
//      @Override
//      public boolean onTouch(View v, MotionEvent event) {
//        return mDetector.onTouchEvent(event);
//      }
//    });
  }

  class TouchGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      Logger.d("Single tap.");
      doPlayPause();
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
    return getResources().getConfiguration().orientation;
  }

  protected abstract void doRewind();
  protected abstract void doForward();
  protected abstract void doPlayPause();
  protected abstract void doStop();
  protected abstract void doMic();
  protected abstract void doNext();
  protected abstract void doPrevious();

  protected void setStream(Stream stream) {
    client.setStream(stream);
  }

  protected void doMediaOptions() {
    if(nowPlayingMedia == null) {
      return;
    }

    final List<Stream> audioStreams = nowPlayingMedia.getStreams(Stream.AUDIO);
    final List<Stream> subtitleStreams = nowPlayingMedia.getStreams(Stream.SUBTITLE);

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    View layout = inflater.inflate(R.layout.media_options_dialog, null);

    if(subtitleStreams.size() > 0) {
      Spinner subtitlesSpinner = (Spinner) layout.findViewById(R.id.subtitlesSpinner);
      StreamAdapter subtitlesStreamAdapter = new StreamAdapter(getActivity(), android.R.layout.simple_spinner_dropdown_item, subtitleStreams);
      subtitlesSpinner.setAdapter(subtitlesStreamAdapter);
      subtitlesSpinner.setSelection(subtitleStreams.indexOf(nowPlayingMedia.getActiveStream(Stream.SUBTITLE)), false);

      subtitlesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          Stream stream = subtitleStreams.get(position);
          if (!stream.isActive()) {
            setStream(stream);
            nowPlayingMedia.setActiveStream(stream);
          }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      });
    } else {
      layout.findViewById(R.id.subtitlesRow).setVisibility(View.GONE);
    }

    if(audioStreams.size() > 0) {
      Spinner audioSpinner = (Spinner) layout.findViewById(R.id.audioSpinner);
      StreamAdapter audioStreamAdapter = new StreamAdapter(getActivity(), android.R.layout.simple_spinner_dropdown_item, audioStreams);
      audioSpinner.setAdapter(audioStreamAdapter);
      audioSpinner.setSelection(audioStreams.indexOf(nowPlayingMedia.getActiveStream(Stream.AUDIO)), false);

      audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          Stream stream = audioStreams.get(position);
          if (!stream.isActive()) {
            setStream(stream);
            nowPlayingMedia.setActiveStream(stream);
          }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      });
    } else {
      // For some reason, no audio streams found, so hide the row
      layout.findViewById(R.id.audioRow).setVisibility(View.GONE);
    }

    builder.setView(layout);
    builder.setTitle(getResources().getString(R.string.stream_selection));
    mediaOptionsDialog = builder.create();
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

  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {

  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {

  }

  public void setState(PlayerState newState) {
    this.currentState = newState;
    if(newState == PlayerState.PAUSED && playPauseButton != null) {
      playPauseButton.setImageResource(R.drawable.button_play);
    } else if(newState == PlayerState.PLAYING && playPauseButton != null) {
      playPauseButton.setImageResource(R.drawable.button_pause);
    }
  }

  public void setPosition(int position) {
    Logger.d("[PlayerFragment] setting position to %d", position);
    this.position = position;
    if(seekBar != null)
      seekBar.setProgress(position);
    else
      Logger.d("Seekbar is null");
    if(currentTimeDisplay != null)
      setCurrentTimeDisplay(position);
  }
}
