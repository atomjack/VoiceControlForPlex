package com.atomjack.vcfpht;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.atomjack.googlesearchplexcontrol.R;
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
        Log.v(MainActivity.TAG, "data: " + data[0]);
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
            
            
//            holder.imgIcon = (ImageView)row.findViewById(R.id.imgIcon);
//            holder.txtTitle = (TextView)row.findViewById(R.id.txtTitle);
           
            row.setTag(holder);
        }
        else
        {
            holder = (SettingHolder)row.getTag();
        }
        
        
        
        MainSetting item = data[position];
        holder.line1.setText(item.line1);
        holder.line2.setText(item.line2);
//        holder.line1 = item[0];
//        holder.line2 = item[1];
//        Weather weather = data[position];
//        holder.txtTitle.setText(weather.title);
//        holder.imgIcon.setImageResource(weather.icon);
       
        return row;
    }
	
	static class SettingHolder {
		TextView line1;
		TextView line2;
	}
}
