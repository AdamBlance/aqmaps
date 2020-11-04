package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class Waypoint {
	
	private Point point;
	
	public Waypoint(Point point) {
		this.point = point;
	}
	
	public Point getPoint() {
		return point;
	}
}
