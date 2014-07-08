package com.atomjack.vcfp.activities;

import android.content.Context;
import android.net.Uri;
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
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.widgets.MiniController;

import org.json.JSONObject;

public class CastActivity extends PlayerActivity {
	private static VideoCastManager castManager = null;
	private VCFPCastConsumer castConsumer;
	private MiniController miniController;

	protected MediaInfo remoteMediaInformation;

	private int currentState = MediaStatus.PLAYER_STATE_UNKNOWN;

	private String transientToken;

	// This gets set to true when we do a seek, so after we receive a message from the receiver that
	// we have stopped, we'll resume with the new offset.
	private boolean seekDone = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

		if(getIntent().getAction().equals(VoiceControlForPlexApplication.Intent.CAST_MEDIA)) {
			PlexVideo video = getIntent().getParcelableExtra("video");
			mClient = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT);
			PlexTrack track = getIntent().getParcelableExtra("track");

			if(video != null)
				nowPlayingMedia = video;
			else if(track != null)
				nowPlayingMedia = track;
			else {
				// TODO: something here
			}
			resumePlayback = getIntent().getBooleanExtra("resume", false);

			Logger.d("Casting %s", nowPlayingMedia.title);

      castManager = castPlayerManager.getCastManager();

      if(castPlayerManager.isSubscribed()) {
        init();
      } else {
        castPlayerManager.subscribe(mClient);
        // TODO: Handle this here, need to load media after done connecting to chromecast
      }
      /*

			setCastConsumer();

			if(castManager == null) {
				castManager = getCastManager(this);
				castManager.addVideoCastConsumer(castConsumer);
				castManager.incrementUiCounter();
			}
			castManager.setDevice(mClient.castDevice, false);


			miniController = (MiniController) findViewById(R.id.miniController1);
			castManager.addMiniController(miniController);

			showNowPlaying(nowPlayingMedia, mClient);


			seekBar = (SeekBar)findViewById(R.id.seekBar);
			seekBar.setOnSeekBarChangeListener(this);
			Logger.d("setting progress to %d", getOffset(nowPlayingMedia));
			seekBar.setMax(nowPlayingMedia.duration);
			seekBar.setProgress(getOffset(nowPlayingMedia)*1000);

			setCurrentTimeDisplay(getOffset(nowPlayingMedia));
			durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));

			currentState = castManager.getPlaybackStatus();
			if (Preferences.getString(Preferences.PLEX_USERNAME) != null) {
				nowPlayingMedia.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
					@Override
					public void success(String token) {
						Logger.d("Got transient token: %s", token);
						transientToken = token;
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
			*/
		} else {
			// TODO: Something here
		}
	}

  private void init() {
    showNowPlaying(nowPlayingMedia, mClient);


    seekBar = (SeekBar)findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    Logger.d("setting progress to %d", getOffset(nowPlayingMedia));
    seekBar.setMax(nowPlayingMedia.duration);
    seekBar.setProgress(getOffset(nowPlayingMedia)*1000);

    setCurrentTimeDisplay(getOffset(nowPlayingMedia));
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));

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

	private MediaInfo getMediaInfo(String url) {
		MediaInfo mediaInfo = buildMediaInfo(
			nowPlayingMedia.getTitle(),
			nowPlayingMedia.getSummary(),
			nowPlayingMedia.getEpisodeTitle(),
			url,
			nowPlayingMedia.getArtUri(),
			nowPlayingMedia.getThumbUri(),
			nowPlayingMedia.duration,
			getOffset(nowPlayingMedia)
		);
		return mediaInfo;
	}

	private void beginPlayback() {
		String url = getTranscodeUrl(nowPlayingMedia, transientToken);
		Logger.d("url: %s", url);
		Logger.d("duration: %s", nowPlayingMedia.duration);
//		final MediaInfo mediaInfo = getMediaInfo(url);

		Logger.d("offset is %d", getOffset(nowPlayingMedia));
		if(castManager.isConnected()) {
//      try {
//        JSONObject data = buildMedia();
        castPlayerManager.loadMedia(nowPlayingMedia, getOffset(nowPlayingMedia));
//        castManager.sendDataMessage(data.toString());

      /*
			try {
				castManager.loadMedia(mediaInfo, true, getOffset(nowPlayingMedia) * 1000);
			} catch (Exception ex) {}
		} else {
			castConsumer.setOnConnected(new Runnable() {
				@Override
				public void run() {
					try {
						Logger.d("loading media");
						castManager.loadMedia(mediaInfo, true, getOffset(nowPlayingMedia) * 1000);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			});
			*/
		}
	}

	public static VideoCastManager getCastManager(Context context) {
		if (null == castManager) {
			castManager = VideoCastManager.initialize(context, MainActivity.CHROMECAST_APP_ID,
							null, null);
			castManager.enableFeatures(
							VideoCastManager.FEATURE_NOTIFICATION |
											VideoCastManager.FEATURE_LOCKSCREEN |
											VideoCastManager.FEATURE_DEBUGGING);

		}
		castManager.setContext(context);
//		String destroyOnExitStr = Utils.getStringFromPreference(context,
//						CastPreference.TERMINATION_POLICY_KEY);
		castManager.setStopOnDisconnect(false);
		return castManager;
	}

	private static MediaInfo buildMediaInfo(String title, String summary, String episodeName,
				String url, String imgUrl, String bigImageUrl, int duration, int offset) {
		MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

//		movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, summary);
//		movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
//		movieMetadata.putString(MediaMetadata.KEY_STUDIO, episodeName);
		movieMetadata.addImage(new WebImage(Uri.parse(imgUrl)));
		movieMetadata.addImage(new WebImage(Uri.parse(bigImageUrl)));

		JSONObject customData = new JSONObject();
		try {
			customData.put("duration", duration/1000);
			customData.put("position", offset);

			customData.put("title", title);
			customData.put("summary", summary);
			customData.put("episodeName", episodeName);

		} catch(Exception ex) {}
		return new MediaInfo.Builder(url)
						.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
						.setStreamDuration((long)duration)
						.setCustomData(customData)
						.setContentType("video/mp4")
						.setMetadata(movieMetadata)
						.build();
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
		Logger.d("onDestroy is called");
		if (null != castManager) {
			if(miniController != null) {
				miniController.removeOnMiniControllerChangedListener(castManager);
				castManager.removeMiniController(miniController);
			}
			castManager.clearContext(this);
		}
		super.onDestroy();
	}

	public void doPlayPause(View v) {
		try {
      Logger.d("doPlayPause, currentState: %s", currentState);
			if(currentState ==  MediaStatus.PLAYER_STATE_PAUSED) {
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
			seekDone = true;
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
  protected void castSubscribe() {

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
    Logger.d("onCastPlayerStateChanged: %d", status);
    if(status == MediaStatus.PLAYER_STATE_IDLE)
      finish();
    else
      setState(status);
  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {
    seekBar.setProgress(seconds*1000);
  }
}
