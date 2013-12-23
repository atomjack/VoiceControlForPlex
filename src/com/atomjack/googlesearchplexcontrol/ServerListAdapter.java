package com.atomjack.googlesearchplexcontrol;

import java.util.concurrent.ConcurrentHashMap;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atomjack.googlesearchplexcontrol.model.PlexServer;

public class ServerListAdapter extends BaseAdapter {
	private final Context context;
	private final ConcurrentHashMap<String, PlexServer> mData;
	
	private String[] mKeys;
	
	private Dialog dialog;
	
	public ServerListAdapter(Context context, ConcurrentHashMap<String, PlexServer> data) {
		mData = data;
		mKeys = mData.keySet().toArray(new String[data.size()]);
		this.context = context;
	}
   
	@Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(mKeys[position]);
    }

    @Override
    public long getItemId(int arg0) {
        return arg0;
    }
    
    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        String key = mKeys[pos];
        PlexServer server = (PlexServer)getItem(pos);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.server_list_item, parent, false);
        TextView textView = (TextView) rowView.findViewById(R.id.serverListTextView);
        textView.setText(server.getName());
        
        
        return rowView;
    }

	public Dialog getDialog() {
		return dialog;
	}

	public void setDialog(Dialog dialog) {
		this.dialog = dialog;
	}
}
