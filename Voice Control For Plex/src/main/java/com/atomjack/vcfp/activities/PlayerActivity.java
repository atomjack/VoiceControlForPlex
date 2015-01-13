package com.atomjack.vcfp.activities;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.services.PlexSearchService;
import com.google.gson.Gson;

import java.math.BigInteger;
import java.security.SecureRandom;

public abstract class PlayerActivity extends VCFPActivity implements SeekBar.OnSeekBarChangeListener {
	protected boolean resumePlayback;
	protected ImageButton playPauseButton;
	protected boolean isSeeking = false;
	protected SeekBar seekBar;
	protected TextView currentTimeDisplay;
	protected TextView durationDisplay;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		resumePlayback = VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false);
	}

	public void doMic(View v) {
		Intent serviceIntent = new Intent(getApplicationContext(), PlexSearchService.class);
		Gson gson = new Gson();

		PlexServer server = nowPlayingMedia.server;

		Logger.d("server: %s", server);
		if(server != null) {

			serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, gson.toJson(server));
			serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, gson.toJson(mClient));
			serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, resumePlayback);

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

	private void attachUIElements() {
		playPauseButton = (ImageButton)findViewById(R.id.playPauseButton);
		currentTimeDisplay = (TextView)findViewById(R.id.currentTimeView);
		durationDisplay = (TextView)findViewById(R.id.durationView);
	}

	protected void setCurrentTimeDisplay(long seconds) {
		currentTimeDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(seconds));
	}

	protected int getOffset(PlexMedia media) {
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
      if(video.isMovie()) {
        if(setView)
          setContentView(R.layout.now_playing_movie);

        TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
        title.setText(video.title);
        TextView genre = (TextView)findViewById(R.id.nowPlayingGenre);
        genre.setText(video.getGenres());
        TextView year = (TextView)findViewById(R.id.nowPlayingYear);
        year.setText(video.year);
        TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
        duration.setText(video.getDuration());
        TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
        summary.setText(video.summary);
      } else {
        if(setView)
          setContentView(R.layout.now_playing_show);
        TextView showTitle = (TextView)findViewById(R.id.nowPlayingShowTitle);
        showTitle.setText(video.grandparentTitle);
        TextView episodeTitle = (TextView)findViewById(R.id.nowPlayingEpisodeTitle);
        episodeTitle.setText(video.title);
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
}
