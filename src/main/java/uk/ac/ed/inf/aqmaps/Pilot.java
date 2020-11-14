package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.mostDirectBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;


public class Pilot {

	private Drone drone;
	
	private List<Point> path = new ArrayList<>();
	private List<String> log = new ArrayList<>();
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	private Queue<Integer> precomputedBearings = new LinkedList<>();
	
	private NoFlyZoneChecker noFlyZoneChecker;
		
	public Pilot(Drone drone, List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
		this.drone = drone;
		this.noFlyZoneChecker = new NoFlyZoneChecker(noFlyZones, droneConfinementArea);
		path.add(drone.getPosition());
	}
	
	public boolean followRoute(List<Waypoint> waypoints) {
		createSensorReports(waypoints);
		var start = new Waypoint(drone.getPosition());
		
		for (var waypoint : waypoints) {
			boolean arrived = navigateTowards(waypoint);
			if (!arrived) {
				return false;
			}
			if (waypoint instanceof Sensor) {
				readSensorAndRecordReading((Sensor) waypoint);
			}
		}
		return navigateTowards(start);  // True if we make it back, false if we don't.
	}
	
	private void createSensorReports(List<Waypoint> waypoints) {
		for (var waypoint : waypoints) {
			if (waypoint instanceof Sensor) {
				sensorReports.put((Sensor) waypoint, new SensorReport(false, false));
			}
		}
	}
	
	private void readSensorAndRecordReading(Sensor sensor) {
		var reading = drone.readSensor(sensor);
		var report = sensorReports.get(sensor);
		if (reading.isPresent()) {
			report.setValid(true);
		}
		report.setVisited(true);
	}
	
	private boolean navigateTowards(Waypoint waypoint) {
		
		boolean arrived = false;
		while (!arrived) {
			
			var previousPosition = drone.getPosition();
			
			var bearing = nextBearing(waypoint.getPoint());
			if (bearing.isEmpty()) {
				break;
			}
			
			drone.move(bearing.get());
			
			var newPosition = drone.getPosition();
			
			String w3wLocation = null;
			if (inRange(waypoint)) {
				if (waypoint instanceof Sensor) {
					w3wLocation = ((Sensor) waypoint).getW3wAddress();
				}
				arrived = true;
			}
			
			path.add(newPosition);
			log.add(String.format("%d,%f,%f,%d,%f,%f,%s",
					drone.getTimesMoved(),
					previousPosition.longitude(),
					previousPosition.latitude(),
					bearing,
					newPosition.longitude(),
					newPosition.latitude(),
					w3wLocation == null ? "null" : w3wLocation));
		}
		return arrived;
	}
	
	public List<Point> getPath() {
		return path;
	}
	
	private boolean inRange(Waypoint waypoint) {
		return distanceBetween(drone.getPosition(), waypoint.getPoint()) < Drone.SENSOR_READ_DISTANCE;
	}
	
	public HashMap<Sensor, SensorReport> getSensorReports() {
		return sensorReports;
	}
	
	public List<String> getLog() {
		return log;
	}
	
	// make waypoint
	private Optional<Integer> nextBearing(Point target) {
		// Return the next pre-computed bearing if it exists
		if (!precomputedBearings.isEmpty()) {
			return Optional.of(precomputedBearings.poll());
		}
		
		// Otherwise, return the most direct bearing to the target if taking that bearing would be legal
		var dronePos = drone.getPosition();
		int mostDirectBearing = mostDirectBearing(dronePos, target);
		if (noFlyZoneChecker.isMoveLegal(dronePos, mostDirectBearing)) {
			return Optional.of(mostDirectBearing);
		}
		
		// Finally, if both fail, attempt to find a path around the obstruction
		var pathToTake = computeLegalPath(target);
		if (!pathToTake.isEmpty()) {
			// Add the path to the pre-computed queue
			precomputedBearings.addAll(pathToTake);
			return Optional.of(precomputedBearings.poll());	
		}
		
		// If all this fails, we're stuck
		return Optional.empty();
	}
	
