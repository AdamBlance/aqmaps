package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;


// Utility class containing helpful methods for manipulating points
public class PointUtils {
	
	// Euclidean between two points
	public static double distanceBetween(Point a, Point b) {
		return Math.sqrt(Math.pow(a.longitude() - b.longitude(), 2) + Math.pow(a.latitude() - b.latitude(), 2));
	}
	
	// Returns the gradient (bearing) of the line between two points
	// Also rounds the bearing to the nearest 10
	public static int mostDirectBearing(Point origin, Point destination) {	
		double latDist = destination.latitude() - origin.latitude();
		double longDist = destination.longitude() - origin.longitude();
		
		// Gets polar theta, converts to degrees
		int roundedPolarTheta = (int) Math.round(Math.toDegrees(Math.atan2(latDist, longDist)) / 10.0) * 10;		
		return mod360(roundedPolarTheta);  // This converts the negative values past the 180 degree mark
	}
	
	// Returns the point you would arrive at if moving distance from position with bearing
	public static Point moveDestination(Point position, double distance, int bearing) {
		double rad = Math.toRadians(bearing);
		var newPosition = Point.fromLngLat(
				position.longitude() + distance*Math.cos(rad),
				position.latitude() + distance*Math.sin(rad));
				
		return newPosition;
	}

	// Keeps bearings between 0-350
	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
}