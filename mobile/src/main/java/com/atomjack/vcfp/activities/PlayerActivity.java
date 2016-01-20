package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.adapters.StreamAdapter;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.services.PlexSearchService;
import com.google.android.gms.wearable.DataMap;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

public abstract class PlayerActivity extends VCFPActivity implements SeekBar.OnSeekBarChangeListener {
	protected boolean resumePlayback;
	protected ImageButton playPauseButton;
	protected boolean isSeeking = false;
	protected SeekBar seekBar;
	protected TextView currentTimeDisplay;
	protected TextView durationDisplay;

  protected GestureDetectorCompat mDetector;

  protected Dialog mediaOptionsDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		resumePlayback = VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false);
	}

	public void doMic(View v) {

    PlexServer server = nowPlayingMedia.server;

    Logger.d("server: %s", server);
    if(server != null) {
      Intent serviceIntent = new Intent(getApplicationContext(), PlexSearchService.class);

      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, gsonWrite.toJson(server));
			serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, gsonWrite.toJson(mClient));
			serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, resumePlayback);
      serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_FROM_MIC, true);

			SecureRandom random = new SecureRandom();
			serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
			PendingIntent resultsPendingIntent = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

			Intent listenerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
			listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
			listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.voice_prompt));

			startActivity(listenerIntent);
		}
	}

  // Open an alert to allow selection of currently playing media's audio and/or subtitle options
  public void doMediaOptions(View v) {
    if(nowPlayingMedia == null) {
      return;
    }

    final List<Stream> audioStreams = nowPlayingMedia.getStreams(Stream.AUDIO);
    final List<Stream> subtitleStreams = nowPlayingMedia.getStreams(Stream.SUBTITLE);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = getLayoutInflater();
    View layout = inflater.inflate(R.layout.media_options_dialog, null);

    Spinner subtitlesSpinner = (Spinner)layout.findViewById(R.id.subtitlesSpinner);
    StreamAdapter subtitlesStreamAdapter = new StreamAdapter(this, android.R.layout.simple_spinner_dropdown_item, subtitleStreams);
    subtitlesSpinner.setAdapter(subtitlesStreamAdapter);
    subtitlesSpinner.setSelection(subtitleStreams.indexOf(nowPlayingMedia.getActiveStream(Stream.SUBTITLE)), false);

    subtitlesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Stream stream = subtitleStreams.get(position);
        if(!stream.isActive()) {
          mClient.setStream(stream);
          nowPlayingMedia.setActiveStream(stream);
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
    Spinner audioSpinner = (Spinner)layout.findViewById(R.id.audioSpinner);
    StreamAdapter audioStreamAdapter = new StreamAdapter(this, android.R.layout.simple_spinner_dropdown_item, audioStreams);
    audioSpinner.setAdapter(audioStreamAdapter);
    audioSpinner.setSelection(audioStreams.indexOf(nowPlayingMedia.getActiveStream(Stream.AUDIO)), false);

    audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Stream stream = audioStreams.get(position);
        if(!stream.isActive()) {
          mClient.setStream(stream);
          nowPlayingMedia.setActiveStream(stream);
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    builder.setView(layout);
    builder.setTitle(getResources().getString(R.string.stream_selection));
    mediaOptionsDialog = builder.create();
    mediaOptionsDialog.show();
  }

	private void attachUIElements() {
		playPauseButton = (ImageButton)findViewById(R.id.playPauseButton);
		currentTimeDisplay = (TextView)findViewById(R.id.currentTimeView);
		durationDisplay = (TextView)findViewById(R.id.durationView);

    mDetector = new GestureDetectorCompat(this, new TouchGestureListener());
    View nowPlayingScrollView = findViewById(R.id.nowPlayingScrollView);
    nowPlayingScrollView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        return mDetector.onTouchEvent(event);
      }
    });
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
            doForward(null);
          } else {
            Logger.d("Doing back via fling left");
            doRewind(null);
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      return true;
    }
  }

  public void doRewind(View v) {}
  public void doForward(View v) {}

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

  public void showNowPlaying() {
    showNowPlaying(true);
  }

  // This will be called with setView=false when a new track starts playing. We don't need to set the view again (and it seems to mess up the display of the album cover)
  public void showNowPlaying(boolean setView) {
    if (nowPlayingMedia instanceof PlexVideo) {
      PlexVideo video = (PlexVideo)nowPlayingMedia;
      if(video.isMovie() || video.isClip()) {
        if (setView)
          setContentView(R.layout.now_playing_movie);

        TextView title = (TextView) findViewById(R.id.nowPlayingTitle);
        title.setText(video.title);
        TextView genre = (TextView) findViewById(R.id.nowPlayingGenre);
        genre.setText(video.getGenres());
        TextView year = (TextView) findViewById(R.id.nowPlayingYear);
        year.setText(video.year);
        TextView duration = (TextView) findViewById(R.id.nowPlayingDuration);
        duration.setText(video.getDuration());
        TextView summary = (TextView) findViewById(R.id.nowPlayingSummary);
        summary.setText(video.summary);
      } else {
        if(setView)
          setContentView(R.layout.now_playing_show);
        TextView showTitle = (TextView)findViewById(R.id.nowPlayingShowTitle);
        showTitle.setText(video.grandparentTitle);
        TextView episodeTitle = (TextView)findViewById(R.id.nowPlayingEpisodeTitle);
        episodeTitle.setText(String.format("%s (s%02de%02d)", video.title, Integer.parseInt(video.parentIndex), Integer.parseInt(video.index)));
        TextView year = (TextView)findViewById(R.id.nowPlayingYear);
        year.setText(video.year);
        TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
        duration.setText(video.getDuration());
        TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
        summary.setText(video.summary);
      }

    } else if (nowPlayingMedia instanceof PlexTrack) {
      PlexTrack track = (PlexTrack)nowPlayingMedia;
      if(setView)
        setContentView(R.layout.now_playing_music);

      TextView artist = (TextView)findViewById(R.id.nowPlayingArtist);
      Logger.d("Setting artist to %s", track.grandparentTitle);
      artist.setText(track.grandparentTitle);
      TextView album = (TextView)findViewById(R.id.nowPlayingAlbum);
      album.setText(track.parentTitle);
      TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
      title.setText(track.title);
    }
    TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
    nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + mClient.name);

    Logger.d("[PlayerActivity] Setting thumb in showNowPlaying");
    setThumb();
    attachUIElements();
  }

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
		setCurrentTimeDisplay(progress / 1000);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		isSeeking = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

  public abstract void doStop(View v);

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if(intent.getAction() != null) {
      String action = intent.getAction();
      if(action.equals(com.atomjack.shared.Intent.GET_PLAYING_MEDIA) && nowPlayingMedia != null) {
        // Send information on the currently playing media to the wear device
        DataMap data = new DataMap();
        data.putString(WearConstants.MEDIA_TITLE, nowPlayingMedia.title);
        data.putString(WearConstants.IMAGE, nowPlayingMedia.art);
        new SendToDataLayerThread(WearConstants.GET_PLAYING_MEDIA, data, this).start();
      }
    }
  }
}
