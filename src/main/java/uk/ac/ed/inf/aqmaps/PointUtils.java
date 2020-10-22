package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class PointUtils {

	private PointUtils() {}
	
	// Distance between two points
	public static double distanceBetween(Point a, Point b) {
		return Math.sqrt(Math.pow(a.longitude() - b.longitude(), 2) + Math.pow(a.latitude() - b.latitude(), 2));
	}
	
	public static int nearestBearing(Point origin, Point destination) {	
		double latDist = destination.latitude() - origin.latitude();
		double longDist = destination.longitude() - origin.longitude();
		
		// Gets polar theta, converts to degrees, rounds to nearest 10
		int roundedPolarTheta = (int) Math.round(Math.toDegrees(Math.atan2(latDist, longDist)) / 10.0) * 10;
		// Rotate coordinates by 90 degrees (polar theta is 0 on y-axis) and then subtract from 360 to make bearing move clockwise
		int bearing = Math.floorMod(360 - Math.floorMod(roundedPolarTheta - 90, 360), 360); // second mod in case 360-0 = 0
		
		return bearing;
	}
	
	public static Point moveDestination(Point position, double distance, int bearing) {
		if (bearing % 10 == 0 && bearing >= 0 && bearing <= 350) {
			double rad = Math.toRadians(bearing);
			var newPosition = Point.fromLngLat(
					position.longitude() + distance*Math.sin(rad),
					position.latitude() + distance*Math.cos(rad));
			return newPosition;
		} else {
			throw new IllegalArgumentException("Invalid bearing - must a multiple of 10 between 0-350.");
		}	
	}

	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
	public static int oppositeBearing(int bearing) {
		return mod360(bearing - 180);
	}
	
	// The name "normalise" is potentially confusing.
	// This takes two points that define a line segment. 
	// It then moves both points so that point a lies at (0, 0).
	public static Point normalise(Point a, Point b) {
		return Point.fromLngLat(
				b.longitude() - a.longitude(),
				b.latitude() - a.latitude());
	}
	
	public static double cross(Point a, Point b) {
		return a.longitude()*b.latitude() - a.latitude()*b.longitude();
	}
	
}