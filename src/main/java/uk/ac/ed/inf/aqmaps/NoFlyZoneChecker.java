package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class NoFlyZoneChecker {

	private List<Polygon> noFlyZones;
	private Point northWestBound;
	private Point southEastBound;
	
	private HashMap<Polygon, Polygon> boundaries;
	
	public NoFlyZoneChecker(List<Polygon> noFlyZones, Point northWestBound, Point southEastBound) {
		this.noFlyZones = noFlyZones;
		this.northWestBound = northWestBound;
		this.southEastBound = southEastBound;
	}
	
	// https://martin-thoma.com/how-to-check-if-two-line-segments-intersect/
	// The cross product is useful here because it's sign changes depending on stuff
	// Doesn't work in 2D so just use first two axes
	// Points represent vectors instead
	
	// TODO: do some bounding box check or something to make this faster. 
	
	public boolean isMoveLegal(Point origin, Point destination) {
		var lng = destination.longitude();
		var lat = destination.latitude();
		

		if (lng <= northWestBound.longitude() 
				|| lng >= southEastBound.longitude() 
				|| lat <= southEastBound.latitude() 
				|| lat >= northWestBound.latitude()) {
			return false;
		}
		
		boolean intersecting = false;
		for (var zone: noFlyZones) {
			// Get the coordinates that make up each no-fly-zone
			List<Point> polyCoords = zone.coordinates().get(0);
			for (int i = 0; i < polyCoords.size()-1; i++) {
				
				Point p1 = polyCoords.get(i);
				Point p2 = polyCoords.get(i+1);
				
				Point polyVec = normalise(p1, p2);
				Point droneVec = normalise(origin, destination);
				
				Point originPolyVec = normalise(origin, p1);
				Point destinationPolyVec = normalise(origin, p2);
				Point originDroneVec = normalise(p1, origin);
				Point destinationDroneVec = normalise(p1, destination);
				
				if (doesLineSegmentCrossLine(polyVec, originDroneVec, destinationDroneVec) && doesLineSegmentCrossLine(droneVec, originPolyVec, destinationPolyVec)) {
					intersecting = true;
					break;
				}
			}
		}
		return !intersecting;
	}
	
	private void calculateBoundaries() {
		
		for (var zone : noFlyZones) {
			double minLong = Double.MAX_VALUE; 
			double maxLong = Double.MIN_VALUE;
			double minLat = Double.MAX_VALUE;
			double maxLat = Double.MIN_VALUE;
			
			for (var point : zone.coordinates().get(0)) {
				var lng = point.longitude();
				var lat = point.latitude();
				if (lng < minLong) {
					minLong = lng;
				}
				if (lng > maxLong) {
					maxLong = lng;
				}
				if (lat < minLat) {
					minLat = lat;
				}
				if (lat > maxLat) {
					maxLat = lat;
				}
			}
			
			var topLeft = Point.fromLngLat(minLong, maxLat);
			var bottomLeft = Point.fromLngLat(minLong, minLat);
			var bottomRight = Point.fromLngLat(maxLong, minLat);
			var topRight = Point.fromLngLat(maxLong, maxLat);
			
			var verts = new ArrayList<Point>(Arrays.asList(topLeft, bottomLeft, bottomRight, topRight));
			var bound = Polygon.fromLngLats(Arrays.asList(verts));
			
			boundaries.put(bound, zone);
		}
		
	}
	
	// The name "normalise" is potentially confusing.
	// This takes two points that define a line segment. 
	// It then moves both points so that point a lies at (0, 0).
	private static Point normalise(Point a, Point b) {
		return Point.fromLngLat(
				b.longitude() - a.longitude(),
				b.latitude() - a.latitude());
	}
	
	// Cross product is only defined in 3D
	// It is helpful here for checking which side of a line a point is on (the sign changes)
	// We're only solving for one component of the cross product
	private static double cross(Point a, Point b) {
		return a.longitude()*b.latitude() - a.latitude()*b.longitude();
	}
	
	// Basically you're using the cross product to see which side of the line each point is
	
	private static boolean doesLineSegmentCrossLine(Point lineVec, Point segmentA, Point segmentB) {
		return (cross(lineVec, segmentA) >= 0) ^ (cross(lineVec, segmentB) >= 0);
	}
	
	
}
