package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Point;



public class PointUtils {
	
	public static boolean inRange(Point a, Waypoint w) {
		double arrivalDistance = w instanceof StartEndPoint ? Drone.END_POINT_DISTANCE : Drone.SENSOR_READ_DISTANCE;
		return distanceBetween(a, w.getPoint()) < arrivalDistance;
	}
	

	public static double distanceBetween(Point a, Point b) {
		return Math.sqrt(Math.pow(a.longitude() - b.longitude(), 2) + Math.pow(a.latitude() - b.latitude(), 2));
	}
	
	public static int mostDirectBearing(Point a, Waypoint w) {
		var b = w.getPoint();
		double latDist = b.latitude() - a.latitude();
		double longDist = b.longitude() - a.longitude();
		
		// Gets polar theta, converts to degrees
		int roundedPolarTheta = (int) Math.round(Math.toDegrees(Math.atan2(latDist, longDist)) / 10.0) * 10;		
		return mod360(roundedPolarTheta);  // This converts the negative values past the 180 degree mark
	}
	
	public static Point moveDestination(Point position, int bearing) {
		double rad = Math.toRadians(bearing);
		var newPosition = Point.fromLngLat(
				position.longitude() + Drone.MOVE_DISTANCE * Math.cos(rad),
				position.latitude() + Drone.MOVE_DISTANCE * Math.sin(rad));
				
		return newPosition;
	}

	public static boolean pointStrictlyInsideBoundingBox(Point point, BoundingBox bound) {
		var lng = point.longitude();
		var lat = point.latitude();
		return lng > bound.west() && lng < bound.east() 
				&& lat > bound.south() && lat < bound.north(); 
	}
	
	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
}