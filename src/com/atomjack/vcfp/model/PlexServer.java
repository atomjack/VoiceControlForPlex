package com.atomjack.vcfp.model;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexServer extends PlexDevice {

  public PlexServer() {}
  public PlexServer(String name) {
    this.name = name;
  }

	@Attribute
	private String machineIdentifier;

	private List<String> movieSections = new ArrayList<String>();
	private List<String> tvSections = new ArrayList<String>();
	private List<String> musicSections = new ArrayList<String>();
	
	public String getMachineIdentifier() {
		return machineIdentifier;
	}

	public void setMachineIdentifier(String machineIdentifier) {
		this.machineIdentifier = machineIdentifier;
	}

	public List<String> getMovieSections() {
		return movieSections;
	}

	public void setMovieSections(List<String> movieSections) {
		this.movieSections = movieSections;
	}

	public List<String> getTvSections() {
		return tvSections;
	}

	public void setTvSections(List<String> tvSections) {
		this.tvSections = tvSections;
	}
	
	public void addMovieSection(String key) {
		if(!movieSections.contains(key)) {
			movieSections.add(key);
		}
	}

	public void addTvSection(String key) {
		if(!tvSections.contains(key)) {
			tvSections.add(key);
		}
	}
	
	public void addMusicSection(String key) {
		if(!musicSections.contains(key)) {
			musicSections.add(key);
		}
	}
	
	public String getClientsURL() {
		return "http://" + super.getAddress() + ":" + super.getPort() + "/clients";
	}
	
	public String getBaseURL() {
		return "http://" + super.getAddress() + ":" + super.getPort() + "/";
	}
	
	@Override
	public String toString() {
		String output = "";
		output += "Name: " + super.getName() + "\n";
		output += "IP Address: " + super.getAddress() + "\n";
		return output;
	}

	public List<String> getMusicSections() {
		return musicSections;
	}

	public void setMusicSections(List<String> musicSections) {
		this.musicSections = musicSections;
	}

}
