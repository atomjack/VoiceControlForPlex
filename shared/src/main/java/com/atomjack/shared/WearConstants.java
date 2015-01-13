package com.atomjack.shared;

public class WearConstants {
  public final static String SPEECH_QUERY = "/speech_query";
  // remove this:
  public final static String GET_PLAYBACK_STATE = "/get_playback_state";
  // Used to send the image (Asset) of the currently playing media
  public final static String RECEIVE_MEDIA_IMAGE = "/receive_media_image";
  public final static String GET_PLAYING_MEDIA = "/get_playing_media";

  public final static String MEDIA_PLAYING = "/media_playing";
  public final static String MEDIA_STOPPED = "/media_stopped";
  public final static String MEDIA_PAUSED = "/media_paused";
  public final static String WEAR_UNAUTHORIZED = "/unauthorized";
  public final static String WEAR_PURCHASED = "/purchased";
  // The handheld app will ping the wearable to see if it exists, if the wearable purchase has not been made.
  // If the handheld receives a pong back, it will show a popup alerting the user to the option to purchase Wear support
  // TODO: Do this
  public final static String PING = "/ping";
  public final static String PONG = "/pong";

  // Options
  public final static String SET_WEAR_OPTIONS = "com.atomjack.vcfp.set_wear_options";
  public final static String PRIMARY_FUNCTION_VOICE_INPUT = "com.atomjack.vcfp.primary_function_voice_input";

  public final static String FROM_WEAR = "com.atomjack.vcfp.from_wear";

  // This is used to know whether or not the voice input was triggered from an initial launch of the app
  public final static String LAUNCHED = "com.atomjack.vcfp.launched";

  public final static String SPEECH_QUERY_RESULT = "com.atomjack.vcfp.speech_query_result";

  public final static String SEARCHING_FOR = "com.atomjack.vcfp.searching_for";
  public final static String SET_INFO = "com.atomjack.vcfp.set_info";
  public final static String INFORMATION = "com.atomjack.vcfp.information";

  // Playback manipulation
  public static final String ACTION_PAUSE = "com.atomjack.vcfp.action_pause";
  public static final String ACTION_PLAY = "com.atomjack.vcfp.action_play";
  public static final String ACTION_STOP = "com.atomjack.vcfp.action_stop";

  public static final String DISCONNECTED = "com.atomjack.vcfp.disconnected";

  // data
  public final static String IS_MEDIA_PLAYING = "com.atomjack.vcfp.is_media_playing";
  public final static String PLAYBACK_STATE = "com.atomjack.vcfp.playback_state";

  // media metadata
  public final static String MEDIA_TYPE = "com.atomjack.vcfp.media_type";
  public final static String MEDIA_TITLE = "com.atomjack.vcfp.media_title";
  public final static String IMAGE = "com.atomjack.vcfp.image";
  public final static String CLIENT_NAME = "com.atomjack.vcfp.client_name";
}
