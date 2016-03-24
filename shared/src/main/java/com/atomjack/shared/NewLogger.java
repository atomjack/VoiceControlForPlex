package com.atomjack.shared;

import android.util.Log;

import java.util.regex.Pattern;

public class NewLogger {
  private static Pattern XPlexTokenPattern = Pattern.compile("(X-Plex-Token=)([^&])+");
  private String className;

  public NewLogger(Object object) {
    className = object.getClass().getSimpleName();
  }

  private String scrub(String text) {
    return XPlexTokenPattern.matcher(text).replaceAll("$1<XXXXXXXXXX>");
  }

  public void i(String format, Object ... args)
  {
    String msg = String.format("[%s] %s", className, scrub(String.format(format, args)));
    Log.i("VoiceControlForPlex", msg);
  }

  public void d(String format, Object ... args) {
    String msg = String.format("[%s] %s", className, scrub(String.format(format, args)));
    Log.d("VoiceControlForPlex", msg);
  }

  public void w(String format, Object ... args)
  {
    String msg = String.format("[%s] %s", className, scrub(String.format(format, args)));
    Log.w("VoiceControlForPlex", msg);
  }

  public void e(String format, Object ... args)
  {
    String msg = String.format("[%s] %s", className, scrub(String.format(format, args)));
    Log.e("VoiceControlForPlex", msg);
  }
}
