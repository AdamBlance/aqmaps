package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Point;


// Utility class containing helpful methods for manipulating points
public class PointUtils {
	
	public static boolean inRange(Point a, Waypoint w) {
		double arrivalDistance = w.isHome() ? 0.0003 : 0.0002;
		return distanceBetween(a, w.getPoint()) < arrivalDistance;
	}
	
	// Euclidean between two points
	public static double distanceBetween(Point a, Point b) {
		return Math.sqrt(Math.pow(a.longitude() - b.longitude(), 2) + Math.pow(a.latitude() - b.latitude(), 2));
	}
	
	// Returns the gradient (bearing) of the line between two points
	// Also rounds the bearing to the nearest 10
	public static int mostDirectBearing(Point a, Point b) {	
		double latDist = b.latitude() - a.latitude();
		double longDist = b.longitude() - a.longitude();
		
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

	public static boolean pointStrictlyInsideBoundingBox(Point point, BoundingBox bound) {
		var lng = point.longitude();
		var lat = point.latitude();
		return lng > bound.west() && lng < bound.east() 
				&& lat > bound.south() && lat < bound.north(); 
	}
	
	// Keeps bearings between 0-350
	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
}