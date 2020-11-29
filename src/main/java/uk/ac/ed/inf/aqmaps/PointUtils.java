package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class PointUtils {
	
	public static boolean inRange(Point point, Waypoint waypoint) {
		double arrivalDistance = waypoint instanceof StartEndPoint ? Drone.END_POINT_DISTANCE : Drone.SENSOR_READ_DISTANCE;
		return distanceBetween(point, waypoint.getPoint()) < arrivalDistance;
	}
	
	public static double distanceBetween(Point pointA, Point pointB) {
		return Math.sqrt(Math.pow(pointA.longitude() - pointB.longitude(), 2) + Math.pow(pointA.latitude() - pointB.latitude(), 2));
	}
	
	public static int mostDirectBearing(Point point, Waypoint waypoint) {
		var b = waypoint.getPoint();
		double latDist = b.latitude() - point.latitude();
		double longDist = b.longitude() - point.longitude();
		
		// Gets polar theta, converts to degrees
		int roundedPolarTheta = (int) Math.round(Math.toDegrees(Math.atan2(latDist, longDist)) / 10.0) * 10;		
		return mod360(roundedPolarTheta);  // This converts the negative values past the 180 degree mark
	}
	
	public static Point moveDestination(Point point, int bearing) {
		double rad = Math.toRadians(bearing);
		var newPosition = Point.fromLngLat(
				point.longitude() + Drone.MOVE_DISTANCE * Math.cos(rad),
				point.latitude() + Drone.MOVE_DISTANCE * Math.sin(rad));
				
		return newPosition;
	}
	
	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
}