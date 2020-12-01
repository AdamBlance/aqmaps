package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.mostDirectBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.inRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;


public class Pilot {

	private final Drone drone;
	private final NoFlyZoneChecker noFlyZoneChecker;
	
	private final List<String> log = new ArrayList<>();
	private final List<Point> pathTaken = new ArrayList<>();
	private final HashMap<Sensor, Boolean> sensorsVisited = new HashMap<>();
	
	private final Queue<Integer> precomputedBearings = new LinkedList<>();
	
	public Pilot(Drone drone, List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
		this.drone = drone;
		this.noFlyZoneChecker = new NoFlyZoneChecker(noFlyZones, droneConfinementArea);
		pathTaken.add(drone.getPosition());  // Include start position in the flight path
	}
	
	public boolean followRoute(List<Sensor> route) {
		
		for (var sensor : route) {
			sensorsVisited.put(sensor, false);  
		}

		for (var sensor : route) {
			boolean arrived = navigateTo(sensor);
			if (!arrived) {
				return false;
			}
			takeReading(sensor);
		}
		
		var startPosition = new StartEndPoint(pathTaken.get(0));
		return navigateTo(startPosition);  // True if we complete the loop, false if we don't.
	}
	
	private void takeReading(Sensor sensor) {
		drone.readSensor(sensor);  // We don't actually save the reading from the drone (more detail in report)
		sensorsVisited.put(sensor, true);
	}
	
	private boolean navigateTo(Waypoint waypoint) {
		
		// Until we have arrived at the waypoint, try repeatedly to move towards it 
		boolean arrived = false;
		while (!arrived) {
						
			var previousPosition = drone.getPosition();  // Remembered for logging to flightpath
			
			// This "isEmpty" syntax is because nextBearing returns an empty Optional object if it fails
			// Unfortunately it's a bit wordy, but it's safer than returning null
			var possibleNextBearing = nextBearing(waypoint);
			if (possibleNextBearing.isEmpty()) {
				System.out.println("Could not find a way around obstruction!");
				return false;
			}
			int bearing = possibleNextBearing.get();
			
			boolean moved = drone.move(bearing);
			if (!moved) {
				System.out.println("Drone has ran out of moves!");
				return false;
			}
			
			var newPosition = drone.getPosition();
			if (inRange(newPosition, waypoint)) {
				arrived = true;
			}
			
			// If we have arrived at a sensor (not the start/end point), get its w3w address
			var w3w = (arrived && waypoint instanceof Sensor) ? ((Sensor) waypoint).getW3wAddress() : "null";
			
			logMove(previousPosition, bearing, newPosition, w3w);
		}
		return true;
	}
	
	private void logMove(Point previousPosition, int bearing, Point newPosition, String w3wAddress) {
		// Update the path that the drone has taken
		pathTaken.add(newPosition);
		// Create flightpath-*.txt line for this move
		log.add(String.format("%d,%f,%f,%d,%f,%f,%s%n",
				drone.getTimesMoved(),
				previousPosition.longitude(),
				previousPosition.latitude(),
				bearing,
				newPosition.longitude(),
				newPosition.latitude(),
				w3wAddress));
	}
	
	private Optional<Integer> nextBearing(Waypoint waypoint) {
		// Return the next pre-computed bearing if it exists
		if (!precomputedBearings.isEmpty()) {
			return Optional.of(precomputedBearings.poll());
		}
		
		// Otherwise, (if there is nothing in the way) go in a straight line towards the waypoint  
		var dronePos = drone.getPosition();
		var maybe = correctedMostDirectBearing(dronePos, waypoint, noFlyZoneChecker);
		if (maybe.isPresent()) {
			// TODO: Use is present everywhere it's much better
			
			return Optional.of(maybe.get());
		}
		
		
		// Finally, if both fail, attempt to compute a path around the obstruction
		var pathToTake = computeLegalPath(waypoint);
		if (!pathToTake.isEmpty()) {
			precomputedBearings.addAll(pathToTake);			 // Fill the precomputedBearings queue with our shiny new path
			return Optional.of(precomputedBearings.poll());	 // Return the first element of the path as the next bearing
		}
		
		// If all this fails, we're stuck
		return Optional.empty();
	}
	
	
	
