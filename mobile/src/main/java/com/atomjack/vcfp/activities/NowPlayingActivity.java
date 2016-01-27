package com.atomjack.vcfp.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.SeekBar;

import com.atomjack.shared.PlayerState;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.BuildConfig;
import com.atomjack.shared.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDirectory;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.bugsense.trace.BugSenseHandler;
import com.google.android.gms.wearable.DataMap;

import org.codechimp.apprater.AppRater;

import java.util.List;

public class NowPlayingActivity extends PlayerActivity {
	private boolean subscribed = false;
  private boolean fromWear = false;
  private final Handler handler = new Handler();

	PlayerState state = PlayerState.STOPPED;

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("[NowPlayingActivity] onCreate");
		super.onCreate(savedInstanceState);

    plexSubscription.setListener(this);

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(NowPlayingActivity.this, MainActivity.BUGSENSE_APIKEY);

		setContentView(R.layout.play_media);

		AppRater.app_launched(this);

		if(savedInstanceState != null) {
			Logger.d("found saved instance state");
			nowPlayingMedia = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_MEDIA);
      mClient = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT);
      fromWear = savedInstanceState.getBoolean(WearConstants.FROM_WEAR, false);
      Logger.d("[NowPlaying] set client: %s", mClient);
		} else {
			nowPlayingMedia = getIntent().getParcelableExtra(com.atomjack.shared.Intent.EXTRA_MEDIA);
      mClient = getIntent().getParcelableExtra(com.atomjack.shared.Intent.EXTRA_CLIENT);
      fromWear = getIntent().getBooleanExtra(WearConstants.FROM_WEAR, false);
      Logger.d("[NowPlayingActivity] 2 set client: %s", mClient);
		}

    if(fromWear) {
      new SendToDataLayerThread(WearConstants.FINISH, this).start();
    }

		if(mClient == null || nowPlayingMedia == null)
			finish();

    // If we're not subscribed, or the current state of the PlexClient is stopped, finish the activity.
    // However, we need to wait a few seconds before checking this as if we're not subscribed when playback
    // is triggered, getting subscribed will take a small amount of time
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        Logger.d("[NowPlayingActivity] subscribed: %s", plexSubscription.isSubscribed());
        if(!plexSubscription.isSubscribed() || plexSubscription.getCurrentState() == PlayerState.STOPPED) {
          VoiceControlForPlexApplication.getInstance().cancelNotification();
          finish();
        }
      }
    }, 3000);


    if(nowPlayingMedia == null) {
      VoiceControlForPlexApplication.getInstance().cancelNotification();
      finish();
    } else {
      Logger.d("mClient: %s", mClient);
      Logger.d("nowPlayingMedia: %s", nowPlayingMedia);
      showNowPlaying();
      seekBar = (SeekBar) findViewById(R.id.seekBar);
      seekBar.setOnSeekBarChangeListener(NowPlayingActivity.this);
      seekBar.setMax(nowPlayingMedia.duration);
      seekBar.setProgress(Integer.parseInt(nowPlayingMedia.viewOffset));

      setCurrentTimeDisplay(getOffset(nowPlayingMedia));
      durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
    }
	}

	private void setState(PlayerState newState) {
		state = newState;
		if(state == PlayerState.PAUSED) {
			playPauseButton.setImageResource(R.drawable.button_play);
		} else if(state == PlayerState.PLAYING) {
			playPauseButton.setImageResource(R.drawable.button_pause);
		}
	}


  @Override
	public void doPlayPause() {
		Logger.d("play pause clicked");
		if(state == PlayerState.PLAYING) {
			mClient.pause(new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {
					setState(PlayerState.PAUSED);
				}

				@Override
				public void onFailure(Throwable error) {
					// TODO: Handle this
				}
			});
		} else if(state == PlayerState.PAUSED) {
			mClient.play(new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {
					setState(PlayerState.PLAYING);
				}

				@Override
				public void onFailure(Throwable error) {
					// TODO: Handle this
				}
			});
		}
	}

  @Override
  public void doPlayPause(View v) {
    doPlayPause();
  }

  @Override
	public void doRewind(View v) {
		if(position > -1) {
			mClient.seekTo(position - 15000, null);
		}
	}

  @Override
	public void doForward(View v) {
		if(position > -1) {
			mClient.seekTo(position + 30000, null);
		}
	}

  public void doNext(View v) {
    Logger.d("doNext");
    mClient.next(null);
  }

  public void doPrevious(View v) {
    Logger.d("doPrevious");
    mClient.previous(null);
  }

  @Override
	public void doStop(View v) {
    mClient.stop(new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
        finish();
      }

      @Override
      public void onFailure(Throwable error) {
        finish();
      }
    });
	}

  @Override
	protected void onPause() {
		super.onPause();
    Logger.d("NowPlaying onPause");
		VoiceControlForPlexApplication.applicationPaused();
	}

	@Override
	protected void onResume() {
		super.onResume();
    Logger.d("NowPlaying onResume");
		VoiceControlForPlexApplication.applicationResumed();
    plexSubscription.setListener(this);
    if(menu != null) {
      Logger.d("Now subscribing");
//      if(plexSubscription.isSubscribed()) {
//        if(plexSubscription.mClient.machineIdentifier != mClient.machineIdentifier) {
//          // We're already subscribed to another client, so unsubscribe from that one and subscribe to the new one
//          plexSubscription.unsubscribe(new Runnable() {
//            @Override
//            public void run() {
//              plexSubscription.subscribe(mClient);
//            }
//          });
//        }
//      } else
        plexSubscription.subscribe(mClient);
    }
	}

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Logger.d("now playing onconfigchanged: %d", newConfig.orientation);

