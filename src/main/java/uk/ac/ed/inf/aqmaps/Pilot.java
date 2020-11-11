package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Stack;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;


public class Pilot {

	private Drone drone;
	
	// Given to gjg
	private List<Point> path = new ArrayList<>();
	private List<String> log = new ArrayList<>();
	
	private int lastBearingTaken;  // This might break if the first move hits a wall
	private Point previousPosition;
	
	private Queue<Integer> precomputedBearings = new LinkedList<>();
	
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	private NoFlyZoneChecker nfzc;
	
	private Random rand = new Random();
	
	// When looking for the best bearing, look this many steps ahead
	private final int LOOKAHEAD = 4;
	
	public Pilot(Drone drone, NoFlyZoneChecker nfzc) {
		this.drone = drone;
		this.nfzc = nfzc;
		for (var sensor : drone.getSensors()) {
			sensorReports.put(sensor, new SensorReport(false, false));
		}
		path.add(drone.getPosition());
	}
	
	// TODO: Write common terminology somewhere and standardise it
	public boolean followRoute(List<Waypoint> route) {
		var start = new Waypoint(drone.getPosition());
		lastBearingTaken = nearestBearing(drone.getPosition(), route.get(0).getPoint());
		for (Waypoint s : route) {
			boolean arrived = navigateTowards(s);
			if (!arrived) {
				return false;
			}
			var reading = drone.readSensor((Sensor) s);
			var report = sensorReports.get(s);
			if (reading.isPresent()) {
				report.setValid(true);
			}
			report.setVisited(true);
		}
		return navigateTowards(start);  // True if we make it back, false if not
	}
	
	private boolean navigateTowards(Waypoint waypoint) {
		
		boolean arrived = false;
		while (!arrived) {
			
			int breakp = path.size();
			
			if (drone.outOfMoves()) {
				break;
			}
			
			previousPosition = drone.getPosition();
			
			var targetPoint = waypoint.getPoint();
						
			int bearing = bestLegalBearing(targetPoint);
			
			var move = drone.move(bearing);
			
			if (!move) {
				throw new RuntimeException("FUCK");
			}
			
			path.add(drone.getPosition());
						
			lastBearingTaken = bearing;
			
			String w3wLocation = null;
			
			var temp = distanceBetween(drone.getPosition(), targetPoint); 
			
//			System.out.println(temp);
			
			if (temp <= drone.SENSOR_READ_DISTANCE) {
				if (waypoint instanceof Sensor) {
					w3wLocation = ((Sensor) waypoint).getW3wAddress();
				}
				arrived = true;
			}
			
			var pos = drone.getPosition();
			log.add(String.format("%d,%f,%f,%d,%f,%f,%s",
					drone.getTimesMoved(),
					previousPosition.longitude(),
					previousPosition.latitude(),
					lastBearingTaken,
					pos.longitude(),
					pos.latitude(),
					w3wLocation == null ? "null" : w3wLocation));
			
//			System.out.println(LineString.fromLngLats(path).toJson());
			
		}
		return arrived;
	}
	
	public List<Point> getPath() {
		return path;
	}
	
	public HashMap<Sensor, SensorReport> getSensorReports() {
		return sensorReports;
	}
	
	public List<String> getLog() {
		return log;
	}
	
	public Optional<Point> testMove(Point pos, int bearing) {
		
		
		
		var destination = moveDestination(pos, 0.0003, bearing);
		
		if (nfzc.isMoveLegal(pos, destination)) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
		}
	}
	
	
	// This should precompute the path to the next waypoint and store it
	// Then this should return what's in that if it's not empty
	
	// We need to make sure that this only runs when it needs to
	// That means that when we finally stop running into walls we should stop the search
	
