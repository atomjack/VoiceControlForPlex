package com.atomjack.vcfpht.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import android.text.TextUtils;
import android.util.Log;

import com.atomjack.vcfpht.MainActivity;

@Root(strict=false)
public class PlexVideo {
	@Attribute
	private String key;
	@Attribute
	private String title;
	@Attribute(required=false)
	private String viewOffset;
	@Attribute(required=false)
	private String index;
	@Attribute(required=false)
	private String grandparentTitle;
	@Attribute(required=false)
	private PlexServer server;
	@Attribute(required=false)
	private String grandparentThumb;
	@Attribute(required=false)
	private String thumb;
	@Attribute(required=false)
	private String type;
	@Attribute(required=false)
	private String year;
	@ElementList(required=false, inline=true, entry="Genre")
	private ArrayList<Genre> genre = new ArrayList<Genre>();
	@Attribute(required=false)
	public String duration;
	@Attribute(required=false)
	private String summary;
	
	private String showTitle;
	
	public String getGrandparentThumb() {
		return grandparentThumb;
	}

	public void setGrandparentThumb(String grandparentThumb) {
		this.grandparentThumb = grandparentThumb;
	}
	
	public String getGrandparentTitle() {
		return grandparentTitle;
	}

	public void setGrandparentTitle(String grandparentTitle) {
		this.grandparentTitle = grandparentTitle;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public String getViewOffset() {
		return viewOffset;
	}

	public void setViewOffset(String viewOffset) {
		this.viewOffset = viewOffset;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public PlexServer getServer() {
		return server;
	}

	public void setServer(PlexServer server) {
		this.server = server;
	}

	public String getThumb() {
		return thumb;
	}

	public void setThumb(String thumb) {
		this.thumb = thumb;
	}

	public String getThumbnail() {
		Log.v(MainActivity.TAG, "grandparent thumb: " + grandparentThumb);
		return grandparentThumb != null ? grandparentThumb : thumb;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getYear() {
		return year;
	}

	public void setYear(String year) {
		this.year = year;
	}

	public List<Genre> getGenre() {
		return genre;
	}

	public void setGenre(ArrayList<Genre> genre) {
		this.genre = genre;
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

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getShowTitle() {
		return showTitle;
	}

	public void setShowTitle(String showTitle) {
		this.showTitle = showTitle;
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

