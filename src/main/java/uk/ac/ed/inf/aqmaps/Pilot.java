package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.bearingFromTo;
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
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;


public class Pilot {

	// The pilot’s assigned drone
	private final Drone drone;
	
	// Lets the Pilot check whether a move is legal or not before sending it to the drone
	private final NoFlyZoneChecker noFlyZoneChecker;
	
	// Stores which bearings the drone needs to take to clear no
	private final Queue<Integer> precomputedBearings = new LinkedList<>();
	
	// Remembers which sensors the drone has visited so far
	private final HashMap<Sensor, Boolean> sensorsVisited = new HashMap<>();
	
	// List of points where the drone has been 
	private final List<Point> pathTaken = new ArrayList<>();
	
	// Holds each log line that we later write to flightpath-*.txt
	private final List<String> log = new ArrayList<>();
	
	// Creates a pilot with an assigned drone, and with specified restrictions
	public Pilot(Drone drone, List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
		this.drone = drone;
		this.noFlyZoneChecker = new NoFlyZoneChecker(noFlyZones, droneConfinementArea);
		pathTaken.add(drone.getPosition());  // Include start position in the flight path
	}
	
	// Tries to fly the drone to each sensor in the route (in order), returning it to its initial position afterwards
	// Returns true if the flight was successful (made it back in 150 moves, visited every sensor, didn’t get stuck)
	public boolean followRoute(List<Sensor> route) {
		for (var sensor : route) {
			sensorsVisited.put(sensor, false);     // Mark all sensors unvisited initially  
		}
		
		for (var sensor : route) {
			boolean arrived = navigateTo(sensor);  // Try to navigate to each sensor in the route
			if (!arrived) {
				return false;
			}
			takeReading(sensor);                   // Take reading once arrived
		}
		
		var startPosition = new StartEndPoint(pathTaken.get(0));  // Just snagging the drone's initial position from the start of the path
		return navigateTo(startPosition);                         // True if we return to the start successfully, false if we don't
	}
	
	private void takeReading(Sensor sensor) {
		drone.readSensor(sensor);          // We don't actually save the reading from the drone! (more detail section 1.5 of the report)
		sensorsVisited.put(sensor, true);  // Mark as visited
	}

	// Tries to fly the drone to the specified waypoint
	// Returns true if the drone makes it to the waypoint without running out of moves or getting stuck
	private boolean navigateTo(Waypoint waypoint) {
		
		// Until we have arrived at the waypoint, try repeatedly to move towards it 
		boolean arrived = false;
		while (!arrived) {
			
			var previousPosition = drone.getPosition();  // Remembered for logging to flightpath after moving
			
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
			
			// Set as arrived if the drone is now in range of the waypoint
			var newPosition = drone.getPosition();
			if (inRange(newPosition, waypoint)) {
				arrived = true;
			}
			
			// If we have arrived at a sensor (not the start/end point), get its w3w address
			var w3w = (arrived && waypoint instanceof Sensor) ? ((Sensor) waypoint).getW3wAddress() : "null";
			
			logMove(previousPosition, bearing, newPosition, w3w);
		}
		return true;  // breaking from the loop means we have arrived
	}

	// Determines and then returns the most appropriate bearing to take
	private Optional<Integer> nextBearing(Waypoint waypoint) {
		// Return the next pre-computed bearing if it exists
		if (!precomputedBearings.isEmpty()) {
			return Optional.of(precomputedBearings.poll());
		}
		
		// Otherwise, (if there is nothing in the way) go in a straight line towards the waypoint
		var dronePos = drone.getPosition();
		var bearingTowardsWaypoint = mostDirectBearing(dronePos, waypoint, noFlyZoneChecker);  // This handles an edge case mentioned in section 3.3 of the report
		if (bearingTowardsWaypoint.isPresent()) {              // Is there a legal move towards the waypoint?
			return Optional.of(bearingTowardsWaypoint.get());  // Return its bearing
		}
		
		// There must be something in the way, so compute a path around it
		var pathToTake = computePathAroundObstruction(waypoint);
		if (!pathToTake.isEmpty()) {                         // If the path is empty then a legal path could not be found
			precomputedBearings.addAll(pathToTake);			 // Fill the precomputedBearings queue with our shiny new path
			return Optional.of(precomputedBearings.poll());	 // Return the first element of the path as the next bearing
		}
		
		// If all this fails, we're stuck. Don't return anything.
		return Optional.empty();
	}

