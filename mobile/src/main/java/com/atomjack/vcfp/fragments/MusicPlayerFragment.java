package com.atomjack.vcfp.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexDirectory;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.services.LocalMusicService;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MusicPlayerFragment extends Fragment {
  private PlexTrack track; // the current track

  MusicPlayerListener musicPlayerListener;

  private LocalMusicService musicService;
  private Intent playIntent;
  private boolean musicBound = false;
  private MediaContainer mediaContainer;

  private Handler handler;

  // These will be set on first load, then used subsequently to resize the cover art image
  int posterWidth = -1, posterHeight = -1;

  @Bind(R.id.nowPlayingOnClient)
  TextView nowPlayingOnClient;
  @Bind(R.id.nowPlayingArtist)
  TextView nowPlayingArtist;
  @Bind(R.id.nowPlayingTitle)
  TextView nowPlayingTitle;
  @Bind(R.id.nowPlayingAlbum)
  TextView nowPlayingAlbum;
  @Bind(R.id.nowPlayingPosterContainer)
  FrameLayout nowPlayingPosterContainer;
  @Bind(R.id.nowPlayingPoster)
  ImageView nowPlayingPoster;

  // Controller elements
  @Bind(R.id.currentTimeView)
  TextView currentTimeView;
  @Bind(R.id.seekBar)
  SeekBar seekBar;
  @Bind(R.id.durationView)
  TextView durationView;
  @Bind(R.id.playPauseButton)
  ImageButton playPauseButton;
  @Bind(R.id.stopButton)
  ImageButton stopButton;
  @Bind(R.id.nextButton)
  ImageButton nextButton;
  @Bind(R.id.micButton)
  ImageButton micButton;
  @Bind(R.id.previousButton)
  ImageButton previousButton;

  public MusicPlayerFragment() {
    Logger.d("[MusicPlayerFragment]");
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    Logger.d("[MusicPlayerFragment] onCreateView");
    if(savedInstanceState != null) {
      track = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_MEDIA);
      mediaContainer = savedInstanceState.getParcelable(com.atomjack.shared.Intent.EXTRA_ALBUM);
    }

    View view = inflater.inflate(R.layout.fragment_music_player, container, false);

    ButterKnife.bind(this, view);

    handler = new Handler();

    setRetainInstance(true);

    if(playIntent == null) {
      playIntent = new Intent(getActivity().getApplicationContext(), LocalMusicService.class);
      getActivity().getApplicationContext().bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
      getActivity().getApplicationContext().startService(playIntent);
    }

    showNowPlaying();

    return view;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Logger.d("[MusicPlayerFragment] onConfigurationChanged");
    showNowPlaying();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    try {
      musicPlayerListener = (MusicPlayerListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString()
              + " must implement OnHeadlineSelectedListener");
    }
  }

  public void init(Intent intent, final Runnable onFinish) {
    track = intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_MEDIA);
    if(intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_ALBUM) != null) {
      PlexDirectory directory = intent.getParcelableExtra(com.atomjack.shared.Intent.EXTRA_ALBUM);
      Logger.d("Directory: %s", directory.key);
      PlexHttpClient.getChildren(directory, track.server, new PlexHttpMediaContainerHandler() {
        @Override
        public void onSuccess(MediaContainer mc) {
          mediaContainer = mc;
          // Add the server to each track
          for(PlexTrack t : mediaContainer.tracks)
            t.server = track.server;

          if(onFinish != null)
            onFinish.run();
        }

        @Override
        public void onFailure(Throwable error) {

        }
      });
    } else if(intent.getStringExtra(com.atomjack.shared.Intent.EXTRA_PLAYQUEUE) != null) {
      Logger.d("Play queue id: %s", intent.getStringExtra(com.atomjack.shared.Intent.EXTRA_PLAYQUEUE));
      PlexHttpClient.get(track.server, intent.getStringExtra(com.atomjack.shared.Intent.EXTRA_PLAYQUEUE), new PlexHttpMediaContainerHandler() {
        @Override
        public void onSuccess(MediaContainer mc) {
          mediaContainer = mc;
          // Add the server to each track
          for(PlexTrack t : mediaContainer.tracks)
            t.server = track.server;

          if(onFinish != null)
            onFinish.run();
        }

        @Override
        public void onFailure(Throwable error) {
          // TODO: Handle
        }
      });
    } else {
      // Only playing a single track

    }
  }


  private SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      Logger.d("Setting time to %s", VoiceControlForPlexApplication.secondsToTimecode(progress));
      currentTimeView.setText(VoiceControlForPlexApplication.secondsToTimecode(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
      musicService.seek(seekBar.getProgress()*1000);
    }
  };

  @OnClick(R.id.previousButton)
  public void doPrevious(View v) {
    musicService.doPrevious();
  }

  @OnClick(R.id.nextButton)
  public void doNext(View v) {
    musicService.doNext();
  }


  @OnClick(R.id.playPauseButton)
  public void doPlayPause(View v) {
    Logger.d("[MusicPlayerFragment] doPlayPause");
    musicService.doPlayPause();
  }

  @OnClick(R.id.stopButton)
  public void doStop(View v) {
    Logger.d("[MusicPlayerFragment] doStop");
    musicService.doStop();
    Intent serviceIntent = new Intent(getActivity().getApplicationContext(), LocalMusicService.class);
    getActivity().getApplicationContext().stopService(serviceIntent);
    musicService = null;
    musicPlayerListener.finished();
  }

  @OnClick(R.id.micButton)
  public void doMic(View v) {

  }


  private void showNowPlaying() {
    currentTimeView.setText(track.duration / 1000 < 60*60 ? "00:00" : "00:00:00");
    durationView.setText(VoiceControlForPlexApplication.secondsToTimecode(track.duration / 1000));
    seekBar.setMax(track.duration / 1000);
    seekBar.setProgress(0);
    seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

    nowPlayingOnClient.setVisibility(View.GONE);
    Logger.d("Setting artist/album to %s/%s", track.getArtist(), track.getAlbum());
    nowPlayingArtist.setText(track.getArtist());
    nowPlayingAlbum.setText(track.getAlbum());
    nowPlayingTitle.setText(track.title);

    if(nowPlayingPosterContainer != null) {
      if(posterHeight == -1 || posterWidth == -1) {
        ViewTreeObserver vto = nowPlayingPosterContainer.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
              nowPlayingPosterContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            posterWidth = nowPlayingPosterContainer.getMeasuredWidth();
            posterHeight = nowPlayingPosterContainer.getMeasuredHeight();
            setMainImage();
          }
        });
      } else {
        setMainImage();
      }

    }
  }

  private void setMainImage() {
    Logger.d("[MusicPlayerFragment] Fetching main image");

    VoiceControlForPlexApplication.getInstance().fetchMediaThumb(track, posterWidth, posterHeight, track.thumb != null ? track.thumb : track.grandparentThumb, track.getImageKey(PlexMedia.IMAGE_KEY.LOCAL_MUSIC_THUMB), new BitmapHandler() {
      @Override
      public void onSuccess(final Bitmap bitmap) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            nowPlayingPoster.setImageBitmap(bitmap);
          }
        });
      }
    });
  }

  @Override
  public void onDestroy() {
    getActivity().stopService(playIntent);
    musicService = null;
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
  }

  //connect to the service
  private ServiceConnection musicConnection = new ServiceConnection(){

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      LocalMusicService.MusicBinder binder = (LocalMusicService.MusicBinder)service;
      //get service
      musicService = binder.getService();
      musicService.setMediaContainer(mediaContainer);

      binder.setListener(new MusicServiceListener() {
        @Override
        public void onTimeUpdate(PlayerState state, int time) {
//          Logger.d("[MusicPlayerFragment] got time update, state: %s, time: %d", state, time);
          currentTimeView.setText(VoiceControlForPlexApplication.secondsToTimecode(time / 1000));
          seekBar.setProgress(time / 1000);
          if(state == PlayerState.PAUSED)
            playPauseButton.setImageResource(R.drawable.button_play);
          else if(state == PlayerState.PLAYING)
            playPauseButton.setImageResource(R.drawable.button_pause);
        }

        @Override
        public void onTrackChange(PlexTrack t) {
          Logger.d("[MusicPlayerFragment] onTrackChange: %s", t.getTitle());
          track = t;
          showNowPlaying();
        }

        @Override
        public void onFinished() {
          musicPlayerListener.finished();
        }
      });

      musicService.reset();
      musicService.playSong();
      musicBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      musicBound = false;
    }
  };

  public interface MusicPlayerListener {
    void finished();
  }

  public interface MusicServiceListener {
    void onTimeUpdate(PlayerState state, int time);
    void onTrackChange(PlexTrack track);
    void onFinished();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(com.atomjack.shared.Intent.EXTRA_MEDIA, track);
    outState.putParcelable(com.atomjack.shared.Intent.EXTRA_ALBUM, mediaContainer);
  }
}