//    if(nowPlayingMedia instanceof PlexVideo)
      Logger.d("[NowPlayingActivity] Setting thumb in onConfigChanged");
      setThumb();
//    else if(nowPlayingMedia instanceof PlexTrack)
//      setThumb((PlexTrack)nowPlayingMedia, newConfig.orientation, (ImageView)findViewById(R.id.nowPlayingImage));
  }

  @Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			if(subscribed) {
        // TODO: Unsubscribe
			}
		} catch(Exception ex) {
			ex.printStackTrace();
		}

	}

	@Override
	protected void onStop() {
		Logger.d("NowPlaying onStop");
		super.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Logger.d("Saving instance state");
		outState.putParcelable(com.atomjack.shared.Intent.EXTRA_MEDIA, nowPlayingMedia);
		outState.putParcelable(com.atomjack.shared.Intent.EXTRA_CLIENT, mClient);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if(intent.getExtras() != null && intent.getExtras().getBoolean("finish") == true)
			finish();
	}

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    super.onStartTrackingTouch(seekBar);
    isSeeking = true;
  }

  @Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		mClient.seekTo(_seekBar.getProgress(), new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				isSeeking = false;
			}

			@Override
			public void onFailure(Throwable error) {
				isSeeking = false;
				feedback.e(String.format(getString(R.string.error_seeking), error.getMessage()));
			}
		});

	}

  @Override
  protected void onSubscriptionMessage(Timeline timeline) {
//    Logger.d("[NowPlaying] onSubscriptionMessage: %d, Continuing: %s", timeline.time, continuing);
    if(!isSeeking)
      seekBar.setProgress(timeline.time);

    if(continuing) {
      onMediaChange();
      continuing = false;
    }

    if(timeline.state.equals("stopped")) {
      Logger.d("NowPlayingActivity stopping");
      if(timeline.continuing != null && timeline.continuing.equals("1")) {
        Logger.d("Continuing to next track");
      } else {
        VoiceControlForPlexApplication.getInstance().cancelNotification();
        finish();
      }
    } else if(timeline.state.equals("playing")) {
      setState(PlayerState.PLAYING);
    } else if(timeline.state.equals("paused")) {
      setState(PlayerState.PAUSED);
    }
  }

  @Override
  protected void onMediaChange() {
    Logger.d("[NowPlayingActivity] onMediaChange: %s, duration %d", nowPlayingMedia.title, nowPlayingMedia.duration);
    seekBar.setMax(nowPlayingMedia.duration);
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
    Logger.d("[NowPlayingActivity] Setting thumb in onSubscriptionMessage");
    setThumb();
    showNowPlaying(false);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu _menu) {
    Logger.d("NowPlaying onPrepareOptionsMenu");
    menu = _menu;
    return super.onPrepareOptionsMenu(_menu);
  }

  @Override
   public boolean onCreateOptionsMenu(Menu _menu) {
    super.onCreateOptionsMenu(_menu);
    Logger.d("NowPlaying onCreateOptionsMenu: %s", _menu);
    getMenuInflater().inflate(R.menu.menu_playing, _menu);
    menu = _menu;
    if(plexSubscription.isSubscribed()) {
      if(!plexSubscription.mClient.machineIdentifier.equals(mClient.machineIdentifier)) {
        // We're already subscribed to another client, so unsubscribe from that one and subscribe to the new one
        plexSubscription.unsubscribe(false, new Runnable() {
          @Override
          public void run() {
            Logger.d("[NowPlayingActivity] now subscribing to %s", mClient.name);
            plexSubscription.subscribe(mClient);
            setCastIconActive();
          }
        });
      } else
        setCastIconActive();
    } else
      plexSubscription.subscribe(mClient);
    return true;
  }

  @Override
  public void onSubscribed(PlexClient _client) {
    Logger.d("NowPlayingActivity onSubscribed: %s", _client);
    super.onSubscribed(_client);
  }

  @Override
  public void onUnsubscribed() {
    super.onUnsubscribed();
    finish();
  }


}