	// Returns the bearing directly towards the waypoint if the resulting move would be legal
	// If the resulting move overshoots the waypoint and hits a no-fly-zone, tries to correct it
	private static Optional<Integer> mostDirectBearing(Point point, Waypoint waypoint, NoFlyZoneChecker noFlyZoneChecker) {
		
		// The bearing of the line that goes directly towards the waypoint from point
		int bearingTowardsWaypoint = bearingFromTo(point, waypoint);
		
		// If the move literally straight towards the waypoint is legal, return that as the most direct bearing
		if (noFlyZoneChecker.moveIsLegal(point, bearingTowardsWaypoint)) {
			return Optional.of(bearingTowardsWaypoint);
		}
		
		// Otherwise, check if we overshoot the waypoint (see section 3.3 for more detail) and attempt to correct the overshoot
		// Return the corrected bearing as the most direct bearing (if we can correct it)
		if (noFlyZoneChecker.moveLandsInNoFlyZone(point, bearingTowardsWaypoint)) {
			for (int i = 10; i <= 60; i += 10) {
				int cwCheck = mod360(bearingTowardsWaypoint + i);   // Check 10 degrees further clockwise to see if that makes the move legal
				if (noFlyZoneChecker.moveIsLegal(point, cwCheck) && inRange(moveDestination(point, cwCheck), waypoint)) {
					return Optional.of(cwCheck);
				}
				int acwCheck = mod360(bearingTowardsWaypoint - i);  // Check 10 degrees further anti-clockwise to see if that makes the move legal
				if (noFlyZoneChecker.moveIsLegal(point, acwCheck) && inRange(moveDestination(point, acwCheck), waypoint)) {
					return Optional.of(acwCheck);
				}
			}
		}
		// The path to the waypoint is completely blocked by a no-fly-zone
		return Optional.empty();
	}

