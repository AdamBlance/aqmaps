package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.mostDirectBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.pointStrictlyInsideBoundingBox;

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

	private final Drone drone;
	
	// Stores each line of our flightpath-*.txt file for writing later 
	private final List<String> log = new ArrayList<>();
	
	// List of points where the drone has been (for readings-*.geojson)
	private final List<Point> path = new ArrayList<>();
	
	// Map with sensors as keys and reports as values
	// This is where we "record" sensor readings (more info in report)
	private final HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	private final Queue<Integer> precomputedBearings = new LinkedList<>();
	
	private final NoFlyZoneChecker noFlyZoneChecker;
		
	public Pilot(Drone drone, List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
		this.drone = drone;
		this.noFlyZoneChecker = new NoFlyZoneChecker(noFlyZones, droneConfinementArea);
	}
	
	// Given a list of waypoints, this will attempt to navigate the drone to each in order
	// Returns true if successful, false if the drone runs out of moves or gets stuck
	public boolean followRoute(List<Waypoint> waypoints) {
		createSensorReports(waypoints);
		var start = new Waypoint(drone.getPosition());
		
		for (var waypoint : waypoints) {
			boolean arrived = navigateTo(waypoint);
			if (!arrived) {
				return false;
			}
			if (waypoint instanceof Sensor) {
				readSensorAndRecordReading((Sensor) waypoint);
			}
		}
		return navigateTo(start);  // True if we make it back, false if we don't.
	}
	
	// Creates a report for each sensor initially marking them as unvisited
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
	
	// Attempts to navigate the drone to the specified waypoint
	// Returns true if it arrives, false if something happens along the way
	private boolean navigateTo(Waypoint waypoint) {
		
		// This will repeatedly make moves until we have arrived or the drone cannot proceed
		boolean arrived = false;
		while (!arrived) {
			
			var previousPosition = drone.getPosition();
			
			// Determine the best bearing to take next
			var possibleBearing = nextBearing(waypoint);
			if (possibleBearing.isEmpty()) {
				System.out.println("Could not find a way around obstruction!");
				break;
			}
			int bearing = possibleBearing.get();
			
			// Try to move the drone with bearing
			var possibleNewPosition = drone.move(bearing);
			if (possibleNewPosition.isEmpty()) {
				System.out.println("Drone has ran out of moves!");
				break;
			}
			var newPosition = possibleNewPosition.get();
			
			String w3wLocation = null;
			if (distanceBetween(newPosition, waypoint.getPoint()) < Drone.SENSOR_READ_DISTANCE) {
				
				// If the waypoint is a sensor, get its what3words address for logging
				if (waypoint instanceof Sensor) {
					w3wLocation = ((Sensor) waypoint).getW3wAddress();
				}
				arrived = true;
			}
			
			logMove(previousPosition, bearing, newPosition, w3wLocation);
		}
		return arrived;
	}
	
	private void logMove(Point previousPosition, int bearing, Point newPosition, String w3wLocation) {
		// Update the path that the drone has taken
		path.add(newPosition);
		// Create flightpath-*.txt line for this move
		log.add(String.format("%d,%f,%f,%d,%f,%f,%s\n",
				drone.getTimesMoved(),
				previousPosition.longitude(),
				previousPosition.latitude(),
				bearing,
				newPosition.longitude(),
				newPosition.latitude(),
				w3wLocation == null ? "null" : w3wLocation));
	}
	
	// Determines the next bearing that the drone should take
	// Will return an empty optional object if we can't find any legal way to move
	private Optional<Integer> nextBearing(Waypoint waypoint) {
		// Return the next pre-computed bearing if it exists
		if (!precomputedBearings.isEmpty()) {
			return Optional.of(precomputedBearings.poll());
		}
		
		// Otherwise, return the most direct bearing to the target if taking that bearing would be legal
		var dronePos = drone.getPosition();
		int mostDirectBearing = mostDirectBearing(dronePos, waypoint.getPoint());
		if (noFlyZoneChecker.isMoveLegal(dronePos, mostDirectBearing)) {
			return Optional.of(mostDirectBearing);
		}
		
		System.out.println(drone.getPosition().toJson());
		
		// Finally, if both fail, attempt to find a path around the obstruction
		var pathToTake = computeLegalPath(waypoint);
		if (!pathToTake.isEmpty()) {
			// Add the path to the pre-computed queue
			precomputedBearings.addAll(pathToTake);
			return Optional.of(precomputedBearings.poll());	
		}
		
		
		
		// If all this fails, we're stuck
		return Optional.empty();
	}
	
	// Returns a list of bearings, that if taken, will move the drone around an obstruction
	// This will return an empty list if we can't find any path around the obstruction
	private List<Integer> computeLegalPath(Waypoint waypoint) {
		var startPoint = drone.getPosition();
		// Creates two search branches (clockwise and anti-clockwise)
		// CWBranch and ACWBranch explore clockwise and anti-clockwise around the obstruction respectively 
		var CWBranch = new SearchBranch(startPoint, waypoint, true, noFlyZoneChecker);
		var ACWBranch = new SearchBranch(startPoint, waypoint, false, noFlyZoneChecker);
		
		// Repeat until we find a valid path or both paths get stuck
		while (!(CWBranch.isStuck() && ACWBranch.isStuck())) {	
			// At each step, pick the branch with the lowest heuristic (this is the A* min-heap step but with only two branches)
			var shortestBranch = (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch : ACWBranch;
			shortestBranch.explore();
			if (shortestBranch.isFinished()) {
				return shortestBranch.getBranchDirections();
			}
		}
		return new ArrayList<Integer>();
	}
	
	// Useful inner class for determining which moves are legal and which are not
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
				System.out.println("moving outside the bound");
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
	
	// Inner class implementing an A*-type algorithm for navigating around obstructions
	private static class SearchBranch {
		
		
		private double branchLength = 0;     // Length of branch so far
		private Point branchHead;            // Head of the branch so far
		private boolean stuck = false;       // Whether we cannot explore any further
		private final Point goal;            // Where we're trying to get
		private final int step;              // Bearing interval that we check for legal moves with
		
		List<Integer> branchDirections = new ArrayList<>();  // Stores the bearings the branch has taken so far
		NoFlyZoneChecker noFlyZoneChecker;
		
		public SearchBranch(Point startPoint, Waypoint goal, boolean clockwise, NoFlyZoneChecker noFlyZoneChecker) {
			this.branchHead = startPoint;
			step = clockwise ? 10 : -10;
			this.goal = goal.getPoint();
			this.noFlyZoneChecker = noFlyZoneChecker;
		}
		
		// I mean actually, you could do something that lets you enter a crevice once, then puts you back out or something
		
		public void explore() {
			int mostDirectBearing = mostDirectBearing(branchHead, goal);
			int backtrack;
			if (branchDirections.isEmpty()) {
				backtrack = mod360(mostDirectBearing - 180);  // not good in certain circumstances, maybe don't set a limit on the first move  imagine you start in here |_| trying to go down, you need to double back
				System.out.println(backtrack);
				System.out.println("first move");
			} else {
				backtrack = mod360(lastBearing() - 180);
				System.out.println("not first");
			}
			
			var legalBearing = bearingScan(branchHead, mostDirectBearing, step, backtrack);
			if (legalBearing.isPresent()) {
				var newBearing = legalBearing.get();
				branchHead = moveDestination(branchHead, Drone.MOVE_DISTANCE, newBearing);
				branchDirections.add(newBearing);
				branchLength += Drone.MOVE_DISTANCE;
			} else {
				stuck = true;
			}	
		}
		
		private Optional<Integer> bearingScan(Point position, int startBearing, int offset, int limitBearing) {			
			int bearing = startBearing;
			System.out.println(startBearing);
			while (bearing != limitBearing) {
				System.out.println(bearing);
				if (noFlyZoneChecker.isMoveLegal(position, bearing)) {
					 return Optional.of(bearing);
				 }
				 bearing = mod360(bearing + offset);
			}
			return Optional.empty();
		}
		
		public boolean isFinished() {
			if (stuck) {
				System.out.println("stuck!");
				return false;
			} else {
				System.out.println("not stuck!");
			}
			if (distanceBetween(branchHead, goal) < Drone.SENSOR_READ_DISTANCE) {
				return true;
			}
			int mostDirectBearing = mostDirectBearing(branchHead, goal);
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
			return stuck ? Double.MAX_VALUE : branchLength + distanceBetween(branchHead, goal);
		}
		
		public List<Integer> getBranchDirections() {
			return branchDirections;
		}
		
		public boolean isStuck() {
			return stuck;
		}
	}
	
	public List<Point> getPath() {
		return path;
	}
	
	public HashMap<Sensor, SensorReport> getSensorReports() {
		return sensorReports;
	}
	
	public String getLog() {
		return String.join("", log);
	}
}
