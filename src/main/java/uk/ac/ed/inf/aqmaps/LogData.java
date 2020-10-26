package uk.ac.ed.inf.aqmaps;

import org.eclipse.jdt.annotation.Nullable;

// this might cause problems

import com.mapbox.geojson.Point;

public class LogData {

	int num;
	Point before;
	Point after;
	int bearing;
	String w3wLocation;
	
	public LogData(int num, Point before, int bearing, Point after, @Nullable String w3wLocation) {
		this.num = num;
		this.before = before;
		this.bearing = bearing;
		this.after = after;
		this.w3wLocation = w3wLocation;
	}
	
	@Override
	public String toString() {
		return String.format("%d,%f,%f,%d,%f,%f,%s",
				num,
				before.longitude(),
				before.latitude(),
				bearing,
				after.longitude(),
				after.latitude(),
				w3wLocation == null ? "null" : w3wLocation);
	}
	
}
