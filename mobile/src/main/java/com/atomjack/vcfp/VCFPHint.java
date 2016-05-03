package com.atomjack.vcfp;

import android.content.Context;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.net.PlexHttpClient;

public class VCFPHint {
  private TextView hintTextView;
  private PlexServer server;
  private Preferences prefs;
  private Context context;
  private Handler handler;
  private Runnable job;
  private boolean active = false;
  final Animation in = new AlphaAnimation(0.0f, 1.0f);
  final Animation out = new AlphaAnimation(1.0f, 0.0f);


  public VCFPHint(TextView textView) {
    this.context = VoiceControlForPlexApplication.getInstance().getApplicationContext();
    hintTextView = textView;
    prefs = VoiceControlForPlexApplication.getInstance().prefs;
    server = VoiceControlForPlexApplication.gsonRead.fromJson(prefs.get(Preferences.SERVER, ""), PlexServer.class);
    handler = new Handler();
    in.setDuration(3000);
    out.setDuration(3000);
    out.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {

      }

      @Override
      public void onAnimationEnd(Animation animation) {
        hintTextView.setText("");
      }

      @Override
      public void onAnimationRepeat(Animation animation) {

      }
    });
  }

  public void start() {
    active = true;
    handler.removeCallbacks(job);
    handler.removeCallbacks(onFinishSuccess);
    handler.removeCallbacks(onFinishFailure);
    int delay = Utils.getRandomInt(10, 20);
    Logger.d("Delaying %d seconds for hint", delay);
    job = () -> doHint();
    handler.postDelayed(job, delay*1000);
  }

  public void stop() {
    active = false;
    handler.removeCallbacks(job);
    handler.removeCallbacks(onFinishSuccess);
    handler.removeCallbacks(onFinishFailure);
    hintTextView.startAnimation(out);
  }

  private class HintRunnable {
    public void success() {
      if(active) {
        server = VoiceControlForPlexApplication.gsonRead.fromJson(prefs.get(Preferences.SERVER, ""), PlexServer.class);
        final int delay = Utils.getRandomInt(5, 15);
        Logger.d("Finished doing hint, delaying %d before next hint", delay);
        handler.postDelayed(() -> {
          hintTextView.startAnimation(out);
          handler.postDelayed(job, delay * 1000);
        }, 15 * 1000); // leave this hint up for 15 seconds, then delay for the random amount
      }
    }

    public void failure() {
      Logger.d("Failed, doing hint immediately");
      handler.post(job);
    }
  }

  private HintRunnable onFinish = new HintRunnable();

  private Runnable onFinishSuccess = () -> onFinish.success();

  private Runnable onFinishFailure = () -> onFinish.failure();

  public void doHint() {
    if(active) {
      int a = Utils.getRandomInt(0, 13);
      if (a == 0) {
        hintWatchMovie();
      } else if (a == 1) {
        hintWatchOnDeckEpisode();
      } else if (a == 2) {
        hintWatchSeasonEpisode();
      } else if (a == 3) {
        hintWatchSeasonEpisodeAlternate();
      } else if (a == 4) {
        hintWatchEpisode();
      } else if (a == 5) {
        hintListenToSong();
      } else if (a == 6) {
        hintListenToAlbumByArtist();
      } else if (a == 7) {
        hintListenToAlbum();
      } else if (a == 8) {
        hintListenToArtist();
      } else if (a == 9) {
        hintWatchRandomEpisode();
      } else if (a == 10) {
        hintWhatsNew();
      } else if (a == 11) {
        hintWhatsNewMovies();
      } else if (a == 12) {
        hintWhatsOnDeck();
      }
    }
  }

  private void setText(String text) {
    hintTextView.setText(String.format("%s '%s'", context.getString(R.string.try_string), text));
    hintTextView.startAnimation(in);
  }

  // hint methods

  private void hintWatchMovie() {
    PlexHttpClient.getRandomMovie(server, media -> {
      if(media != null) {
        setText(String.format(context.getString(R.string.hint_watch_movie), media.getTitle()));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  private void hintWatchOnDeckEpisode() {
    PlexHttpClient.getRandomOnDeck(server, media -> {
      if(media != null) {
        setText(String.format(context.getString(R.string.hint_watch_movie), media.getTitle()));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  private void hintWatchRandomEpisode() {
    PlexHttpClient.getRandomShow(server, show -> {
      if(show != null) {
        setText(String.format(context.getString(R.string.hint_watch_random_episode), show.title));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  // watch season x episode y of show
  private void hintWatchSeasonEpisode() {
    PlexHttpClient.getRandomEpisode(server, media -> {
      if(media != null) {
        PlexVideo video = (PlexVideo) media;
        setText(String.format(context.getString(R.string.hint_watch_season_episode), video.parentIndex, video.index, video.getTitle()));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  private void hintWatchSeasonEpisodeAlternate() {
    PlexHttpClient.getRandomEpisode(server, media -> {
      if(media != null) {
        Logger.d("Got media: %s", media.getTitle());
        PlexVideo video = (PlexVideo) media;
        setText(String.format(context.getString(R.string.hint_watch_season_episode2), video.getTitle(), video.parentIndex, video.index));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  private void hintWatchEpisode() {
    PlexHttpClient.getRandomEpisode(server, media -> {
      if(media != null) {
        PlexVideo video = (PlexVideo) media;
        Logger.d("[VCFPHint] video: %s %s", video.getEpisodeTitle(), video.getTitle());
        setText(String.format(context.getString(R.string.hint_watch_episode), video.getEpisodeTitle(), video.getTitle()));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  // listen to song by artist
  private void hintListenToSong() {
    PlexHttpClient.getRandomSong(server, media -> {
      if(media != null) {
        PlexTrack track = (PlexTrack) media;
        setText(String.format(context.getString(R.string.hint_listen_to_song), track.title, track.getArtist()));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  // listen to album by artist
  private void hintListenToAlbumByArtist() {
    PlexHttpClient.getRandomAlbum(server, directory -> {
      if(directory != null) {
        setText(String.format(context.getString(R.string.hint_listen_to_album_by_artist), directory.title, directory.parentTitle));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  // listen to album
  private void hintListenToAlbum() {
    PlexHttpClient.getRandomAlbum(server, directory -> {
      if(directory != null) {
        setText(String.format(context.getString(R.string.hint_listen_to_album), directory.title));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  // listen to artist
  private void hintListenToArtist() {
    PlexHttpClient.getRandomArtist(server, directory -> {
      if(directory != null) {
        setText(String.format(context.getString(R.string.hint_listen_to_artist), directory.title));
        handler.post(onFinishSuccess);
      } else {
        handler.post(onFinishFailure);
      }
    });
  }

  private void hintWhatsNew() {
    setText(context.getString(R.string.hint_whats_new));
  }

  private void hintWhatsNewMovies() {
    setText(context.getString(R.string.hint_whats_new_movies));
  }

  private void hintWhatsOnDeck() {
    setText(context.getString(R.string.hint_whats_on_deck));
  }
}
