package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.cross;

import java.util.Random;

import com.mapbox.geojson.BoundingBox;
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
		
		// Gets polar theta, converts to degrees, rounds to nearest 10
		int roundedPolarTheta = (int) Math.round(Math.toDegrees(Math.atan2(latDist, longDist)) / 10.0) * 10;
		// Rotate coordinates by 90 degrees (polar theta is 0 on y-axis) and then subtract from 360 to make bearing move clockwise
		int bearing = Math.floorMod(360 - Math.floorMod(roundedPolarTheta - 90, 360), 360); // second mod in case 360-0 = 0
		
		return bearing;
	}
	
	// Returns the point you would arrive at if moving distance from position with bearing
	public static Point moveDestination(Point position, double distance, int bearing) {
		
		Random rand = new Random();
		
		if (bearing % 10 == 0 && bearing >= 0 && bearing <= 350) {
			double rad = Math.toRadians(bearing);
			var newPosition = Point.fromLngLat(
					position.longitude() + distance*Math.sin(rad),
					position.latitude() + distance*Math.cos(rad),
					BoundingBox.fromPoints(Point.fromLngLat(-3.0*rand.nextDouble(), 50.0), Point.fromLngLat(-2.0, 52)));
					
			return newPosition;
		} else {
			throw new IllegalArgumentException("Invalid bearing - must a multiple of 10 between 0-350.");
		}	
	}

	
	// lineVector - A vector representing a line (only bearing is important here)
	// endpointA/endpointB - The endpoints of the line segment we are checking for collision
	private static boolean lineSegmentCrossesLine(Point lineVector, Point endpointA, Point endpointB) {
		return (cross(lineVector, endpointA) >= 0) ^ (cross(lineVector, endpointB) >= 0);
	}
	
	
	// Keeps bearings between 0-350 when adding or subtracting
	public static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}

	// The name "normalise" is potentially confusing.
	// This takes two points that define a line segment. 
	// It then moves both points so that point a lies at (0, 0).
	public static Point normalise(Point a, Point b) {
		return Point.fromLngLat(
				b.longitude() - a.longitude(),
				b.latitude() - a.latitude());
	}
	
	// Cross product is only defined in 3D
	// It is helpful here for checking which side of a line a point is on (the sign changes)
	// We're only solving for one component of the cross product
	public static double cross(Point a, Point b) {
		return a.longitude()*b.latitude() - a.latitude()*b.longitude();
	}
	
}