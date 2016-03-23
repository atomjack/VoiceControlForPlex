package com.atomjack.shared;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
  public final static String PREFS = "VoiceControlForPlexPrefs";

  private SharedPreferences mPrefs;
  private SharedPreferences.Editor mPrefsEditor;

  public final static String FEEDBACK_VOICE = "pref.feedback.voice";
  public final static String ERRORS_VOICE = "pref.errors.voice";
  public final static String SAVED_SERVERS = "pref.saved_servers";
  public final static String SAVED_CLIENTS = "pref.saved_clients";
  public final static String SAVED_CAST_CLIENTS = "pref.saved_cast_clients";
  public final static String UUID = "pref.uuid";
  public final static String AUTHENTICATION_TOKEN = "pref.authentication_token";
  public final static String CLIENT = "Client";
  public final static String SERVER = "Server";
  public final static String RESUME = "resume";
  public final static String FEEDBACK = "feedback";
  public final static String ERRORS = "errors";
  public final static String PLEX_USERNAME = "pref.plex_username";
  public final static String PLEX_EMAIL = "pref.plex_email";
  public final static String HAS_SHOWN_WEAR_PURCHASE_POPUP = "pref.has_shown_wear_purchase_popup";
  public final static String NUM_CINEMA_TRAILERS = "pref.num_cinema_trailers";
  public final static String CHROMECAST_VIDEO_QUALITY_LOCAL = "pref.chromecast_video_quality_local";
  public final static String CHROMECAST_VIDEO_QUALITY_REMOTE = "pref.chromecast_video_quality_remote";
  public final static String LOCAL_VIDEO_QUALITY_LOCAL = "pref.local_video_quality_local";
  public final static String LOCAL_VIDEO_QUALITY_REMOTE = "pref.local_video_quality_remote";
  public final static String SUBSCRIBED_CLIENT = "pref.subscribed_client";
  public final static String LAST_SERVER_SCAN = "pref.last_server_scan";
  public final static String SERVER_SCAN_FINISHED = "pref.server_scan_finished";
  public final static String FIRST_TIME_SETUP_COMPLETED = "pref.first_time_setup_completed";
  public final static String IMAGE_CACHE_VERSION = "pref.image_cache_version";
  public final static String CRASHED = "pref.crashed";
  public final static String SHOW_USAGE_HINTS = "pref.show_usage_hints";
  public final static String GOOGLE_NOW_LAUNCH_NOW_PLAYING = "pref.google_now_launch_now_playing";
  // The first time the local music player is run, these values will be defined, so that music posters can be fetched from within PlexSearchService
  public final static String MUSIC_POSTER_WIDTH = "pref.music_poster_width";
  public final static String MUSIC_POSTER_HEIGHT = "pref.music_poster_height";

  public final static String ACTIVE_CONNECTION_EXPIRES = "pref.active_connection_expires";
  public final static String ACTIVE_CONNECTION = "pref.active_connection";



  public Preferences(Context context) {
    mPrefs = context.getSharedPreferences(PREFS, context.MODE_PRIVATE);
    mPrefsEditor = mPrefs.edit();
  }

  public String get(String pref, String defaultValue) {
    return mPrefs.getString(pref, defaultValue);
  }

  public String getString(String pref) {
    return mPrefs.getString(pref, null);
  }

  public int get(String pref, int defaultValue) {
    return mPrefs.getInt(pref, defaultValue);
  }

  public long get(String pref, long defaultValue) { return mPrefs.getLong(pref, defaultValue); }

  public void put(String pref, String value) {
    mPrefsEditor.putString(pref, value);
    mPrefsEditor.commit();
  }

  public void put(String pref, int value) {
    mPrefsEditor.putInt(pref, value);
    mPrefsEditor.commit();
  }

  public void put(String pref, long value) {
    mPrefsEditor.putLong(pref, value);
    mPrefsEditor.commit();
  }

  public void put(String pref, boolean value) {
    mPrefsEditor.putBoolean(pref, value);
    mPrefsEditor.commit();
  }

  public boolean get(String pref, boolean defaultValue) {
    return mPrefs.getBoolean(pref, defaultValue);
  }

  public void remove(String pref) {
    mPrefsEditor.remove(pref);
    mPrefsEditor.commit();
  }

  public String getUUID() {
    String uuid = getString(UUID);
    if(uuid == null) {
      uuid = java.util.UUID.randomUUID().toString();
      put(UUID, uuid);
    }
    return uuid;
  }
}
