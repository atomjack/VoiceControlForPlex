package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.castcompanionlibrary.cast.BaseCastManager;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.callbacks.IVideoCastConsumer;
import com.google.sample.castcompanionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.sample.castcompanionlibrary.utils.LogUtils;
import com.google.sample.castcompanionlibrary.widgets.MiniController;

public class CastActivity extends Activity {
	private PlexVideo playingVideo; // The video currently playing
	private PlexTrack playingTrack; // The track currently playing
	private PlexClient client = null;

	private boolean resumePlayback;
	private static VideoCastManager castManager = null;
	private IVideoCastConsumer castConsumer;
	private MiniController miniController;

	private SharedPreferences mPrefs;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mPrefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

		if(getIntent().getAction().equals(VoiceControlForPlexApplication.Intent.CAST_MEDIA)) {
			playingVideo = getIntent().getParcelableExtra("video");
			client = getIntent().getParcelableExtra("client");
			playingTrack = getIntent().getParcelableExtra("track");

			resumePlayback = getIntent().getBooleanExtra("resume", false);

			Logger.d("Casting %s", playingVideo.title);

			setContentView(R.layout.now_playing_movie);

			setCastConsumer();




			if(castManager == null) {
				castManager = getCastManager(this);
				castManager.addVideoCastConsumer(castConsumer);
				castManager.incrementUiCounter();
			}
			castManager.setDevice(client.castDevice, false);


			miniController = (MiniController) findViewById(R.id.miniController1);
			castManager.addMiniController(miniController);


			Logger.d("tag = %s", LogUtils.makeLogTag(BaseCastManager.class));

			String url = getTranscodeUrl(playingVideo);
			Logger.d("url: %s", url);
			MediaInfo mediaInfo = buildMediaInfo(
							playingVideo.type.equals("movie") ? playingVideo.title : playingVideo.showTitle,
							playingVideo.summary,
							playingVideo.type.equals("movie") ? "" : playingVideo.title,
							url,
							playingVideo.getArtUri(),
							playingVideo.getThumbUri()
			);

			try {
				Logger.d("loading media");
				castManager.loadMedia(mediaInfo, true, 0);
			} catch(Exception ex) {
				ex.printStackTrace();
			}

//			castManager.startCastControllerActivity(this, mediaInfo, 0, true);

			// TODO: Do this later
			NowPlayingActivity.showNowPlaying(this, playingVideo, client);



		} else {
			// TODO: Something here
		}
	}

	private void setCastConsumer() {
		castConsumer = new VideoCastConsumerImpl() {

			@Override
			public void onFailed(int resourceId, int statusCode) {
				Logger.d("castConsumer failed: %d", statusCode);
			}

			@Override
			public void onConnectionSuspended(int cause) {
				Logger.d("onConnectionSuspended() was called with cause: " + cause);
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_temp_lost);
			}

			@Override
			public void onConnectivityRecovered() {
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_recovered);
			}

			@Override
			public void onApplicationStatusChanged(String appStatus) {
				Logger.d("CastActivity onApplicationStatusChanged: %s", appStatus);
			}

			@Override
			public void onCastDeviceDetected(final MediaRouter.RouteInfo info) {
				Logger.d("onCastDeviceDetected: %s", info);
			}
		};
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

	private static MediaInfo buildMediaInfo(String title, String summary, String episodeName, String url, String imgUrl, String bigImageUrl) {
		MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

		movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, summary);
		movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
		movieMetadata.putString(MediaMetadata.KEY_STUDIO, episodeName);
		movieMetadata.addImage(new WebImage(Uri.parse(imgUrl)));
		movieMetadata.addImage(new WebImage(Uri.parse(bigImageUrl)));

		return new MediaInfo.Builder(url)
						.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
						.setContentType("video/mp4")
						.setMetadata(movieMetadata)
						.build();
	}

	private String getTranscodeUrl(PlexVideo video) {
		String url = video.server.activeConnection.uri;
		url += "/video/:/transcode/universal/start?";
		QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", video.key));
		qs.add("mediaIndex", "0");
		qs.add("partIndex", "0");
		qs.add("protocol", "http");
//		qs.add("offset")
		if((mPrefs.getBoolean("resume", false) || resumePlayback) && video.viewOffset != null)
			qs.add("offset", video.viewOffset);
		qs.add("fastSeek", "1");
		qs.add("directPlay", "0");
		qs.add("directStream", "1");
		qs.add("videoQuality", "60");
		qs.add("videoResolution", "1024x768");
		qs.add("maxVideoBitrate", "2000");
		qs.add("subtitleSize", "100");
		qs.add("audioBoost", "100");
		if(mPrefs.getString(Preferences.UUID, null) != null)
			qs.add("session", mPrefs.getString(Preferences.UUID, null));
//		qs.add(PlexHeaders.XPlexClientIdentifier)
		return url + qs.toString();
	}

	@Override
	protected void onResume() {
		Logger.d("onResume() was called");
		castManager = getCastManager(this);
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
//			mMini.removeOnMiniControllerChangedListener(castManager);
//			castManager.removeMiniController(mMini);
			castManager.clearContext(this);
		}
		super.onDestroy();
	}
}
