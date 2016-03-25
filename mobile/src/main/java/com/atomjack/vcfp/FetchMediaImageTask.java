package com.atomjack.vcfp;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexMedia;

import java.io.InputStream;

import cz.fhucho.android.util.SimpleDiskCache;

public class FetchMediaImageTask extends AsyncTask<Void, Void, Void> {
  PlexMedia media;
  int width, height;
  String whichThumb;
  String imageKey;
  BitmapHandler bitmapHandler = null;
  public SimpleDiskCache mSimpleDiskCache;

  public FetchMediaImageTask(PlexMedia media, int width, int height, String whichThumb, String imageKey, BitmapHandler bitmapHandler) {
    this.media = media;
    this.width = width;
    this.height = height;
    this.bitmapHandler = bitmapHandler;
    this.whichThumb = whichThumb;
    this.imageKey = imageKey;
    mSimpleDiskCache = VoiceControlForPlexApplication.getInstance().mSimpleDiskCache;
  }

  public FetchMediaImageTask(PlexMedia media, int width, int height, String whichThumb, String imageKey) {
    this(media, width, height, whichThumb, imageKey, null);
  }

  @Override
  protected Void doInBackground(Void... params) {
    if (whichThumb == null)
      return null;
    final Bitmap bitmap = getCachedBitmap(imageKey);
    if (bitmap == null) {
      Logger.d("Fetching media thumb for %s at %dx%d with key %s", media.getTitle(), width, height, imageKey);
      media.server.findServerConnection(new ActiveConnectionHandler() {
        @Override
        public void onSuccess(final Connection connection) {
          new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
              Logger.d("No cached bitmap found, fetching");
              InputStream is = media.getThumb(width, height, whichThumb, connection);
              try {
                Logger.d("Saving cached bitmap with key %s", imageKey);
                mSimpleDiskCache.put(imageKey, is);
                Bitmap bm = getCachedBitmap(imageKey);
                if(bitmapHandler != null)
                  bitmapHandler.onSuccess(bm);
              } catch (Exception e) {
                e.printStackTrace();
                if(bitmapHandler != null)
                  bitmapHandler.onSuccess(null);
              }
              return null;
            }
          }.execute();
        }

        @Override
        public void onFailure(int statusCode) {
          Logger.d("Failed to find server connection for %s while searching for thumb for %s", media.server.name, media.getTitle());
        }
      });
    } else {
      Logger.d("Found cached thumb for %s at %dx%d with key %s", media.getTitle(), width, height, imageKey);
      if (bitmapHandler != null)
        bitmapHandler.onSuccess(bitmap);
    }
    return null;
  }

  private Bitmap getCachedBitmap(String key) {
    Bitmap bitmap = null;
    try {
//      Logger.d("Trying to get cached thumb: %s", key);
      SimpleDiskCache.BitmapEntry bitmapEntry = mSimpleDiskCache.getBitmap(key);
//      Logger.d("bitmapEntry: %s", bitmapEntry);
      if(bitmapEntry != null) {
        bitmap = bitmapEntry.getBitmap();
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return bitmap;


  }
}