	// Tries to compute a legal path for the drone to take to avoid an obstruction
	// Returns the path (as a list of bearings) if successful
	private List<Integer> computePathAroundObstruction(Waypoint waypoint) {
			var startPoint = drone.getPosition();
			
			// CWBranch and ACWBranch explore clockwise and anti-clockwise around the obstruction respectively 
			var CWBranch = new SearchBranch(startPoint, waypoint, true, noFlyZoneChecker);
			var ACWBranch = new SearchBranch(startPoint, waypoint, false, noFlyZoneChecker);
			
			// Fully explore both branches until they either find a way around the obstruction or they get stuck
			CWBranch.evaluate();
			ACWBranch.evaluate();
	
			if (CWBranch.isStuck() && ACWBranch.isStuck()) {  // If both branches got stuck, we failed to compute a path around the obstruction 
				return new ArrayList<Integer>();  
			} else if (CWBranch.isStuck()) {                  // If only one gets stuck, return the other one
				return ACWBranch.getBearingsTaken();
			} else if (ACWBranch.isStuck()) {
				return CWBranch.getBearingsTaken();
			}
			
			// If both branches completed, return the path of the one with the lower heuristic
			return (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch.getBearingsTaken() : ACWBranch.getBearingsTaken();
		}
	
	// Creates a new log entry (String) and adds it to log
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
	
	public List<Point> getPathTaken() {
		return pathTaken;
	}
	
	public HashMap<Sensor, Boolean> getSensorsVisited() {
		return sensorsVisited;
	}
	
	// Returns the concatenation of all log strings in the log list 
	public String getLog() {
		return String.join("", log);
	}

	private static class NoFlyZoneChecker {
	
		private BoundingBox droneConfinementArea;
	
		// Map with no-fly-zones as the values and their bounding boxes as the keys
		private HashMap<Polygon, Polygon> boundariesWithNoFlyZones = new HashMap<>();
		
		// Creates a NoFlyZoneChecker object that checks the legality of moves against the provided no-fly-zones and confinement area
		public NoFlyZoneChecker(List<Polygon> noFlyZones, BoundingBox droneConfinementArea) {
			calculateBoundaries(noFlyZones);  // Populates boundariesWithNoFlyZones
			this.droneConfinementArea = droneConfinementArea;
		}
		
		// Returns true if the move starting at the specified point, moving in the direction of the specified bearing would be legal
		public boolean moveIsLegal(Point point, int bearing) {
			var destination = moveDestination(point, bearing);  // Where the move lands
			// Move is not legal if it exits the drone confinement area
			if (!pointStrictlyInsideBoundingBox(destination, droneConfinementArea)) {
				return false;
			}
			
			// For each no-fly-zone
			for (var zone : boundariesWithNoFlyZones.keySet()) {
				// If the move's start or end point are inside the no-fly-zone's bounding box
				if (pointStrictlyInsideBoundingBox(point, zone.bbox()) || pointStrictlyInsideBoundingBox(destination, zone.bbox())) {
					// We need to check collision with the entire no-fly-zone
					if (lineIntersectsPolygon(point, destination, boundariesWithNoFlyZones.get(zone))) {
						return false;
					}
				// Otherwise, if the move intersects with the no-fly-zone's bounding box (just clips the corner)
				} else if (lineIntersectsPolygon(point, destination, zone)){
					// We also need to check collision with the entire no-fly-zone
					if (lineIntersectsPolygon(point, destination, boundariesWithNoFlyZones.get(zone))) {
						return false;
					}
				}
			}
			return true;
		}
		
		// Returns true if the move starting at the specified point, moving in the direction of the specified bearing terminates inside a no-fly-zone
		public boolean moveLandsInNoFlyZone(Point point, int bearing) {
			// For each no-fly-zone, check the specified move lands inside it
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
			return lng > bound.west() 
					&& lng < bound.east() 
					&& lat > bound.south() 
					&& lat < bound.north(); 
		}
		
		// Details on how this works will be in Section 3 of the report
		// Returns true if the line segment defined by the points start and end intersects with any of the line segments that make up poly
		private static boolean lineIntersectsPolygon(Point start, Point end, Polygon poly) {
			// Start and end points of the line
			var S = start;
			var E = end;
			
			var SE = toVector(S, E);
			
			var polyPoints = poly.coordinates().get(0);  // We only need the first coordinate list because our no-fly-zones have no inner points
			// For each line segment that makes up the no-fly-zone
			for (int i = 0; i < polyPoints.size() - 1; i++) {
				
				var P = polyPoints.get(i);
				var Q = polyPoints.get(i+1);
				var PQ = toVector(P, Q);  // Vector representing a no-fly-zone line segment 
				
				var SP = toVector(S, P);  // Sorry, you definitely need to check section 3.4 of the report 
				var SQ = toVector(S, Q);
				var PS = toVector(P, S);
				var PE = toVector(P, E);
				
				// If the start and end points of the move are on different sides of a no-fly-zone line segment
				// AND if the no-fly-zone line segment crosses the line segment between the start and end of the move
				if (vectorsOppositeSidesOfLine(PS, PE, PQ) && vectorsOppositeSidesOfLine(SP, SQ, SE)) {
					return true;  // The line intersects the polygon
				}
			}
			return false;
		}
		
		// Calculates the rectangle that fully encloses each noFlyZone, stores them in boundariesWithNoFlyZones
		private void calculateBoundaries(List<Polygon> noFlyZones) {
			for (var noFlyZone : noFlyZones) {
				
				double minLong = Double.MAX_VALUE; 
				double maxLong = Double.MIN_VALUE;
				double minLat = Double.MAX_VALUE;
				double maxLat = Double.MIN_VALUE;
	
				for (var point : noFlyZone.coordinates().get(0)) {
					var lng = point.longitude();
					var lat = point.latitude();
					
					// Finding the bounds of the no-fly-zone (just min/max search)
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
				
				// Add a little buffer room to the bounding box just in case
				double epsilon = 0.00005;  
				minLong -= epsilon;
				maxLong += epsilon;
				minLat -= epsilon;
				maxLat += epsilon;
				
				// Bounding box corners
				var northEast = Point.fromLngLat(maxLong, maxLat);
				var northWest = Point.fromLngLat(minLong, maxLat);
				var southWest = Point.fromLngLat(minLong, minLat);
				var southEast = Point.fromLngLat(maxLong, minLat);
				
				// Creates an actual BoundingBox object
				var boundingBox = BoundingBox.fromPoints(southWest, northEast);
				
				var points = new ArrayList<Point>(
						Arrays.asList(northEast, northWest, southWest, southEast, northEast));
				// Polygon.fromLngLats needs a list of list of points
				var singletonListOfPoints = new ArrayList<List<Point>>(Arrays.asList(points));
				
				// Just the bounding box but as a polygon so that we can check for intersection
				// Sets the polygon's bbox property as well
				var boundingBoxAsPolygon = Polygon.fromLngLats(singletonListOfPoints, boundingBox);
				// no-fly-zone bounding box/polygon is the key, the full no-fly-zone is the value
				boundariesWithNoFlyZones.put(boundingBoxAsPolygon, noFlyZone);
			}
		}
		
		// The points start and end define a line segment
		// This line segment is then moved so that start now lies on the origin
		// The point end can then represent a 2D vector from the origin
		private static Point toVector(Point start, Point end) {
			return Point.fromLngLat(
					end.longitude() - start.longitude(),
					end.latitude() - start.latitude());
		}
		
		// Returns the (Z component of) the cross product of vectorA and vectorB
		private static double cross(Point vectorA, Point vectorB) {
			return vectorA.longitude()*vectorB.latitude() - vectorA.latitude()*vectorB.longitude();
		}
		
		// Returns true if vectorA and vectorB are on opposite sides of the line defined by lineVector
		private static boolean vectorsOppositeSidesOfLine(Point vectorA, Point vectorB, Point lineVector) {
			return (cross(lineVector, vectorA) >= 0) ^ (cross(lineVector, vectorB) >= 0);
		}
	}

	private static class SearchBranch {
	
			// Current head of the search branch
			private Point branchHead;           
			
			// The target waypoint for the branch 
			// Helps determine when to stop searching
			private final Waypoint goal;            
			
			private boolean stuck = false;      
			private final boolean clockwise;
			
			// List of bearings taken by the search branch
			List<Integer> bearingsTaken = new ArrayList<>();
			NoFlyZoneChecker noFlyZoneChecker;
			
			public SearchBranch(Point startPoint, Waypoint goal, boolean clockwise, NoFlyZoneChecker noFlyZoneChecker) {
				this.branchHead = startPoint;
				this.clockwise = clockwise;
				this.goal = goal;
				this.noFlyZoneChecker = noFlyZoneChecker;
			}
			
			// Repeatedly expands the branch until it finishes or gets stuck
			public void evaluate() {
				while (!stuck) {
					expand();
					if (isFinished()) {
						break;
					}
				}
			}
			
			// Tries to expand the search branch by one move (updating branchHead and bearingsTaken)
			private void expand() {
				int mostDirectBearing = bearingFromTo(branchHead, goal);
	
				// We stop scanning if the first legal bearing we find is 180 degrees from the one we just took
				int limit = bearingsTaken.isEmpty() ? mostDirectBearing : backtrackBearing();
				int step =  clockwise ? 10 : -10;
				
				// Scan for the first legal bearing
				var legalBearing = bearingScan(mod360(mostDirectBearing + step), limit, step);
				if (legalBearing.isPresent()) {
					var newBearing = legalBearing.get();
					branchHead = moveDestination(branchHead, newBearing);  // Update the branch head with the bearing
					bearingsTaken.add(newBearing);                         // Remember which bearing we took so the drone can follow the same path later
				} else {
					stuck = true;
				}	
			}
			
			// Checks the legality of the moves with bearings in the range scanFrom-scanTo (step is the interval)
			private Optional<Integer> bearingScan(int scanFrom, int scanTo, int step) {
				// Looks scary, but it's just a for loop with modulo so that our bearings don't go below 0 or above 350
				// Check the legality of the moves with the bearings between scanFrom and scanTo
				for (int bearing = scanFrom; bearing != scanTo; bearing = mod360(bearing + step)) {

					if (noFlyZoneChecker.moveIsLegal(branchHead, bearing)) {
						return Optional.of(bearing);
					}
				}
				return Optional.empty();
			}
			
			// Returns true if the branch has found a legal path around the obstruction
			private boolean isFinished() {
				// Obvious if we're stuck we're not finished
				if (stuck) {
					return false;
				}
				
				// FIRST
				// Check if we're already in range (corner cutting for example)
				if (inRange(branchHead, goal)) {
					return true;
				}
				
				// SECOND
				// Check if there is a move towards the waypoint that is legal
				var bearingTowardsWaypoint = mostDirectBearing(branchHead, goal, noFlyZoneChecker);
				if (bearingTowardsWaypoint.isPresent()) {
					var bearing = bearingTowardsWaypoint.get();
					// Take it if it lands us in range of a sensor (end of section 3.3)
					if (inRange(moveDestination(branchHead, bearing), goal)) {
						bearingsTaken.add(bearing);
						return true;
					}
					
					// If it doesn't, take it as long as it doesn't just move us back a step (also end of section 3.3)
					if (bearing != backtrackBearing()) {
						bearingsTaken.add(bearing);
						return true;
					}
				}
				// Otherwise, we're not finished
				return false;
			}
			
			// Returns the bearing the branch last took - 180
			private int backtrackBearing() {
				int lastBearing = bearingsTaken.get(bearingsTaken.size() - 1);
				return mod360(lastBearing - 180);
			}
			
			// Returns the length of the branch + the euclidean distance to the goal
			public double getHeuristic() {
				return stuck ? Double.MAX_VALUE : (bearingsTaken.size()*Drone.MOVE_DISTANCE) + distanceBetween(branchHead, goal.getPoint());
			}
			
			public List<Integer> getBearingsTaken() {
				return bearingsTaken;
			}
			
			// Returns whether the branch was marked as stuck when evaluating it
			// Always returns false if the branch gets too long
			public boolean isStuck() {
				return stuck ? stuck : (bearingsTaken.size() > Drone.MAX_MOVES);
			}
			
		}
	
}
