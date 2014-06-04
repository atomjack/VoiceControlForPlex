package com.atomjack.vcfp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.bugsense.trace.BugSenseHandler;

import org.codechimp.apprater.AppRater;

public class NowPlayingActivity extends Activity {
	private PlexVideo playingVideo; // The video currently playing
	private PlexTrack playingTrack; // The track currently playing
	private PlexClient client = null;



	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create NowPlayingActivity");
		super.onCreate(savedInstanceState);

		BugSenseHandler.initAndStartSession(NowPlayingActivity.this, MainActivity.BUGSENSE_APIKEY);

		setContentView(R.layout.play_media);

		AppRater.app_launched(this);

		if(savedInstanceState != null) {
			Logger.d("found saved instance state");
			playingVideo = savedInstanceState.getParcelable("video");
			playingTrack = savedInstanceState.getParcelable("track");
			client = savedInstanceState.getParcelable("client");
		} else {
			playingVideo = getIntent().getParcelableExtra("video");
			client = getIntent().getParcelableExtra("client");
			playingTrack = getIntent().getParcelableExtra("track");
		}

		if(client == null)
			finish();

		if(playingVideo != null) {
			Logger.d("now playing %s", playingVideo.title);
			if(playingVideo.type.equals("movie")) {
				setContentView(R.layout.now_playing_movie);
				TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
				title.setText(playingVideo.title);
				TextView genre = (TextView)findViewById(R.id.nowPlayingGenre);
				genre.setText(playingVideo.getGenres());
				TextView year = (TextView)findViewById(R.id.nowPlayingYear);
				year.setText(playingVideo.year);
				TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
				duration.setText(playingVideo.getDuration());
				TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
				summary.setText(playingVideo.summary);
			} else {
				setContentView(R.layout.now_playing_show);

				TextView showTitle = (TextView)findViewById(R.id.nowPlayingShowTitle);
				showTitle.setText(playingVideo.showTitle);
				TextView episodeTitle = (TextView)findViewById(R.id.nowPlayingEpisodeTitle);
				episodeTitle.setText(playingVideo.title);
				TextView year = (TextView)findViewById(R.id.nowPlayingYear);
				year.setText(playingVideo.year);
				TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
				duration.setText(playingVideo.getDuration());
				TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
				summary.setText(playingVideo.summary);
			}
			TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
			nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

			PlexHttpClient.setThumb(playingVideo, (ScrollView) findViewById(R.id.background));
		} else if(playingTrack != null) {

			setContentView(R.layout.now_playing_music);

			TextView artist = (TextView)findViewById(R.id.nowPlayingArtist);
			artist.setText(playingTrack.artist);
			TextView album = (TextView)findViewById(R.id.nowPlayingAlbum);
			album.setText(playingTrack.album);
			TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
			title.setText(playingTrack.title);

			TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
			nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

			PlexHttpClient.setThumb(playingTrack, (ImageView)findViewById(R.id.nowPlayingImage));
		} else {
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Logger.d("Saving instance state");
		outState.putParcelable("video", playingVideo);
		outState.putParcelable("client", client);
		outState.putParcelable("track", playingTrack);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if(intent.getExtras().getBoolean("finish") == true)
			finish();
	}
}
