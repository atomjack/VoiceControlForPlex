package com.atomjack.vcfp;

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

  public void put(String pref, String value) {
    mPrefsEditor.putString(pref, value);
    mPrefsEditor.commit();
  }

  public void put(String pref, int value) {
    mPrefsEditor.putInt(pref, value);
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
