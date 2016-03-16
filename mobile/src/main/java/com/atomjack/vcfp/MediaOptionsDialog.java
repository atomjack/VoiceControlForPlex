package com.atomjack.vcfp;

import android.app.AlertDialog;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.atomjack.vcfp.adapters.StreamAdapter;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.Stream;

import java.util.List;

public class MediaOptionsDialog extends AlertDialog.Builder {
  private AppCompatActivity activity;
  private LocalStreamChangeListener localStreamChangeListener;

  public MediaOptionsDialog(Context context, final PlexMedia media, final PlexClient client) {
    super(context);
    activity = (AppCompatActivity)context;

    final List<Stream> audioStreams = media.getStreams(Stream.AUDIO);
    final List<Stream> subtitleStreams = media.getStreams(Stream.SUBTITLE);

    View layout = activity.getLayoutInflater().inflate(R.layout.media_options_dialog, null);

    if(subtitleStreams.size() > 0) {
      Spinner subtitlesSpinner = (Spinner) layout.findViewById(R.id.subtitlesSpinner);
      StreamAdapter subtitlesStreamAdapter = new StreamAdapter(activity, android.R.layout.simple_spinner_dropdown_item, subtitleStreams);
      subtitlesSpinner.setAdapter(subtitlesStreamAdapter);
      subtitlesSpinner.setSelection(subtitleStreams.indexOf(media.getActiveStream(Stream.SUBTITLE)), false);

      subtitlesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          Stream stream = subtitleStreams.get(position);
          if (!stream.isActive()) {
            if(localStreamChangeListener != null)
              localStreamChangeListener.setStream(stream);
            else
              client.setStream(stream);
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
      StreamAdapter audioStreamAdapter = new StreamAdapter(activity, android.R.layout.simple_spinner_dropdown_item, audioStreams);
      audioSpinner.setAdapter(audioStreamAdapter);
      audioSpinner.setSelection(audioStreams.indexOf(media.getActiveStream(Stream.AUDIO)), false);

      audioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          Stream stream = audioStreams.get(position);
          if (!stream.isActive()) {
            if(localStreamChangeListener != null)
              localStreamChangeListener.setStream(stream);
            else
              client.setStream(stream);
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

  public void setLocalStreamChangeListener(LocalStreamChangeListener listener) {
    localStreamChangeListener = listener;
  }

  public interface LocalStreamChangeListener {
    void setStream(Stream stream);
  }

}
