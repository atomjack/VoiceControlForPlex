package com.atomjack.vcfp.interfaces;

import com.atomjack.vcfp.model.PlexTrack;

/*
 * This interface is used for MusicPlayerFragment to send commands to MainActivity,
 * which sends those commands on to LocalMusicService.
 */
public interface MusicPlayerListener {
  void doNext();
  void doPlay();
  void doPause();
  void doPrevious();
  void doStop();
  PlexTrack getTrack();
  void seek(int time);
}
