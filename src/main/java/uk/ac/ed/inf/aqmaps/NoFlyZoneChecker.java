package uk.ac.ed.inf.aqmaps;

import java.util.List;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class NoFlyZoneChecker {

	private List<Polygon> noFlyZones;
	
	public NoFlyZoneChecker(List<Polygon> noFlyZones) {
		this.noFlyZones = noFlyZones;
	}
	
	
	// https://martin-thoma.com/how-to-check-if-two-line-segments-intersect/
	// The cross product is useful here because it's sign changes depending on stuff
	// Doesn't work in 2D so just use first two axes
	// Points represent vectors instead
	
	public boolean isMoveLegal(Point origin, Point destination) {
		
		boolean intersecting = false;
		
		for (Polygon building : noFlyZones) {
			// Get the coordinates that make up each no-fly-zone building
			List<Point> polyCoords = building.coordinates().get(0);
			for (int i = 0; i < polyCoords.size()-2; i++) {
				
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
	
	
	
	
	// Basically you're using the cross product to see which side of the line each point is
	
	private static boolean doesLineSegmentCrossLine(Point lineVec, Point segmentA, Point segmentB) {
		return (cross(lineVec, segmentA) >= 0) ^ (cross(lineVec, segmentB) >= 0);
	}
	
	// The name "normalise" is potentially confusing.
	// This takes two points that define a line segment. 
	// It then moves both points so that point a lies at (0, 0).
	private static Point normalise(Point a, Point b) {
		return Point.fromLngLat(
				b.longitude() - a.longitude(),
				b.latitude() - a.latitude()
				);
	}
	
	private static double cross(Point a, Point b) {
		return a.longitude()*b.latitude() - a.latitude()*b.longitude();
	}
	
}
