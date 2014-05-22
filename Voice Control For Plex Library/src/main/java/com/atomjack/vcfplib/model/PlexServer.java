package com.atomjack.vcfplib.model;

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
	public String machineIdentifier;

	public List<String> movieSections = new ArrayList<String>();
	public List<String> tvSections = new ArrayList<String>();
	public List<String> musicSections = new ArrayList<String>();

  public int movieSectionsSearched = 0;
  public int showSectionsSearched = 0;
  public int musicSectionsSearched = 0;

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
		return "http://" + super.address + ":" + super.getPort() + "/clients";
	}
	
	public String getBaseURL() {
		return "http://" + super.address + ":" + super.getPort() + "/";
	}
	
	@Override
	public String toString() {
		String output = "";
		output += "Name: " + super.name + "\n";
		output += "IP Address: " + super.address + "\n";
		return output;
	}
}
