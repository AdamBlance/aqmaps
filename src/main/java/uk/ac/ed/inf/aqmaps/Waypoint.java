package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

// Very basic "struct" type class for representing waypoints
public class Waypoint {
	
	private final Point point;
	private final boolean isHome;
	
	public Waypoint(Point point, boolean isHome) {
		this.point = point;
		this.isHome = isHome;
	}
	
	public Point getPoint() {
		return point;
	}
	
	public boolean isHome() {
		return isHome;
	}
}
