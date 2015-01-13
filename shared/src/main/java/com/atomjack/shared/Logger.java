package com.atomjack.shared;

import android.util.Log;

import java.util.regex.Pattern;

public class Logger
{

	private static Pattern XPlexTokenPattern = Pattern.compile("(X-Plex-Token=)([^&])+");

	private static String scrub(String text) {
		return XPlexTokenPattern.matcher(text).replaceAll("$1<XXXXXXXXXX>");
	}

	public static void i(String format, Object ... args)
  {
    String msg = scrub(String.format(format, args));
    Log.i("VoiceControlForPlex", msg);
  }

  public static void d(String format, Object ... args)
  {
    String msg = scrub(String.format(format, args));
    Log.d("VoiceControlForPlex", msg);
  }

  public static void w(String format, Object ... args)
  {
    String msg = scrub(String.format(format, args));
    Log.w("VoiceControlForPlex", msg);
  }

  public static void e(String format, Object ... args)
  {
    String msg = scrub(String.format(format, args));
    Log.e("VoiceControlForPlex", msg);
  }
}
