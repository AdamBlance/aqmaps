package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class PointUtils {
	
	// Distance between two points
	public static double distanceBetween(Point a, Point b) {
		return Math.sqrt(Math.pow(a.longitude() - b.longitude(), 2) + Math.pow(a.latitude() - b.latitude(), 2));
	}
	
	// TODO: Look into this breaking when the origin and destination are the same
	// Get the bearing you take to go from origin to destination rounded to nearest 10
	public static int nearestBearing(Point origin, Point destination) {	
		double latDist = destination.latitude() - origin.latitude();
		double longDist = destination.longitude() - origin.longitude();
		
		// Gets polar theta, converts to degrees
		int roundedPolarTheta = (int) Math.round(Math.toDegrees(Math.atan2(latDist, longDist)) / 10.0) * 10;		
		return mod360(roundedPolarTheta);  // This converts the negative values past the 180 degree mark
	}
	
	// Returns the point you would arrive at if moving distance from position with bearing
	public static Point moveDestination(Point position, double distance, int bearing) {
		
		if (bearing % 10 == 0 && bearing >= 0 && bearing <= 350) {
			double rad = Math.toRadians(bearing);
			var newPosition = Point.fromLngLat(
					position.longitude() + distance*Math.cos(rad),
					position.latitude() + distance*Math.sin(rad));
					
			return newPosition;
		} else {
			throw new IllegalArgumentException("Invalid bearing - must a multiple of 10 between 0-350.");
		}	
	}

	// Keeps bearings between 0-350 when adding or subtracting
	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
}