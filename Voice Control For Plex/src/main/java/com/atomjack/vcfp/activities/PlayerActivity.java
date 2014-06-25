package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexSearchService;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.google.gson.Gson;

import java.math.BigInteger;
import java.security.SecureRandom;

public class PlayerActivity extends Activity implements SeekBar.OnSeekBarChangeListener {
	protected PlexVideo playingVideo; // The video currently playing
	protected PlexTrack playingTrack; // The track currently playing
	protected PlexMedia playingMedia;
	protected PlexClient client = null;
	protected boolean resumePlayback;
	protected ImageButton playPauseButton;
	protected boolean isSeeking = false;
	protected SeekBar seekBar;
	protected TextView currentTimeDisplay;
	protected TextView durationDisplay;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		resumePlayback = Preferences.get(Preferences.RESUME, false);
	}

	public void doMic(View v) {
		Intent serviceIntent = new Intent(getApplicationContext(), PlexSearchService.class);
		Gson gson = new Gson();

		PlexServer server = null;
		if(playingVideo != null)
			server = playingVideo.server;
		else if(playingTrack != null)
			server = playingVideo.server;

		Logger.d("server: %s", server);
		if(server != null) {

			serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER, gson.toJson(server));
			serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, gson.toJson(client));
			serviceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_RESUME, resumePlayback);

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

	public void showNowPlaying(PlexMedia media, PlexClient client) {
		if(media instanceof PlexVideo)
			showNowPlaying((PlexVideo)media, client);
		else if(media instanceof PlexTrack)
			showNowPlaying((PlexTrack)media, client);
	}

	public void showNowPlaying(PlexVideo video, PlexClient client) {
		if(video.type.equals("movie")) {
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
			setContentView(R.layout.now_playing_show);
			TextView showTitle = (TextView)findViewById(R.id.nowPlayingShowTitle);
			showTitle.setText(video.showTitle);
			TextView episodeTitle = (TextView)findViewById(R.id.nowPlayingEpisodeTitle);
			episodeTitle.setText(video.title);
			TextView year = (TextView)findViewById(R.id.nowPlayingYear);
			year.setText(video.year);
			TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
			duration.setText(video.getDuration());
			TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
			summary.setText(video.summary);
		}
		TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
		nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

		PlexHttpClient.setThumb(video, (RelativeLayout) findViewById(R.id.background));
		attachUIElements();
	}

	public void showNowPlaying(PlexTrack track, PlexClient client) {
		setContentView(R.layout.now_playing_music);

		TextView artist = (TextView)findViewById(R.id.nowPlayingArtist);
		artist.setText(track.artist);
		TextView album = (TextView)findViewById(R.id.nowPlayingAlbum);
		album.setText(track.album);
		TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
		title.setText(track.title);

		TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
		nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

		PlexHttpClient.setThumb(track, (ImageView)findViewById(R.id.nowPlayingImage));
		attachUIElements();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}
}
