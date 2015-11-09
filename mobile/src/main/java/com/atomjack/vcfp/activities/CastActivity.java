package com.atomjack.vcfp.activities;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.shared.Intent;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.interfaces.AfterTransientTokenRequest;
import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPCastConsumer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;

import java.util.ArrayList;
import java.util.List;

public class CastActivity extends PlayerActivity {
	private static VideoCastManager castManager = null;
	private VCFPCastConsumer castConsumer;

	private PlayerState currentState = PlayerState.STOPPED;

  private List<PlexMedia> nowPlayingAlbum = new ArrayList<>();

  private Dialog infoDialog;

  final Handler handler = new Handler();

  private boolean uiShowing = false;

  private void start(final boolean setView) {
    mClient = getIntent().getParcelableExtra(Intent.EXTRA_CLIENT);
    Logger.d("[CastActivity] set mClient: %s", mClient);

    if(getIntent().getBooleanExtra(WearConstants.FROM_WEAR, false)) {
      new SendToDataLayerThread(WearConstants.FINISH, this).start();
    }

    if(getIntent().getAction() != null && getIntent().getAction().equals(Intent.CAST_MEDIA)) {
      Logger.d("[CastActivity] checking subscribed: %s", castPlayerManager.isSubscribed());
      if(castPlayerManager.isSubscribed()) {
        nowPlayingMedia = castPlayerManager.getNowPlayingMedia();
        nowPlayingAlbum = castPlayerManager.getNowPlayingAlbum();
        if(!castPlayerManager.getCurrentState().equals(PlayerState.STOPPED)) {
          // Media is playing, so show ui
          showNowPlaying(setView);
        } else {
          handler.postDelayed(new Runnable() {
            @Override
            public void run() {
              Logger.d("Checking for playback state: %s", castPlayerManager.getCurrentState());
              if(!castPlayerManager.getCurrentState().equals(PlayerState.STOPPED)) {
                showNowPlaying(setView);
              } else {
                handler.postDelayed(this, 1000);
              }
            }
          }, 1000);
        }
      } else {
        if(getIntent().getParcelableExtra(Intent.EXTRA_MEDIA) != null) {
          nowPlayingMedia = getIntent().getParcelableExtra(Intent.EXTRA_MEDIA);
          nowPlayingAlbum = getIntent().getParcelableExtra(Intent.EXTRA_ALBUM);
          showNowPlaying(setView);
        }
        castPlayerManager.subscribe(mClient, new Runnable() {
          @Override
          public void run() {
            // TODO: this
            nowPlayingMedia = castPlayerManager.getNowPlayingMedia();
            nowPlayingAlbum = castPlayerManager.getNowPlayingAlbum();
          }
        });
      }
    } else {
      // We're coming here expecting to already be connected and have media playing.
      if(!castPlayerManager.isSubscribed() || castPlayerManager.getCurrentState().equals(PlayerState.STOPPED))
        finish();
      else {
        nowPlayingMedia = castPlayerManager.getNowPlayingMedia();
        nowPlayingAlbum = castPlayerManager.getNowPlayingAlbum();
        showNowPlaying(setView);
      }
    }
  }

  @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

