package com.atomjack.vcfp;

import android.content.Context;
import android.content.pm.PackageInfo;

import com.atomjack.vcfp.activities.VCFPActivity;

import cz.fhucho.android.util.SimpleDiskCache;

public class VCFPSingleton {
  private static VCFPSingleton instance;
  private PlexSubscription plexSubscription;
  private CastPlayerManager castPlayerManager;
  private SimpleDiskCache mSimpleDiskCache;

  public static synchronized VCFPSingleton getInstance()
  {
    // Return the instance
    if(instance == null)
      instance = new VCFPSingleton();
    return instance;
  }

  public PlexSubscription getPlexSubscription() {
    if(plexSubscription == null)
      plexSubscription = new PlexSubscription();
    return plexSubscription;
  }

  public CastPlayerManager getCastPlayerManager(Context context) {
    if(castPlayerManager == null)
      castPlayerManager = new CastPlayerManager(context);
    return castPlayerManager;
  }

  public SimpleDiskCache getSimpleDiskCache(Context context) {
    if(mSimpleDiskCache == null) {
      try {
        PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        mSimpleDiskCache = SimpleDiskCache.open(context.getCacheDir(), pInfo.versionCode, Long.parseLong(Integer.toString(10 * 1024 * 1024)));
        Logger.d("Cache initialized");
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    return mSimpleDiskCache;
  }

  private VCFPSingleton()
  {
    // Constructor hidden because this is a singleton
  }

  public void customSingletonMethod()
  {
    // Custom method
  }

  public Object clone() throws CloneNotSupportedException
  {
    throw new CloneNotSupportedException();
  }
}
