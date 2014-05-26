package com.atomjack.vcfp;

import android.util.Log;

public class Logger
{
  public static void i(String format, Object ... args)
  {
    String msg = String.format(format, args);
    Log.i("VoiceControlForPlex", msg);
  }

  public static void d(String format, Object ... args)
  {
    String msg = String.format(format, args);
    Log.d("VoiceControlForPlex", msg);
  }

  public static void w(String format, Object ... args)
  {
    String msg = String.format(format, args);
    Log.w("VoiceControlForPlex", msg);
  }

  public static void e(String format, Object ... args)
  {
    String msg = String.format(format, args);
    Log.e("VoiceControlForPlex", msg);
  }
}
