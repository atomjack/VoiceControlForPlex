package com.atomjack.vcfp.interfaces;

import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.model.PlexClient;

public interface PlayerFragmentListener {
  // This will get passed to the activity with the key of the currently playing media, so the activity can set up the
  // fragment to display the media
  void onFoundPlayingMedia(Timeline timeline);
}