//	private int bestLegalBearing(Point target) {
//				
//		if (!precomputedBearings.isEmpty()) {
//			return precomputedBearings.pop();
//		}
//		
//		var dronePos = drone.getPosition();
//		
//		var near = nearestBearing(dronePos, target);
//		
//		if (testMove(dronePos, near).isPresent()) {
//			return near;
//		}
//		
//		var euclidToTarget = new HashMap<Point, Double>();
//		euclidToTarget.put(dronePos, distanceBetween(dronePos, target));
//		
//		var distanceFromStartToNode = new HashMap<Point, Double>();
//		distanceFromStartToNode.put(dronePos, 0.0);
//		
//		var previousNode = new HashMap<Point, Point>();
//		
//		Comparator<Point> comp = (Point a, Point b) -> Double.compare(
//				distanceFromStartToNode.get(a) + euclidToTarget.get(a),
//				distanceFromStartToNode.get(b) + euclidToTarget.get(b));
//		var priorityQueue = new PriorityQueue<Point>(comp);
//		priorityQueue.add(dronePos);
//		
//		// I don't  think this is what's breaking it but we need to move once. 
//		// Basically first time it's called we can't do the distance check. 
//		int count = 0;
//		while (!priorityQueue.isEmpty()) {
//			var node = priorityQueue.poll();
//			
////			System.out.println(priorityQueue.size());
//			
//			// || testMove(node, nearestBearing(node, target)).isPresent()
//			
//			if (count > 0) {
//				if (distanceBetween(node, target) < 0.0002 ) {
//					
//					while (node != dronePos) {
//						
//						var prev = previousNode.get(node);
//						
//						
//						precomputedBearings.push(nearestBearing(prev, node));
//						node = prev;
//					}
//					
//					// Will break here if we start in the target
//					return precomputedBearings.pop();
//				}
//			}
//			
//			count += 1;
//			
//			for (int bear = 0; bear <= 350; bear += 10) {
//				var moveOpt = testMove(node, bear);
//				if (moveOpt.isPresent()) {
//					var move = moveOpt.get();
//					euclidToTarget.put(move, distanceBetween(move, target));
//					distanceFromStartToNode.put(move, distanceFromStartToNode.get(node) + 0.0003);
//					previousNode.put(move, node);
//					priorityQueue.add(move);
//				}
//			}
//		}
//		// If we don't find anything, this will break things.
//		return -1;
//	}
	
	private int bestLegalBearing(Point target) {
		
		if (!precomputedBearings.isEmpty()) {
			return precomputedBearings.poll();
		}
		
		int nearestBearing = nearestBearing(drone.getPosition(), target);
		var dronePos = drone.getPosition();
		
		if (testMove(dronePos, nearestBearing).isPresent()) {
			return nearestBearing;
		}
		
		// Distance around the building going clockwise/anticlockwise
		
		List<Integer> clockwiseDirections = new ArrayList<>();
		List<Integer> antiClockwiseDirections = new ArrayList<>();
		
		double clockwiseDist = 0;
		double antiClockwiseDist = 0;
		
		int clockwiseLength = 0;
		int antiClockwiseLength = 0;
		
		Point clockwisePosition = dronePos;
		Point antiClockwisePosition = dronePos;
		
		
		
		while (clockwiseDist < Double.MAX_VALUE || antiClockwiseDist < Double.MAX_VALUE) {
			
			if (clockwiseDist < antiClockwiseDist) {
				int nearest = nearestBearing(clockwisePosition, target);
				var newBearing = bearingScan(clockwisePosition, nearest, -10, clockwiseDirections.isEmpty() ? nearest : mod360(clockwiseDirections.get(clockwiseDirections.size()-1) - 180));
				if (newBearing.isPresent()) {
					clockwisePosition = testMove(clockwisePosition, newBearing.get()).get();
					clockwiseDirections.add(newBearing.get());
					clockwiseLength += 1;
					clockwiseDist = clockwiseLength*0.0003 + distanceBetween(clockwisePosition, target);
					
					nearest = nearestBearing(clockwisePosition, target);

					
					boolean tMove = testMove(clockwisePosition, nearest).isPresent();
					int backtrack = mod360(clockwiseDirections.get(clockwiseDirections.size() - 1) - 180);
					
//					System.out.println(nearest);
//					System.out.println(tMove);
//					System.out.println(backtrack);
					
					if ((tMove && (nearest != backtrack)) || distanceBetween(clockwisePosition, target) < 0.0002) {
						precomputedBearings.addAll(clockwiseDirections);
						return precomputedBearings.poll();
					}
					
					
				} else {
					clockwiseDist = Double.MAX_VALUE;
					continue;
				}
			} else {
				int nearest = nearestBearing(antiClockwisePosition, target);
				var newBearing = bearingScan(antiClockwisePosition, nearest, 10, antiClockwiseDirections.isEmpty() ? nearest : mod360(antiClockwiseDirections.get(antiClockwiseDirections.size()-1) - 180));
				if (newBearing.isPresent()) {
					antiClockwisePosition = testMove(antiClockwisePosition, newBearing.get()).get();
					antiClockwiseDirections.add(newBearing.get());
					antiClockwiseLength += 1;
					antiClockwiseDist = antiClockwiseLength*0.0003 + distanceBetween(antiClockwisePosition, target);
					
					nearest = nearestBearing(antiClockwisePosition, target);
					
					
					// End if there is a straight shot to the waypoint and the straight shot doesn't put us in the space we were just in
					// We know the space we were just in did not give us a straight shot
					// Also, 
					if ((testMove(antiClockwisePosition, nearest).isPresent() && nearest != mod360(antiClockwiseDirections.get(antiClockwiseDirections.size() - 1) - 180)) || distanceBetween(antiClockwisePosition, target) < 0.0002) {
						precomputedBearings.addAll(antiClockwiseDirections);
						return precomputedBearings.poll();
					}
					
				} else {
					antiClockwiseDist = Double.MAX_VALUE;
					continue;
				}
			}
		}
		throw new IllegalStateException("The drone cannot escape and is stuck for eternity :(");
	}
		


		

		
		

		
