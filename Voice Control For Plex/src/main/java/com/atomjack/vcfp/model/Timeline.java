package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class Timeline {
	@Attribute
	public String state;
	@Attribute
	public String time;
	@Attribute
	public String type;

	public String getTime() {
		int seconds = Integer.parseInt(time) / 1000;
		int hours = seconds / 3600;
		int minutes = (seconds % 3600) / 60;
		seconds = seconds % 60;

		return String.format("%d:%d:%d", hours, minutes, seconds);
	}
}
