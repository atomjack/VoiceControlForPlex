package com.atomjack.vcfpht;

import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atomjack.googlesearchplexcontrol.R;
import com.atomjack.vcfpht.model.PlexClient;

public class ClientListAdapter extends BaseAdapter {
	private final Context context;
	private final List<PlexClient> mData;
	
	private Dialog dialog;
	
	public ClientListAdapter(Context context, List<PlexClient> data) {
		mData = data;
		this.context = context;
	}
   
	@Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int arg0) {
        return arg0;
    }
    
    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
//        String key = mKeys[pos];
        PlexClient server = (PlexClient)getItem(pos);
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