	private List<Integer> computeLegalPath(Point target) {
		var CWBranch = new SearchBranch(target, true);
		var ACWBranch = new SearchBranch(target, false);
		while (!(CWBranch.isStuck() && ACWBranch.isStuck())) {	
			var shortestBranch = (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch : ACWBranch;
			shortestBranch.explore();
			if (shortestBranch.isFinished()) {
				return shortestBranch.getBranchDirections();
			}
		}
		return new ArrayList<Integer>();
	}
	
	private static class NoFlyZoneChecker {

		private BoundingBox droneConfinementArea;
		private HashMap<Polygon, Polygon> boundariesWithNoFlyZones = new HashMap<>();
		
		public NoFlyZoneChecker(List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
			calculateBoundaries(noFlyZones);  // Populates boundariesWithNoFlyZones
			this.droneConfinementArea = droneConfinementArea;
		}
		
		// https://martin-thoma.com/how-to-check-if-two-line-segments-intersect/	
		public boolean isMoveLegal(Point origin, int bearing) {
			var destination = moveDestination(origin, Drone.MOVE_DISTANCE, bearing);
			if (!pointStrictlyInsideBoundingBox(destination, droneConfinementArea)) {
				return false;
			}

			for (var zone : boundariesWithNoFlyZones.keySet()) {
				if (pointStrictlyInsideBoundingBox(origin, zone.bbox()) || 
						pointStrictlyInsideBoundingBox(destination, zone.bbox())) {				
					if (lineIntersectsPolygon(origin, destination, boundariesWithNoFlyZones.get(zone))) {
						return false;
					}
				} else if (lineIntersectsPolygon(origin, destination, zone)){
					if (lineIntersectsPolygon(origin, destination, boundariesWithNoFlyZones.get(zone))) {
						return false;
					}
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
				
				var points = new ArrayList<Point>(Arrays.asList(
						boundingBox.northeast(),
						Point.fromLngLat(minLong, maxLat),
						boundingBox.southwest(),
						Point.fromLngLat(maxLong, minLat),
						boundingBox.northeast()));
						
				var polyBound = Polygon.fromLngLats(new ArrayList<>(Arrays.asList(points)), boundingBox);
				boundariesWithNoFlyZones.put(polyBound, zone);
			}
		}
		
		// This takes two points that define a line segment and returns the vector
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
	
	private class SearchBranch {
		
		double heuristic = 0;
		int moveCount = 0;
		Point branchHead;
		boolean stuck = false;
		Point target;
		int step;
		List<Integer> branchDirections = new ArrayList<>();
		
		public SearchBranch(Point target, boolean clockwise) {
			this.branchHead = drone.getPosition();
			step = clockwise ? -10 : 10;
			this.target = target;
		}
		
		public void explore() {
			int mostDirectBearing = mostDirectBearing(branchHead, target);
			int backtrack;
			if (branchDirections.isEmpty()) {
				backtrack = mostDirectBearing;
			} else {
				backtrack = mod360(lastBearing() - 180);
			}
			
			var legalBearing = bearingScan(branchHead, mostDirectBearing, step, backtrack);
			if (legalBearing.isPresent()) {
				var newBearing = legalBearing.get();
				branchHead = moveDestination(branchHead, Drone.MOVE_DISTANCE, newBearing);
				branchDirections.add(newBearing);
				moveCount += 1;
				heuristic = moveCount * Drone.MOVE_DISTANCE + distanceBetween(branchHead, target);
			} else {
				heuristic = Double.MAX_VALUE;
				stuck = true;
			}			
		}
		
		private Optional<Integer> bearingScan(Point position, int startBearing, int offset, int limitBearing) {			
			int bearing = mod360(startBearing + offset);
			while (bearing != limitBearing) {
				if (noFlyZoneChecker.isMoveLegal(position, bearing)) {
					 return Optional.of(bearing);
				 }
				 bearing = mod360(bearing + offset);
			}
			return Optional.empty();
		}
		
		public boolean isFinished() {
			if (distanceBetween(branchHead, target) < Drone.SENSOR_READ_DISTANCE) {
				return true;
			}
			int mostDirectBearing = mostDirectBearing(branchHead, target);
			int backtrackBearing = mod360(lastBearing() - 180);
			boolean moveIsLegal = noFlyZoneChecker.isMoveLegal(branchHead, mostDirectBearing);
			
			if (moveIsLegal && (mostDirectBearing != backtrackBearing)) {
				branchDirections.add(mostDirectBearing);
				return true;
			}
			return false;
		}
		
		private int lastBearing() {
			return branchDirections.get(branchDirections.size() - 1);
		}
		
		public double getHeuristic() {
			return heuristic;
		}
		
		public List<Integer> getBranchDirections() {
			return branchDirections;
		}
		
		public boolean isStuck() {
			return stuck;
		}
	}
}