//	// We need to return
//	private BuildingPath pathAroundBuilding(boolean clockwise) {
//		arstoienarstoienarstoienarstoienoyunoyuoyunoyunnuyoun
//	}
	
	
	// Will return empty if nothing is found which is very unlikely
	private Optional<Integer> bearingScan(Point position, int startBearing, int offset, int limitBearing) {
		
		Optional<Integer> newBearing = Optional.empty();
		
		int bearing = mod360(startBearing + offset);
		while (bearing != limitBearing) {
			 if (testMove(position, bearing).isPresent()) {
				 newBearing = Optional.of(bearing);
				 break;
			 }
			 bearing = mod360(bearing + offset);
		}
		return newBearing;
		
	}
	
//	private int bestLegalBearing(Point target) {
//		
//		// this will break if the first move it runs into a building
//		
////		int nearestBearing = nearestBearing(drone.getPosition(), target);
//		// base case - returns the best bearing
////		if (testMove(drone.getPosition(), nearestBearing).isPresent()) {
////			return nearestBearing;
////		}
//		
//		if (precomputedBearings.isEmpty()) {
//			var pppp = bestLegalBearingRecurse(drone.getPosition(), target, lastBearingTaken, LOOKAHEAD);
//			Collections.reverse(pppp);
//			precomputedBearings.addAll(pppp);
//		}
//		
//		int pee = precomputedBearings.remove();
//		
//		
//		return pee;
//		
//	}
	
	// This might getting stuck, maybe not. 
	// This will return a bearing that does not collide with a building
	// TODO: Add lookahead
	// TODO: Caching for lookahead
//	private List<Integer> bestLegalBearingRecurse(Point curr, Point target, int lastBearing, int lookahead) {
//		
////		System.out.println(lastBearing);
//		
//		int nearestBearing = nearestBearing(curr, target);
//		int backtrackBearing = mod360(lastBearing - 180);  // Bearing that would return the drone to where it just was
//		
//		// base case - returns the best bearing
//		if (lookahead == 0 || testMove(curr, nearestBearing).isPresent()) {
//			return new ArrayList<Integer>(Arrays.asList(nearestBearing));
//		}
//		
//		// implement a* instnead of this
//
//		// so, when we hit a building, we'll do a*. There is no need to do that before because the direction that brings us the closest is obviously the direct bearing to the waypoint
//		// When we hit a building, we need to search all directions around the drone. Then, we order them in a priority queue. 
//		
//		Optional<Point> newCWPosition = Optional.empty();
//		Optional<Point> newACWPosition = Optional.empty();
//		
//		int cwBearing = mod360(nearestBearing + 10);
//		while (cwBearing != backtrackBearing) {
//			newCWPosition = testMove(curr, cwBearing);
//			if (newCWPosition.isPresent()) break;
//			cwBearing = mod360(cwBearing + 10);
//		}
//		
//		int acwBearing = mod360(nearestBearing - 10);
//		while (acwBearing != backtrackBearing) {
//			newACWPosition = testMove(curr, acwBearing);
//			if (newACWPosition.isPresent()) break;
//			acwBearing = mod360(acwBearing - 10);
//		}
//		
//		var bearing = new ArrayList<Integer>();
//		
//		if (newCWPosition.isEmpty() && newACWPosition.isEmpty()) {
//			return new ArrayList<Integer>();
//		} else if (newCWPosition.isEmpty()) {
//			var concat = bestLegalBearingRecurse(newACWPosition.get(), target, acwBearing, lookahead - 1);
//			concat.add(acwBearing);
//			return concat;
//		} else if (newACWPosition.isEmpty()) {
//			var concat = bestLegalBearingRecurse(newCWPosition.get(), target, cwBearing, lookahead - 1);
//			concat.add(cwBearing);
//			return concat;
//		} else {
//			
//			var concatCW = bestLegalBearingRecurse(newCWPosition.get(), target, cwBearing, lookahead - 1);
//			concatCW.add(cwBearing);
//			
//			var concatACW = bestLegalBearingRecurse(newACWPosition.get(), target, acwBearing, lookahead - 1);
//			concatACW.add(acwBearing);
//			
//			var cwDistance = distanceBetween(bearingListToDestination(curr, concatCW), target);
//			var acwDistance = distanceBetween(bearingListToDestination(curr, concatACW), target);
//			
//			if (cwDistance > acwDistance) {
//				return concatACW;
//			} else {
//				return concatCW;
//			}
//		}
//	}
//	
//	private static Point bearingListToDestination(Point start, List<Integer> bearings) {
//		Point pos = start;
//		var test = new ArrayList<>(bearings);
//		for (int bearing : test) {
//			
//			pos = moveDestination(pos, 0.0003, bearing);
//		}
//		return pos;
//	}
	
}
