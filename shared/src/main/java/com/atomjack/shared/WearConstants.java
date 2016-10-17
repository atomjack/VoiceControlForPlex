package com.atomjack.shared;

public class WearConstants {
  public final static String SPEECH_QUERY = "/vcfp/speech_query";
  // remove this:
  public final static String GET_PLAYBACK_STATE = "/vcfp/get_playback_state";
  // Used to send the image (Asset) of the currently playing media
  public final static String RECEIVE_MEDIA_IMAGE = "/vcfp/receive_media_image";
  public final static String GET_PLAYING_MEDIA = "/vcfp/get_playing_media";

  public final static String MEDIA_PLAYING = "/vcfp/media_playing";
  public final static String MEDIA_STOPPED = "/vcfp/media_stopped";
  public final static String MEDIA_PAUSED = "/vcfp/media_paused";
  public final static String WEAR_UNAUTHORIZED = "/vcfp/unauthorized";
  public final static String WEAR_PURCHASED = "/vcfp/purchased";
  // The handheld app will ping the wearable to see if it exists, if the wearable purchase has not been made.
  // If the handheld receives a pong back, it will show a popup alerting the user to the option to purchase Wear support
  public final static String PING = "/vcfp/ping";
  public final static String PONG = "/vcfp/pong";

  public final static String GET_DEVICE_LOGS = "/vcfp/get_device_logs";

  // Options
  public final static String SET_WEAR_OPTIONS = "com.atomjack.vcfp.set_wear_options";
  public final static String PRIMARY_FUNCTION_VOICE_INPUT = "com.atomjack.vcfp.primary_function_voice_input";

  public final static String FROM_WEAR = "com.atomjack.vcfp.from_wear";

  // This is used to know whether or not the voice input was triggered from an initial launch of the app
  public final static String LAUNCHED = "com.atomjack.vcfp.launched";
  public final static String RETRY_GET_PLAYBACK_STATE = "com.atomjack.vcfp.retry_get_playback_state";

  // key of intent extra containing the wear device's logs
  public final static String LOG_CONTENTS = "/vcfp/log_contents";

  public final static String FINISH = "com.atomjack.vcfp.finish";

  public final static String SPEECH_QUERY_RESULT = "com.atomjack.vcfp.speech_query_result";

  public final static String SEARCHING_FOR = "com.atomjack.vcfp.searching_for";
  public final static String SET_INFO = "com.atomjack.vcfp.set_info";
  public final static String INFORMATION = "com.atomjack.vcfp.information";

  // Playback manipulation
  public static final String ACTION_PAUSE = "/vcfp/com.atomjack.vcfp.intent.action_pause";
  public static final String ACTION_PLAY = "/vcfp/com.atomjack.vcfp.intent.action_play";
  public static final String ACTION_STOP = "/vcfp/com.atomjack.vcfp.intent.action_stop";

  public static final String DISCONNECTED = "com.atomjack.vcfp.disconnected";

  // data
  public final static String IS_MEDIA_PLAYING = "com.atomjack.vcfp.is_media_playing";
  public final static String PLAYBACK_STATE = "com.atomjack.vcfp.playback_state";

  // media metadata
  public final static String MEDIA_TYPE = "com.atomjack.vcfp.media_type";
  public final static String MEDIA_TITLE = "com.atomjack.vcfp.media_title";
  public final static String MEDIA_SUBTITLE = "com.atomjack.vcfp.media_episode_title";
  public final static String IMAGE = "com.atomjack.vcfp.image";
  public final static String CLIENT_NAME = "com.atomjack.vcfp.client_name";
}
