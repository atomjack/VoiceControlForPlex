package com.atomjack.vcfp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.atomjack.vcfp.model.Stream;

import java.util.List;

public class StreamAdapter extends ArrayAdapter<Stream> {
  Context context;
  private List<Stream> streams;

  public StreamAdapter(Context c, int resource, List<Stream> s) {
    super(c, resource, s);
    streams = s;
    context = c;
  }

  @Override
  public int getCount() {
    return streams.size();
  }

  @Override
  public Stream getItem(int position) {
    return streams.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getDropDownView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      convertView = vi.inflate(com.atomjack.vcfp.R.layout.stream_dropdown_item, parent, false);
    }
    ((TextView) convertView).setText(streams.get(position).getTitle());
    return convertView;

  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    TextView textView = (TextView) View.inflate(context, android.R.layout.simple_spinner_item, null);
    textView.setText(streams.get(position).getTitle());
    return textView;
  }
}
