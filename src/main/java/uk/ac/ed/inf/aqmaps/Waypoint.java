package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

// Very basic "struct" type class for representing waypoints
public class Waypoint {
	
	private Point point;
	
	public Waypoint(Point point) {
		this.point = point;
	}
	
	public Point getPoint() {
		return point;
	}
}
