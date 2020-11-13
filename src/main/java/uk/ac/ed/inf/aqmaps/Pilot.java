package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.mostDirectBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import com.mapbox.geojson.Point;


public class Pilot {

	private Drone drone;
	
	private List<Point> path = new ArrayList<>();
	private List<String> log = new ArrayList<>();
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	private Queue<Integer> precomputedBearings = new LinkedList<>();
	
	private NoFlyZoneChecker noFlyZoneChecker;
		
	public Pilot(Drone drone, NoFlyZoneChecker noFlyZoneChecker) {
		this.drone = drone;
		this.noFlyZoneChecker = noFlyZoneChecker;
		path.add(drone.getPosition());
	}
	
	public boolean followRoute(List<Waypoint> route) {
		createSensorReports(route);
		var start = new Waypoint(drone.getPosition());
		
		for (var waypoint : route) {
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
			
//			int breakp = path.size();
			
			var previousPosition = drone.getPosition();
			
			int bearing = nextBearing(waypoint.getPoint());
			var newPositionOptional = drone.move(bearing);
			if (newPositionOptional.isEmpty()) {
				break;
			}
			
			var newPosition = newPositionOptional.get();
			
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
			
//			System.out.println(LineString.fromLngLats(path).toJson());
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
	
	
	// I made a bunch of things static, maybe don't have to be. No fly zone is only used here. Could even move it into the checker itself? 
	
	private Optional<Point> testMove(Point pos, int bearing) {
		var destination = moveDestination(pos, 0.0003, bearing);
		
		if (noFlyZoneChecker.isMoveLegal(pos, destination)) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
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
		
		public SearchBranch(Point startPoint, Point target, boolean clockwise) {
			this.branchHead = startPoint;
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
		
		public boolean isFinished() {
			if (distanceBetween(branchHead, target) < Drone.SENSOR_READ_DISTANCE) {
				return true;
			}
			int mostDirectBearing = mostDirectBearing(branchHead, target);
			int backtrackBearing = mod360(lastBearing() - 180);
			boolean moveIsLegal = testMove(branchHead, mostDirectBearing).isPresent();
			
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
	
	private int nextBearing(Point target) {
		if (!precomputedBearings.isEmpty()) {
			return precomputedBearings.poll();
		}
		
		var dronePos = drone.getPosition();
		int mostDirectBearing = mostDirectBearing(dronePos, target);
		if (testMove(dronePos, mostDirectBearing).isPresent()) {
			return mostDirectBearing;
		}
		
		var CWBranch = new SearchBranch(dronePos, target, true);
		var ACWBranch = new SearchBranch(dronePos, target, false);
		
		while (!(CWBranch.isStuck() && ACWBranch.isStuck())) {	
			var shortestBranch = (CWBranch.getHeuristic() < ACWBranch.getHeuristic()) ? CWBranch : ACWBranch;
			shortestBranch.explore();
			if (shortestBranch.isFinished()) {
				precomputedBearings.addAll(shortestBranch.getBranchDirections());
				return precomputedBearings.poll();
			}
		}
		throw new IllegalStateException("The drone cannot escape and is stuck for eternity...");
	}
	
	private Optional<Integer> bearingScan(Point position, int startBearing, int offset, int limitBearing) {	
		Optional<Integer> legalBearing = Optional.empty();
		
		int bearing = mod360(startBearing + offset);
		while (bearing != limitBearing) {
			var move = testMove(position, bearing);
			if (move.isPresent()) {
				 legalBearing = Optional.of(bearing);
				 break;
			 }
			 bearing = mod360(bearing + offset);
		}
		return legalBearing;
	}
}
