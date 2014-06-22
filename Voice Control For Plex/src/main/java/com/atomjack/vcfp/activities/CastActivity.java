package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.atomjack.vcfp.AfterTransientTokenRequest;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

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


			playingVideo.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
				@Override
				public void success(String token) {
					Logger.d("Got transient token: %s", token);
					String url = getTranscodeUrl(playingVideo, token);
//			url = "http://192.168.1.101:32400/video/:/transcode/universal/start?path=http%3A%2F%2F127.0.0.1%3A32400%2Flibrary%2Fmetadata%2F14&mediaIndex=0&partIndex=0&protocol=http&offset=0&fastSeek=1&directPlay=0&directStream=1&videoQuality=60&videoResolution=1024x768&maxVideoBitrate=2000&subtitleSize=100&audioBoost=100&session=v778c32skclkgldi&X-Plex-Client-Identifier=qhajaiikdxsthuxr&X-Plex-Product=Plex+Chromecast&X-Plex-Device=Chromecast&X-Plex-Platform=Chromecast&X-Plex-Platform-Version=1.0&X-Plex-Version=2.1.11&X-Plex-Device-Name=Chromecast&X-Plex-Token=transient-3938fefb-c3fa-4c9a-901a-65c13b4c05ea&X-Plex-Username=atomjack";
					Logger.d("url: %s", url);
					final MediaInfo mediaInfo = buildMediaInfo(
									playingVideo.type.equals("movie") ? playingVideo.title : playingVideo.showTitle,
									playingVideo.summary,
									playingVideo.type.equals("movie") ? "" : playingVideo.title,
									url,
									playingVideo.getArtUri(),
									playingVideo.getThumbUri()
					);

					final Handler handler = new Handler();
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							try {
								Logger.d("loading media");
								castManager.loadMedia(mediaInfo, true, 0);
							} catch(Exception ex) {
								ex.printStackTrace();
							}
						}
					}, 5000);


//			castManager.startCastControllerActivity(this, mediaInfo, 0, true);

					// TODO: Do this later
					NowPlayingActivity.showNowPlaying(CastActivity.this, playingVideo, client);
				}

				@Override
				public void failure() {
					// TODO: Handle this
					Logger.d("Failed to get transient access token");
				}
			});




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

	private String getTranscodeUrl(PlexVideo video, String transientToken) {
		String url = video.server.activeConnection.uri;
		url += "/video/:/transcode/universal/start?";
		QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", video.key));
		qs.add("mediaIndex", "0");
		qs.add("partIndex", "0");
		qs.add("protocol", "http");
//		qs.add("offset")
		if((Preferences.get(Preferences.RESUME, false) || resumePlayback) && video.viewOffset != null)
			qs.add("offset", video.viewOffset);
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
		qs.add(PlexHeaders.XPlexDevice, client.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexDeviceName, client.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexPlatform, client.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexToken, transientToken);
		qs.add(PlexHeaders.XPlexPlatformVersion, "1.0");
		try {
			qs.add(PlexHeaders.XPlexVersion, getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		// TODO: Fix this
		qs.add(PlexHeaders.XPlexUsername, Preferences.get(Preferences.PLEX_USERNAME, ""));
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
