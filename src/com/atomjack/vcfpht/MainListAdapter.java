package com.atomjack.vcfpht;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atomjack.vcfpht.model.MainSetting;

public class MainListAdapter extends ArrayAdapter<MainSetting> {
	Context context;
    int layoutResourceId; 
    MainSetting[] data = null;
    
	public MainListAdapter(Context context, int layoutResourceId,
			MainSetting[] data) {
		super(context, layoutResourceId, data);
		this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
//        Log.v(MainActivity.TAG, "context: " + context);
	}

	@Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        SettingHolder holder = null;
        if(row == null)
        {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);
           
            holder = new SettingHolder();
            holder.line1 = (TextView)row.findViewById(R.id.mainSettingItem1);
            holder.line2 = (TextView)row.findViewById(R.id.mainSettingItem2);
            holder.helpButton = (ImageButton)row.findViewById(R.id.settingRowHelpButton);
            
            row.setTag(holder);
        }
        else
        {
            holder = (SettingHolder)row.getTag();
        }
        
        MainSetting item = data[position];
        holder.line1.setText(item.line1);
        holder.line2.setText(item.line2);
        holder.tag = item.tag;
        holder.helpButton.setTag(item.tag);
        return row;
    }
	
	static class SettingHolder {
		TextView line1;
		TextView line2;
		ImageButton helpButton;
		String tag;
	}
}
