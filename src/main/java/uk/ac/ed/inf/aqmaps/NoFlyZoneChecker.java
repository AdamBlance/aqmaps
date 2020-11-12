package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class NoFlyZoneChecker {

	private BoundingBox droneConfinementArea;
	private HashMap<Polygon, Polygon> boundariesWithNoFlyZones = new HashMap<>();
	
	// instead, have polygons with bounding boxes
	// then can check bounds and intersection
	
	public NoFlyZoneChecker(List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
		calculateBoundaries(noFlyZones);  // Populates boundariesWithNoFlyZones
		this.droneConfinementArea = droneConfinementArea;
		
//		var peen = new ArrayList<Feature>();
//		for (var p : boundariesWithNoFlyZones.keySet()) {
//			var f = Feature.fromGeometry(p);
//			f.addStringProperty("rgb-string", "#aaaaaa");
//			peen.add(f);
//			
//			var q = Feature.fromGeometry(boundariesWithNoFlyZones.get(p));
//			q.addStringProperty("rgb-string", "#eeeeee");
//			peen.add(q);
//			
//		}
//
//		
//		System.out.println(FeatureCollection.fromFeatures(peen).toJson());
		
	}
	
	// https://martin-thoma.com/how-to-check-if-two-line-segments-intersect/
	// The cross product is useful here because it's sign changes depending on stuff
	// Doesn't work in 2D so just use first two axes
	// Points represent vectors instead
	
	// Will fail if we land on the boundary of the area
	// Could fail if we land on one of the bounding boxes, so I'll add a little buffer area
	
	public boolean isMoveLegal(Point origin, Point destination) {
		
		if (!pointStrictlyInsideBoundingBox(destination, droneConfinementArea)) {
			return false;
		}
		
		// So the ways we could hit a building:
		// -- We move inside the bounding box of a building
		// -- We enter the bounding box of a building
		// -- We exit the bounding box of a building
		// -- We cross the borders but we stay outside of it
		
		// So first check whether origin or destination is inside (cheaper)
		// Then check collision

		for (var zone : boundariesWithNoFlyZones.keySet()) {
			if (pointStrictlyInsideBoundingBox(origin, zone.bbox()) || 
					pointStrictlyInsideBoundingBox(destination, zone.bbox())) {
				
				System.out.println("in bound");
				if (lineIntersectsPolygon(origin, destination, boundariesWithNoFlyZones.get(zone))) {
					System.out.println("intersects");
					return false;
				}
			} else if (lineIntersectsPolygon(origin, destination, zone)){
				System.out.println("crossed bound");
				if (lineIntersectsPolygon(origin, destination, boundariesWithNoFlyZones.get(zone))) {
					System.out.println("intersects!");
					return false;
				}
			} else {
				System.out.println("nope!");
			}
		}
		return true;
	}
	
	private static boolean lineIntersectsPolygon(Point start, Point end, Polygon poly) {
		var S = start;
		var E = end;
		
		var SE = toVector(S, E);
		
		var polyPoints = poly.coordinates().get(0);
		for (int i = 0; i < polyPoints.size() - 1; i++) {
		
			var P = polyPoints.get(i);
			var Q = polyPoints.get(i+1);
			
			var PQ = toVector(P, Q);
			
			var SP = toVector(S, P);
			var SQ = toVector(S, Q);
			var PS = toVector(P, S);
			var PE = toVector(P, E);
			
			// TODO: Maybe look at weird conditions from that webpage
			
			if (vectorsOppositeSidesOfLine(PS, PE, PQ) && vectorsOppositeSidesOfLine(SP, SQ, SE)) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean pointStrictlyInsideBoundingBox(Point point, BoundingBox bound) {
		var lng = point.longitude();
		var lat = point.latitude();
		
		return lng > bound.west() && lng < bound.east() 
				&& lat > bound.south() && lat < bound.north(); 
	}
	
	private void calculateBoundaries(List<Polygon> noFlyZones) {
		for (var zone : noFlyZones) {
			double minLong = 1000; 
			double maxLong = -1000;
			double minLat = 1000;
			double maxLat = -1000;
			
			// longitude/latitude is never > 180 or < -180
			
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
			
			double epsilon = 0.00005;  // Buffer room
			minLong -= epsilon;
			maxLong += epsilon;
			minLat -= epsilon;
			maxLat += epsilon;
			
			var boundingBox = BoundingBox.fromLngLats(minLong, minLat, maxLong, maxLat);
			
			var temp = new ArrayList<Point>(Arrays.asList(
					boundingBox.northeast(),
					Point.fromLngLat(minLong, maxLat),
					boundingBox.southwest(),
					Point.fromLngLat(maxLong, minLat),
					boundingBox.northeast()));
					
			
			var polyBound = Polygon.fromLngLats(new ArrayList<>(Arrays.asList(temp)), boundingBox);
			
			boundariesWithNoFlyZones.put(polyBound, zone);
		}
		
	}
	
	// The name "normalise" is potentially confusing.
	// This takes two points that define a line segment. 
	// It then moves both points so that point A lies at (0, 0).
	private static Point toVector(Point a, Point b) {
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
	
	private static boolean vectorsOppositeSidesOfLine(Point vectorA, Point vectorB, Point lineVector) {
		return (cross(lineVector, vectorA) >= 0) ^ (cross(lineVector, vectorB) >= 0);
	}
	
	
}