	private List<Integer> computeLegalPath(Waypoint waypoint) {
		var startPoint = drone.getPosition();

		var penis = Feature.fromGeometry(LineString.fromLngLats(pathTaken));
		penis.addStringProperty("rgb-string", "#000000");
//		System.out.println(penis.toJson());
		
		// CWBranch and ACWBranch explore clockwise and anti-clockwise around the obstruction respectively 
		var CWBranch = new SearchBranch(startPoint, waypoint, true, noFlyZoneChecker);
		var ACWBranch = new SearchBranch(startPoint, waypoint, false, noFlyZoneChecker);
		
		CWBranch.evaluate();
		ACWBranch.evaluate();

		if (CWBranch.isStuck() && ACWBranch.isStuck()) {  // If both get stuck, we failed to compute a path around the obstruction 
			return new ArrayList<Integer>();  
		} else if (CWBranch.isStuck()) {                  // If only one gets stuck, return the other one
			return ACWBranch.getBearingsTaken();
		} else if (ACWBranch.isStuck()) {
			return CWBranch.getBearingsTaken();
		}
		
		// If both branches completed, return the one with the lower heuristic
		return (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch.getBearingsTaken() : ACWBranch.getBearingsTaken();
	}
	
	
	private static Optional<Integer> correctedMostDirectBearing(Point point, Waypoint waypoint, NoFlyZoneChecker noFlyZoneChecker) {
		
		int mostDirectBearing = mostDirectBearing(point, waypoint);
		
		if (noFlyZoneChecker.moveIsLegal(point, mostDirectBearing)) {
			return Optional.of(mostDirectBearing);
		}
		
		// Otherwise, we need to scan for a good one
		
		/* 
		 * The absolute worst case scenario is where the drone is on the absolute edge of the radius
		 * and the sensor is on the no-fly-zone edge which is perpendicular to the approach angle of the drone
		 * 
		 * In this scenario, the drone could stay in range by turning 45 degrees in either direction
		 * Our drone can't do this since it moves in 10 degree increments
		 * Instead, we should scan 50 degrees from either side and just in case I'm forgetting something, lets make it 60 degrees so 120 degree scan in total
		*/
		
		// So before we do this check we should really check that the drone is overshooting and hitting a wall
		// Otherwise, we'll be checking on corner cuts and stuff
		
		if (noFlyZoneChecker.moveLandsInNoFlyZone(point, mostDirectBearing)) {
			for (int i = 10; i <= 60; i += 10) {
				int cwCheck = mod360(mostDirectBearing + i);
				if (noFlyZoneChecker.moveIsLegal(point, cwCheck) && inRange(moveDestination(point, cwCheck), waypoint)) {
					return Optional.of(cwCheck);
				}
				int acwCheck = mod360(mostDirectBearing - i);
				if (noFlyZoneChecker.moveIsLegal(point, acwCheck) && inRange(moveDestination(point, acwCheck), waypoint)) {
					return Optional.of(acwCheck);
				}
			}
		}
		return Optional.empty();
	}
	
	private static class NoFlyZoneChecker {

		private BoundingBox droneConfinementArea;
		
		private HashMap<Polygon, Polygon> boundariesWithNoFlyZones = new HashMap<>();
		
		public NoFlyZoneChecker(List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
			calculateBoundaries(noFlyZones);  // Populates boundariesWithNoFlyZones
			this.droneConfinementArea = droneConfinementArea;
		}
		
		// https://martin-thoma.com/how-to-check-if-two-line-segments-intersect/
		
		public boolean moveIsLegal(Point point, int bearing) {
			var destination = moveDestination(point, bearing);
			if (!pointStrictlyInsideBoundingBox(destination, droneConfinementArea)) {
				return false;
			}
			
			for (var zone : boundariesWithNoFlyZones.keySet()) {
				if (pointStrictlyInsideBoundingBox(point, zone.bbox()) || 
						pointStrictlyInsideBoundingBox(destination, zone.bbox())) {				
					if (lineIntersectsPolygon(point, destination, boundariesWithNoFlyZones.get(zone))) {
						return false;
					}
				} else if (lineIntersectsPolygon(point, destination, zone)){
					if (lineIntersectsPolygon(point, destination, boundariesWithNoFlyZones.get(zone))) {
						return false;
					}
				}
			}
			return true;
		}
		
		public boolean moveLandsInNoFlyZone(Point point, int bearing) {
			for (var noFlyZone : boundariesWithNoFlyZones.values()) {
				if (TurfJoins.inside(moveDestination(point, bearing), noFlyZone)) {
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
		
		// Details on how this works will be in Section 3 of the report
		private static boolean lineIntersectsPolygon(Point start, Point end, Polygon poly) {
			var S = start;
			var E = end;
			
			var SE = toVector(S, E);
			
			var polyPoints = poly.coordinates().get(0);  // We only need the first list (index 0) because the no-fly-zones are not hollow
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
			for (var noFlyZone : noFlyZones) {
				double minLong = Double.MAX_VALUE; 
				double maxLong = Double.MIN_VALUE;
				double minLat = Double.MAX_VALUE;
				double maxLat = Double.MIN_VALUE;

				for (var point : noFlyZone.coordinates().get(0)) {
					var lng = point.longitude();
					var lat = point.latitude();
					
					// Finding the bounds of the no-fly-zone
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
				
				double epsilon = 0.00005;  // Add a little buffer room to the bounding box
				minLong -= epsilon;
				maxLong += epsilon;
				minLat -= epsilon;
				maxLat += epsilon;
				
				// Bounding box corners
				var northEast = Point.fromLngLat(maxLong, maxLat);
				var northWest = Point.fromLngLat(minLong, maxLat);
				var southWest = Point.fromLngLat(minLong, minLat);
				var southEast = Point.fromLngLat(maxLong, minLat);
				
				var boundingBox = BoundingBox.fromPoints(southWest, northEast);
				var points = new ArrayList<Point>(
						Arrays.asList(northEast, northWest, southWest, southEast, northEast));

				// Polygon.fromLngLats needs a list of list of points
				var singletonListOfPoints = new ArrayList<List<Point>>(Arrays.asList(points));
				
				// Rectangle that completely surrounds no-fly-zone
				// We also set the bbox property of the polygon to help with some calculations
				var boundingBoxAsPolygon = Polygon.fromLngLats(singletonListOfPoints, boundingBox);
				boundariesWithNoFlyZones.put(boundingBoxAsPolygon, noFlyZone);
			}
		}
		
		private static Point toVector(Point a, Point b) {
			return Point.fromLngLat(
					b.longitude() - a.longitude(),
					b.latitude() - a.latitude());
		}
		
		private static double cross(Point a, Point b) {
			return a.longitude()*b.latitude() - a.latitude()*b.longitude();
		}
		
		private static boolean vectorsOppositeSidesOfLine(Point vectorA, Point vectorB, Point lineVector) {
			return (cross(lineVector, vectorA) >= 0) ^ (cross(lineVector, vectorB) >= 0);
		}
	}
	

	private static class SearchBranch {
		
		private Point branchHead;            
		private final Waypoint goal;            
		
		private boolean stuck = false;      

		private final boolean clockwise;
		
		List<Integer> bearingsTaken = new ArrayList<>();
		NoFlyZoneChecker noFlyZoneChecker;
		
		public SearchBranch(Point startPoint, Waypoint goal, boolean clockwise, NoFlyZoneChecker noFlyZoneChecker) {
			this.branchHead = startPoint;
			this.clockwise = clockwise;
			this.goal = goal;
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
		
		// All right so I think this can be brought into one function (expand and isfinished) maybe idk I'm pretty sure actually, I should think of all the steps to do in the first section
		// We should check if the move destination is within range of the sensor but it's illegal, then we've identified one of these weird edge cases where the sensor's range intersects with a wall
		// Then we should identify a minimum number of scans in a range that we have to do
		
		// https://en.wikipedia.org/wiki/Chord_(geometry)#In_circles with chords (fixing the r chr 0 to 0.0003 ...
		
		private void expand() {
			int mostDirectBearing = mostDirectBearing(branchHead, goal);

			// We stop scanning if the first legal bearing we find is 180 degrees from the one we just took
			int limit = bearingsTaken.isEmpty() ? mostDirectBearing : backtrackBearing();
			int step =  clockwise ? 10 : -10;
			
			var legalBearing = bearingScan(mod360(mostDirectBearing + step), limit, step);
			if (legalBearing.isPresent()) {
				var newBearing = legalBearing.get();
				
				var temp = moveDestination(branchHead, newBearing);
				
				var penis = Feature.fromGeometry(LineString.fromLngLats(Arrays.asList(branchHead, temp)));
				penis.addStringProperty("rgb-string", "#000000");
//				System.out.println(penis.toJson());
				
				branchHead = temp;
				bearingsTaken.add(newBearing);
				
			} else {
				stuck = true;
			}	
		}
		
		private Optional<Integer> bearingScan(int scanFrom, int scanTo, int step) {
			for (int bearing = scanFrom; bearing != scanTo; bearing = mod360(bearing + step)) {
				
				if (noFlyZoneChecker.moveIsLegal(branchHead, bearing)) {
					return Optional.of(bearing);
				}
			}
			return Optional.empty();
		}
		
		private boolean isFinished() {
			if (stuck) {
				return false;
			}
			
			// FIRST
			// Check if we're already in range (corner cutting for example)
			if (inRange(branchHead, goal)) {
				return true;
			}
			

			// SECOND
			// Check if there is a move (either direct or scanned that lands us directly inside the target)
			var legalBearingToWaypoint = correctedMostDirectBearing(branchHead, goal, noFlyZoneChecker);
			
			if (legalBearingToWaypoint.isPresent()) {
				var bearing = legalBearingToWaypoint.get();
				if (inRange(moveDestination(branchHead, bearing), goal)) {
					bearingsTaken.add(bearing);
					return true;
				}
				
				
				if (bearing != backtrackBearing()) {
					bearingsTaken.add(bearing);
					return true;
				}
				
			}
			
			return false;

			
			// THIRD
			// Check if there is a move directly towards the target that DOES NOT land in range of it
			// We check this because rounding problems mean that sometimes there is a legal move towards the goal that just puts us back a step
			// ...and we know from that step that there is no legal move towards it 
			
			// so will need something that if you're within a certain range, it then checks more stuff
			
			

			
			// Okay so at this point we've not checked anything like the backtrack bearing
			
			// We just returned the direct path regardless of if it was the backtrack bearing or not, will need to check that
			
			// So we need something that checks for direct landings in sensor range
			// We need a separate thing that checks for paths towards
			
			// Thankfully only relevant inside the branch search
			// Could return some weird object that has 
			// whether it was a direct path or actually lands in there
			
			// Can figure out the exact minimum set of functions later stop procrastinating
			
//			int mostDirectBearing = mostDirectBearing(branchHead, goal);
//			int backtrackBearing = backtrackBearing();
//			var backtrackResult = moveDestination(branchHead, backtrackBearing);
			
			// There is an edge case where
			//    the drone starts in range of a sensor
			//    it moves towards it, overshooting and hitting a wall
			//    it then does a bearing scan and finds a legal move 180 degrees from where it tried to move at first
			
//			var penis = Feature.fromGeometry(LineString.fromLngLats(Arrays.asList(branchHead, moveDestination(branchHead, mostDirectBearing))));
//			penis.addStringProperty("rgb-string", "#000000");
//			System.out.println(penis.toJson());
//			
//			if (noFlyZoneChecker.isMoveLegal(branchHead, mostDirectBearing) && ((mostDirectBearing != backtrackBearing) || (inRange(backtrackResult, goal)))) {
//				bearingsTaken.add(mostDirectBearing);
//				return true;
//			}
//			return false;
		}
		
		private int backtrackBearing() {
			int lastBearing = bearingsTaken.get(bearingsTaken.size() - 1);
			return mod360(lastBearing - 180);
		}
				
		public double getHeuristic() {
			return stuck ? Double.MAX_VALUE : (bearingsTaken.size()*Drone.MOVE_DISTANCE) + distanceBetween(branchHead, goal.getPoint());
		}
		
		public List<Integer> getBearingsTaken() {
			return bearingsTaken;
		}
		
		public boolean isStuck() {
			return stuck ? stuck : (bearingsTaken.size() > 150);
		}
	}
	
	public List<Point> getPathTaken() {
		return pathTaken;
	}
	
	public HashMap<Sensor, Boolean> getSensorsVisited() {
		return sensorsVisited;
	}
	
	public String getLog() {
		return String.join("", log);
	}
}
