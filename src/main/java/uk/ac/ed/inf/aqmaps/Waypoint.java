package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public abstract class Waypoint {
	
	private final Point point;
	
	protected Waypoint(Point point) {
		this.point = point;
	}
	
	protected Point getPoint() {
		return point;
	}
	
}
