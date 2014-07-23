package com.atomjack.vcfp.activities;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.AfterTransientTokenRequest;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPCastConsumer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.google.android.gms.cast.MediaStatus;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;

import java.util.List;

public class CastActivity extends PlayerActivity {
	private static VideoCastManager castManager = null;
	private VCFPCastConsumer castConsumer;

	private int currentState = MediaStatus.PLAYER_STATE_UNKNOWN;

	private String transientToken;

  private List<PlexMedia> nowPlayingAlbum;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

		if(getIntent().getAction() != null && getIntent().getAction().equals(VoiceControlForPlexApplication.Intent.CAST_MEDIA)) {
			mClient = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT);
      nowPlayingMedia = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
      nowPlayingAlbum = getIntent().getParcelableArrayListExtra(VoiceControlForPlexApplication.Intent.EXTRA_ALBUM);
			resumePlayback = getIntent().getBooleanExtra("resume", false);

			Logger.d("Casting %s", nowPlayingMedia.title);

      castManager = castPlayerManager.getCastManager();

      showNowPlaying();
      if(castPlayerManager.isSubscribed()) {
        init();
      } else {
        castPlayerManager.subscribe(mClient);
      }
		} else {
			// TODO: Something here
      Logger.d("[CastActivity] No action found.");
      finish();
		}
	}

  @Override
  public void onCastConnected(PlexClient _client) {
    super.onCastConnected(_client);
    init();
  }

  private void setupUI() {
    Logger.d("setting progress to %d", getOffset(nowPlayingMedia));
    seekBar.setMax(nowPlayingMedia.duration);
    seekBar.setProgress(getOffset(nowPlayingMedia)*1000);

    setCurrentTimeDisplay(getOffset(nowPlayingMedia));
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
  }

  private void init() {
    seekBar = (SeekBar)findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    setupUI();

    currentState = castManager.getPlaybackStatus();
    if (Preferences.getString(Preferences.PLEX_USERNAME) != null) {
      nowPlayingMedia.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
        @Override
        public void success(String token) {
          Logger.d("Got transient token: %s", token);
          transientToken = token;
          castPlayerManager.setTransientToken(token);
          beginPlayback();
        }

        @Override
        public void failure() {
          Logger.d("Failed to get transient access token");
          // Failed to get an access token, so let's try without one
          beginPlayback();
        }
      });
    } else {
      beginPlayback();
    }
  }

	private void beginPlayback() {
		String url = getTranscodeUrl(nowPlayingMedia, transientToken);
		Logger.d("url: %s", url);
		Logger.d("duration: %s", nowPlayingMedia.duration);

		Logger.d("offset is %d", getOffset(nowPlayingMedia));
		if(castManager.isConnected()) {
        castPlayerManager.loadMedia(nowPlayingMedia, nowPlayingAlbum, getOffset(nowPlayingMedia));
		}
	}

	private String getTranscodeUrl(PlexMedia media, String transientToken) {
		String url = media.server.activeConnection.uri;
		url += "/video/:/transcode/universal/start?";
		QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", media.key));
		qs.add("mediaIndex", "0");
		qs.add("partIndex", "0");
		qs.add("protocol", "http");
		qs.add("offset", Integer.toString(getOffset(media)));
		qs.add("fastSeek", "1");
		qs.add("directPlay", "0");
		qs.add("directStream", "1");
		qs.add("videoQuality", "60");
		qs.add("videoResolution", "1024x768");
		qs.add("maxVideoBitrate", "2000");
		qs.add("subtitleSize", "100");
		qs.add("audioBoost", "100");
		qs.add("session", Preferences.getUUID());
		qs.add(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID());
		qs.add(PlexHeaders.XPlexProduct, String.format("%s Chromecast", getString(R.string.app_name)));
		qs.add(PlexHeaders.XPlexDevice, mClient.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexDeviceName, mClient.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexPlatform, mClient.castDevice.getModelName());
		if(transientToken != null)
			qs.add(PlexHeaders.XPlexToken, transientToken);
		qs.add(PlexHeaders.XPlexPlatformVersion, "1.0");
		try {
			qs.add(PlexHeaders.XPlexVersion, getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		// TODO: Fix this
		if(Preferences.getString(Preferences.PLEX_USERNAME) != null)
			qs.add(PlexHeaders.XPlexUsername, Preferences.getString(Preferences.PLEX_USERNAME));
		return url + qs.toString();
	}

	@Override
	protected void onResume() {
		Logger.d("CastActivity onResume");
		castManager = castPlayerManager.getCastManager();
    castPlayerManager.setListener(this);
		if (null != castManager) {
			castManager.addVideoCastConsumer(castConsumer);
			castManager.incrementUiCounter();
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		castManager.decrementUiCounter();
		castManager.removeVideoCastConsumer(castConsumer);
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		Logger.d("[CastActivity] onDestroy");
		if (null != castManager) {
			castManager.clearContext(this);
		}
		super.onDestroy();
	}

	public void doPlayPause(View v) {
		try {
      Logger.d("doPlayPause, currentState: %s", currentState);
			if(currentState !=  MediaStatus.PLAYER_STATE_PLAYING) {
				castPlayerManager.play();
			} else if(currentState ==  MediaStatus.PLAYER_STATE_PLAYING) {
        castPlayerManager.pause();
			}
		} catch (Exception ex) {}
	}

	public void doRewind(View v) {

	}

	public void doForward(View v) {
	}

	public void doStop(View v) {
		try {
      castPlayerManager.stop();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		Logger.d("stopped changing progress");
		try {
			nowPlayingMedia.viewOffset = Integer.toString(_seekBar.getProgress());
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    isSeeking = false;
	}

	private void setState(int newState) {
		currentState = newState;
		if(currentState ==  MediaStatus.PLAYER_STATE_PAUSED) {
			playPauseButton.setImageResource(R.drawable.button_play);
		} else if(currentState ==  MediaStatus.PLAYER_STATE_PLAYING) {
			playPauseButton.setImageResource(R.drawable.button_pause);
		}
	}

  @Override
  public boolean onCreateOptionsMenu(Menu _menu) {
    super.onCreateOptionsMenu(_menu);
    getMenuInflater().inflate(R.menu.menu_playing, _menu);
    menu = _menu;
    if(castPlayerManager.isSubscribed())
      onSubscribed(mClient);

    return true;
  }

  @Override
  public void onCastPlayerStateChanged(int status) {
    super.onCastPlayerStateChanged(status);
    Logger.d("[CastActivity] onCastPlayerStateChanged: %d", status);
    if(isSeeking)
      isSeeking = false;
    if(status == MediaStatus.PLAYER_STATE_IDLE) {
      Logger.d("[CastActivity] media player is idle, finishing");
      finish();
    } else
      setState(status);
  }

  @Override
  public void onUnsubscribed() {
    super.onUnsubscribed();
    finish();
  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {
    if(!isSeeking)
      seekBar.setProgress(seconds*1000);
  }

  @Override
  public void onCastPlayerPlaylistAdvance(String key) {
    for(PlexMedia track : nowPlayingAlbum) {
      if(track.key.equals(key)) {
        nowPlayingMedia = track;
        setupUI();
        showNowPlaying(false);
        break;
      }
    }
  }
}
