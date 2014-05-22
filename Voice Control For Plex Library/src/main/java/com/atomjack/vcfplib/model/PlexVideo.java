package com.atomjack.vcfplib.model;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import android.text.TextUtils;

@Root(strict=false)
public class PlexVideo {
	@Attribute
	public String key;
	@Attribute
	public String title;
	@Attribute(required=false)
	public String viewOffset;
	@Attribute(required=false)
	public String index;
	@Attribute(required=false)
	public String grandparentTitle;
	@Attribute(required=false)
	public PlexServer server;
	@Attribute(required=false)
	public String grandparentThumb;
	@Attribute(required=false)
	public String thumb;
	@Attribute(required=false)
	public String type;
	@Attribute(required=false)
	public String year;
	@ElementList(required=false, inline=true, entry="Genre")
	public ArrayList<Genre> genre = new ArrayList<Genre>();
	@Attribute(required=false)
	public String duration;
	@Attribute(required=false)
	public String summary;
  @Attribute(required=false)
  public String originallyAvailableAt;
	public Date airDate() {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    try {
      return formatter.parse(originallyAvailableAt);
    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }
	public String showTitle;
	
	public PlexServer getServer() {
		return server;
	}

	public void setServer(PlexServer server) {
		this.server = server;
	}

	public String getGenres() {
		return TextUtils.join(", ", genre);
	}

	public String getDuration() {
		if(TimeUnit.MILLISECONDS.toHours(Long.parseLong(duration)) > 0) {
		return String.format("%d hr %d min", 
				TimeUnit.MILLISECONDS.toHours(Long.parseLong(duration)),
				TimeUnit.MILLISECONDS.toMinutes(Long.parseLong(duration)) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(Long.parseLong(duration))));
		} else {
			return String.format("%d min", TimeUnit.MILLISECONDS.toMinutes(Long.parseLong(duration)));
		}
	}
}



class Genre {
	@Attribute
	public String tag;
	
	@Override
	public String toString() {
		return tag;
	}
}

