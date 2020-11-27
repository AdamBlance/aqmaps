package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.mostDirectBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.pointStrictlyInsideBoundingBox;
import static uk.ac.ed.inf.aqmaps.PointUtils.inRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.LineString;
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
	private final HashMap<Sensor, Boolean> sensorsVisited = new HashMap<>();
	
	private final Queue<Integer> precomputedBearings = new LinkedList<>();
	
	private final NoFlyZoneChecker noFlyZoneChecker;
	
	public Pilot(Drone drone, List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
		this.drone = drone;
		this.noFlyZoneChecker = new NoFlyZoneChecker(noFlyZones, droneConfinementArea);
		path.add(drone.getPosition());
	}
	
	// Given a list of waypoints, this will attempt to navigate the drone to each in order
	// Returns true if successful, false if the drone runs out of moves or gets stuck
	public boolean followRoute(List<Waypoint> waypoints) {
		markAllSensorsUnvisited(waypoints);
		var start = new Waypoint(drone.getPosition(), true);
		
		for (var waypoint : waypoints) {
			boolean arrived = navigateTo(waypoint);
			if (!arrived) {
				return false;
			}
			if (waypoint instanceof Sensor) {
				markVisited((Sensor) waypoint);
			}
		}
		return navigateTo(start);  // True if we make it back, false if we don't.
	}
	
	// Creates a report for each sensor initially marking them as unvisited
	private void markAllSensorsUnvisited(List<Waypoint> waypoints) {
		for (var waypoint : waypoints) {
			if (waypoint instanceof Sensor) {
				sensorsVisited.put((Sensor) waypoint, false);
			}
		}
	}
	
	private void markVisited(Sensor sensor) {
		drone.readSensor(sensor);  // We don't actually store the reading
		sensorsVisited.put(sensor, true);
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
			boolean moved = drone.move(bearing);
			if (!moved) {
				System.out.println("Drone has ran out of moves!");
				break;
			}
			
			var newPosition = drone.getPosition();
			
			String w3wLocation = null;
			if (inRange(newPosition, waypoint)) {
				
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
		log.add(String.format("%d,%f,%f,%d,%f,%f,%s%n",
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
//	private List<Integer> computeLegalPath(Waypoint waypoint) {
//		var startPoint = drone.getPosition();
//		// Creates two search branches (clockwise and anti-clockwise)
//		// CWBranch and ACWBranch explore clockwise and anti-clockwise around the obstruction respectively 
//		var CWBranch = new SearchBranch(startPoint, waypoint, true, noFlyZoneChecker);
//		var ACWBranch = new SearchBranch(startPoint, waypoint, false, noFlyZoneChecker);
//		
//		// Repeat until we find a valid path or both paths get stuck
//		while (!(CWBranch.isStuck() && ACWBranch.isStuck())) {	
//			// At each step, pick the branch with the lowest heuristic (this is the A* min-heap step but with only two branches)
//			var shortestBranch = (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch : ACWBranch;
//			shortestBranch.expand();
//			if (shortestBranch.isFinished()) {
//				return shortestBranch.getBranchDirections();
//			}
//		}
//		return new ArrayList<Integer>();
//	}
	
	
	
	
	
	
	private List<Integer> computeLegalPath(Waypoint waypoint) {
		var startPoint = drone.getPosition();

		// CWBranch and ACWBranch explore clockwise and anti-clockwise around the obstruction respectively 
		var CWBranch = new SearchBranch(startPoint, waypoint, true, noFlyZoneChecker);
		var ACWBranch = new SearchBranch(startPoint, waypoint, false, noFlyZoneChecker);
		
		CWBranch.evaluate();
		ACWBranch.evaluate();
		
		if (CWBranch.isStuck() && ACWBranch.isStuck()) {
			return new ArrayList<Integer>();  // If both get stuck, we could not compute a legal path around the obstruction 
		} else if (CWBranch.isStuck()) {
			return ACWBranch.getBranchDirections();
		} else if (ACWBranch.isStuck()) {
			return CWBranch.getBranchDirections();
		}
		
		// If both branches completed, return the one with the lower heuristic
		return (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch.getBranchDirections() : ACWBranch.getBranchDirections();
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
		
		private Point branchHead;            // Head of the branch so far
		private final Waypoint goal;            // Where we're trying to get to
		
		private boolean stuck = false;       // Whether we cannot explore any further

		private final boolean clockwise;              // Bearing interval that we check for legal moves with
		
		List<Integer> bearingsTaken = new ArrayList<>();  // Stores the bearings the branch has taken so far
		NoFlyZoneChecker noFlyZoneChecker;
		
		public SearchBranch(Point startPoint, Waypoint goal, boolean clockwise, NoFlyZoneChecker noFlyZoneChecker) {
			this.branchHead = startPoint;
			this.clockwise = clockwise;
			this.goal = goal;  // changing all stuff to use inrange
			this.noFlyZoneChecker = noFlyZoneChecker;
		}
		
		public void evaluate() {
			while (!stuck) {
				expand();
				if (isFinished()) {
					break;
				}
			}
		}
		
		private void expand() {
			int mostDirectBearing = mostDirectBearing(branchHead, goal.getPoint());

			
			int limit = bearingsTaken.isEmpty() ? mostDirectBearing : mod360(lastBearing() - 180);
			
			
			int step =  clockwise ? 10 : -10;
			var legalBearing = bearingScan(branchHead, mostDirectBearing + step, step, limit);
			if (legalBearing.isPresent()) {
				var newBearing = legalBearing.get();
				branchHead = moveDestination(branchHead, Drone.MOVE_DISTANCE, newBearing);
				bearingsTaken.add(newBearing);
			} else {
				stuck = true;
			}	
		}
		
		private Optional<Integer> bearingScan(Point position, int startBearing, int offset, int limitBearing) {			
			int bearing = startBearing;
			while (bearing != limitBearing) {
				
				if (noFlyZoneChecker.isMoveLegal(position, bearing)) {
						return Optional.of(bearing);
				 }
				 bearing = mod360(bearing + offset);
			}
			return Optional.empty();
		}
		
		private boolean isFinished() {
			if (stuck) {
				return false;
			}
			
			if (inRange(branchHead, goal)) {
				return true;
			}
			
			int mostDirectBearing = mostDirectBearing(branchHead, goal.getPoint());                        // Bearing directly to goal
			int backtrackBearing = mod360(lastBearing() - 180);                                 // Bearing that takes us back to where the branch head last was 
			
			var backtrackResult = moveDestination(branchHead, Drone.MOVE_DISTANCE, backtrackBearing);
			
			
			if (noFlyZoneChecker.isMoveLegal(branchHead, mostDirectBearing) 
					&& ((mostDirectBearing != backtrackBearing) || (inRange(backtrackResult, goal)))) {
				bearingsTaken.add(mostDirectBearing);
				return true;
			}
			return false;
		}
		
		private int lastBearing() {
			return bearingsTaken.get(bearingsTaken.size() - 1);
		}
		
		public double getHeuristic() {
			return stuck ? Double.MAX_VALUE : (bearingsTaken.size()*Drone.MOVE_DISTANCE) + distanceBetween(branchHead, goal.getPoint());
		}
		
		public List<Integer> getBranchDirections() {
			return bearingsTaken;
		}
		
		public boolean isStuck() {
			return stuck ? stuck : (bearingsTaken.size() > 150);
		}
	}
	
	public List<Point> getPath() {
		return path;
	}
	
	public HashMap<Sensor, Boolean> getSensorsVisited() {
		return sensorsVisited;
	}
	
	public String getLog() {
		return String.join("", log);
	}
}