    start(true);

/*

    boolean mediaChange = false;
    PlexMedia newMedia = getIntent().getParcelableExtra(Intent.EXTRA_MEDIA);
    if(castPlayerManager.isSubscribed()) {
      if(newMedia != null && castPlayerManager.getNowPlayingMedia() != null && !newMedia.key.equals(castPlayerManager.getNowPlayingMedia().key))
        mediaChange = true;
    }


    nowPlayingMedia = newMedia;
    nowPlayingAlbum = getIntent().getParcelableArrayListExtra(Intent.EXTRA_ALBUM);
    resumePlayback = getIntent().getBooleanExtra("resume", false);
    castManager = castPlayerManager.getCastManager();

    if(getIntent().getBooleanExtra(WearConstants.FROM_WEAR, false)) {
      new SendToDataLayerThread(WearConstants.FINISH, this).start();
    }
    // If just playing a single track, put the media into an array
    if(nowPlayingAlbum == null) {
      nowPlayingAlbum = new ArrayList<>();
      nowPlayingAlbum.add(nowPlayingMedia);
    }

    Logger.d("[CastActivity] starting up, action: %s, current state: %s", getIntent().getAction(), castPlayerManager.getCurrentState());
    Logger.d("client: %s", mClient);
		if(getIntent().getAction() != null && getIntent().getAction().equals(Intent.CAST_MEDIA)) {

      /*
			Logger.d("Casting %s (%s)", nowPlayingMedia.title, nowPlayingMedia.viewOffset);

      // TODO: only show now playing if stopped?
//      if(castPlayerManager.getCurrentState().equals(PlayerState.STOPPED))

      if(mediaChange) {
        Logger.d("[CastActivity] MEDIA CHANGED!");

        init(true); // tell the chromecast to load the new media. The cast player activity will receive a notification of the new media and will update accordingly
      } else {

        showNowPlaying(castPlayerManager.getCurrentState().equals(PlayerState.STOPPED) || !mediaChange ? true : false);
        if (castPlayerManager.isSubscribed()) {
          init();
        } else {
          showInfoDialog(getResources().getString(R.string.connecting));
          castPlayerManager.subscribe(mClient);
        }
      }
		} else {
      Logger.d("[CastActivity] No action found.");
      if(castPlayerManager.getCurrentState().equals(PlayerState.STOPPED))
        finish();
      else {
        showNowPlaying(true);
      }
		}
		*/
	}

  @Override
  protected void onNewIntent(android.content.Intent intent) {
    super.onNewIntent(intent);
    // If the currently playing media is of a different type than the one being received, make sure to set the view
    PlexMedia newMedia = intent.getParcelableExtra(Intent.EXTRA_MEDIA);
    start(newMedia != null && nowPlayingMedia != null && !newMedia.getType().equals(nowPlayingMedia.getType()));
  }

  @Override
  public void showNowPlaying() {
    showNowPlaying(true);
  }

  @Override
  public void showNowPlaying(boolean setView) {
    super.showNowPlaying(setView);
    setupUI();
  }

  @Override
  public void onCastConnected(PlexClient _client) {
    super.onCastConnected(_client);
    hideInfoDialog();
    init();
  }

  private void setupUI() {
    Logger.d("setting progress to %d", getOffset(nowPlayingMedia));
    seekBar = (SeekBar)findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    seekBar.setMax(nowPlayingMedia.duration);
    seekBar.setProgress(getOffset(nowPlayingMedia) * 1000);

    Logger.d("setupUI, setting time display to %d", getOffset(nowPlayingMedia));
    setCurrentTimeDisplay(getOffset(nowPlayingMedia));
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
    uiShowing = true;
  }

  private void init() {
    init(false);
  }

  private void init(boolean forceLoad) {
    currentState = castPlayerManager.getCurrentState();
    Logger.d("castPlayerManager.getCurrentState(): %s", castPlayerManager.getCurrentState());
    if(castPlayerManager.getCurrentState() != PlayerState.STOPPED && !forceLoad)
      return;
    if (VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null) {
      nowPlayingMedia.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
        @Override
        public void success(String token) {
          Logger.d("Got transient token: %s", token);
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

  private void showInfoDialog(String text) {
    if(infoDialog == null) {
      infoDialog = new Dialog(this);
    }
    infoDialog.setContentView(R.layout.search_popup);
    infoDialog.setTitle(text);

    // TODO: Re-enable this
//    infoDialog.setCancelable(false);

    infoDialog.show();
  }

  private void hideInfoDialog() {
    if(infoDialog != null && infoDialog.isShowing()) {
      infoDialog.dismiss();
    }
  }

	private void beginPlayback() {
		Logger.d("duration: %s", nowPlayingMedia.duration);

		Logger.d("offset is %d", getOffset(nowPlayingMedia));



		if(castManager.isConnected()) {
        castPlayerManager.loadMedia(nowPlayingMedia, nowPlayingAlbum, getOffset(nowPlayingMedia));
		}
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
    if(castManager != null) {
      castManager.decrementUiCounter();
      castManager.removeVideoCastConsumer(castConsumer);
    }
    VoiceControlForPlexApplication.applicationPaused();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		Logger.d("[CastActivity] onDestroy");
		if (null != castManager) {
//			castManager.clearContext(this);
		}
		super.onDestroy();
	}

  @Override
	public void doPlayPause(View v) {
		try {
      Logger.d("doPlayPause, currentState: %s", currentState);
			if(currentState !=  PlayerState.PLAYING) {
				castPlayerManager.play();
			} else if(currentState ==  PlayerState.PLAYING) {
        castPlayerManager.pause();
			}
		} catch (Exception ex) {}
	}

	public void doRewind(View v) {
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString(position - 15000);
      if (Integer.parseInt(nowPlayingMedia.viewOffset) < 0) {
        position = 0;
        nowPlayingMedia.viewOffset = "0";
      }
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
	}

	public void doForward(View v) {
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString(position + 30000);
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
	}

  @Override
	public void doStop(View v) {
		try {
      castPlayerManager.stop();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

  public void doNext(View v) {
    Logger.d("doNext");
    castPlayerManager.doNext();
  }

  public void doPrevious(View v) {
    Logger.d("doPrevious");
    castPlayerManager.doPrevious();
  }

	@Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		Logger.d("stopped changing progress: %d", _seekBar.getProgress());
		try {
			nowPlayingMedia.viewOffset = Integer.toString(_seekBar.getProgress());
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    isSeeking = false;
	}

	private void setState(PlayerState state) {
		currentState = state;
		if(currentState ==  PlayerState.PAUSED) {
			playPauseButton.setImageResource(R.drawable.button_play);
		} else if(currentState ==  PlayerState.PLAYING) {
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
  public void onCastPlayerStateChanged(PlayerState state) {
    super.onCastPlayerStateChanged(state);
    Logger.d("[CastActivity] onCastPlayerStateChanged: %s", state);

    if(isSeeking) {
      isSeeking = false;
    }
    if(state == PlayerState.STOPPED) {
      Logger.d("[CastActivity] media player is idle, finishing");
      VoiceControlForPlexApplication.getInstance().cancelNotification();
      finish();
    } else if(uiShowing) {
      setState(state);
    }
    if(uiShowing)
      hideInfoDialog();
  }

  @Override
  public void onUnsubscribed() {
    super.onUnsubscribed();
    finish();
  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {
    position = seconds * 1000;
    if(!isSeeking)
      seekBar.setProgress(seconds*1000);
  }

  @Override
  public void onCastPlayerPlaylistAdvance(PlexMedia media) {
    nowPlayingMedia = media;
    setupUI();
    showNowPlaying(false);
  }

  @Override
  public void onCastSeek() {
    if(!nowPlayingMedia.getType().equals("music"))
      showInfoDialog(getString(R.string.please_wait));
  }
}
