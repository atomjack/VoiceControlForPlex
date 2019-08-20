package com.atomjack.vcfp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PorterDuff;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import com.atomjack.vcfp.adapters.StreamAdapter;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.Stream;

import java.util.List;

public class MediaOptionsDialog extends AlertDialog.Builder {
  private AppCompatActivity activity;
  private StreamChangeListener streamChangeListener;

  public MediaOptionsDialog(final Context context, final PlexMedia media, final PlexClient client) {
    super(context);
    activity = (AppCompatActivity)context;

    final List<Stream> audioStreams = media.getStreams(Stream.AUDIO);
    final List<Stream> subtitleStreams = media.getStreams(Stream.SUBTITLE);

    View layout = activity.getLayoutInflater().inflate(R.layout.media_options_dialog, null);

    if(subtitleStreams.size() > 0) {
      Spinner subtitlesSpinner = (Spinner) layout.findViewById(R.id.subtitlesSpinner);
      subtitlesSpinner.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.white), PorterDuff.Mode.SRC_ATOP);
      StreamAdapter subtitlesStreamAdapter = new StreamAdapter(activity, android.R.layout.simple_spinner_dropdown_item, subtitleStreams);
      subtitlesSpinner.setAdapter(subtitlesStreamAdapter);
      subtitlesSpinner.setSelection(subtitleStreams.indexOf(media.getActiveStream(Stream.SUBTITLE)), false);

      subtitlesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          ((TextView)parent.getChildAt(0)).setTextColor(ContextCompat.getColor(context, R.color.white));

          Stream stream = subtitleStreams.get(position);
          if (!stream.isActive()) {
            if(streamChangeListener != null)
              streamChangeListener.setStream(stream);
            media.setActiveStream(stream);
          }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      });
    } else {
      layout.findViewById(R.id.subtitlesRow).setVisibility(View.GONE);
    }

    if(audioStreams.size() > 0) {
      Spinner audioSpinner = (Spinner) layout.findViewById(R.id.audioSpinner);
      audioSpinner.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.white), PorterDuff.Mode.SRC_ATOP);

      StreamAdapter audioStreamAdapter = new StreamAdapter(activity, android.R.layout.simple_spinner_dropdown_item, audioStreams);
      audioSpinner.setAdapter(audioStreamAdapter);
      audioSpinner.setSelection(audioStreams.indexOf(media.getActiveStream(Stream.AUDIO)), false);

      audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          ((TextView)parent.getChildAt(0)).setTextColor(ContextCompat.getColor(context, R.color.white));

          Stream stream = audioStreams.get(position);
          if (!stream.isActive()) {
            if(streamChangeListener != null) {
              streamChangeListener.setStream(stream);
            }
            media.setActiveStream(stream);
          }

        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      });
    } else {
      // For some reason, no audio streams found, so hide the row
      layout.findViewById(R.id.audioRow).setVisibility(View.GONE);
    }

    setView(layout);
  }

  public void setStreamChangeListener(StreamChangeListener listener) {
    streamChangeListener = listener;
  }

  public interface StreamChangeListener {
    void setStream(Stream stream);
  }

}
