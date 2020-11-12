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
	
	// Given to gjg
	private List<Point> path = new ArrayList<>();
	private List<String> log = new ArrayList<>();
	
	private Queue<Integer> precomputedBearings = new LinkedList<>();
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	private NoFlyZoneChecker nfzc;
		
	public Pilot(Drone drone, NoFlyZoneChecker nfzc) {
		this.drone = drone;
		this.nfzc = nfzc;
		path.add(drone.getPosition());
	}
	
	// TODO: Write common terminology somewhere and standardise it
	public boolean followRoute(List<Waypoint> route) {
		
		for (var waypoint : route) {
			sensorReports.put((Sensor) waypoint, new SensorReport(false, false));
		}
		
		var start = new Waypoint(drone.getPosition());
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
		
		var targetPoint = waypoint.getPoint();
		
		boolean arrived = false;
		while (!arrived) {
			
//			int breakp = path.size();
			
			var previousPosition = drone.getPosition();
			
			int bearing = bestLegalBearing(targetPoint);
			var newPositionOptional = drone.move(bearing);
			if (newPositionOptional.isEmpty()) {
				break;
			}
			
			var newPosition = newPositionOptional.get();
			
			String w3wLocation = null;
			if (distanceBetween(drone.getPosition(), targetPoint) <= drone.getSensorReadDistance()) {
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
	
	public HashMap<Sensor, SensorReport> getSensorReports() {
		return sensorReports;
	}
	
	public List<String> getLog() {
		return log;
	}
	
	private Optional<Point> testMove(Point pos, int bearing) {
		var destination = moveDestination(pos, 0.0003, bearing);
		
		if (nfzc.isMoveLegal(pos, destination)) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
		}
	}
	
	private class searchBranch {
		
		double branchDist = 0;
		int branchMoveCount = 0;
		Point branchHead;
		
		Point target;
		
		double moveDist;
		
		int step;
		
		List<Integer> branchDirections = new ArrayList<>();
		
		public searchBranch(Point startPoint, Point target, boolean clockwise, double moveDist) {
			this.branchHead = startPoint;
			this.moveDist = moveDist;
			step = clockwise ? -10 : 10;
		}
		
		public void explore() {
			
			int mostDirectBearing = mostDirectBearing(branchHead, target);
			
			int backtrack;
			if (branchDirections.isEmpty()) {
				backtrack = mostDirectBearing;
			} else {
				backtrack = mod360(lastBearing() - 180);
			}
			
			var newBearingOptional = bearingScan(branchHead, mostDirectBearing, step, backtrack);
			if (newBearingOptional.isPresent()) {
				var newBearing = newBearingOptional.get();
				branchHead = testMove(branchHead, newBearing).get();  // This isn't exactly graceful, could equally use getMoveResult
				branchDirections.add(newBearing);
				branchMoveCount += 1;
				branchDist = branchMoveCount * moveDist + distanceBetween(branchHead, target);
				
				// We've moved along the side of the building
				// Now, have we arrived at our destination, or found a clear direct path to the target?
			} else {
				branchDist = Double.MAX_VALUE;
			}
			
		}
		
		public boolean endingCheck() {
			if (distanceBetween(branchHead, target) < drone.getSensorReadDistance()) {
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
		
		public double getBranchDist() {
			return branchDist;
		}
		
		public List<Integer> getBranchDirections() {
			return branchDirections;
		}
		
	}
	
	private int bestLegalBearing(Point target) {
		
		if (!precomputedBearings.isEmpty()) {
			return precomputedBearings.poll();
		}
		
		int mostDirectBearing = mostDirectBearing(drone.getPosition(), target);
		var dronePos = drone.getPosition();
		
		if (testMove(dronePos, mostDirectBearing).isPresent()) {
			return mostDirectBearing;
		}
		
		// Distance around the building going clockwise/anticlockwise
		
		List<Integer> clockwiseDirections = new ArrayList<>();
		List<Integer> antiClockwiseDirections = new ArrayList<>();
		
		double clockwiseDist = 0;
		double antiClockwiseDist = 0;
		
		int clockwiseMoveCount = 0;
		int antiClockwiseMoveCount = 0;
		
		Point clockwisePosition = dronePos;
		Point antiClockwisePosition = dronePos;
		
		var moveDist = drone.getMoveDistance();
		
		while (clockwiseDist < Double.MAX_VALUE || antiClockwiseDist < Double.MAX_VALUE) {
			
			if (clockwiseDist < antiClockwiseDist) {
				mostDirectBearing = mostDirectBearing(clockwisePosition, target);
				var newBearing = bearingScan(clockwisePosition, mostDirectBearing, -10, clockwiseDirections.isEmpty() ? mostDirectBearing : mod360(clockwiseDirections.get(clockwiseDirections.size()-1) - 180));
				if (newBearing.isPresent()) {
					clockwisePosition = testMove(clockwisePosition, newBearing.get()).get();
					clockwiseDirections.add(newBearing.get());
					clockwiseMoveCount += 1;
					clockwiseDist = clockwiseMoveCount * moveDist + distanceBetween(clockwisePosition, target);
					
					mostDirectBearing = mostDirectBearing(clockwisePosition, target);

					boolean tMove = testMove(clockwisePosition, mostDirectBearing).isPresent();
					int backtrack = mod360(clockwiseDirections.get(clockwiseDirections.size() - 1) - 180);
					
					if ((tMove && (mostDirectBearing != backtrack)) || distanceBetween(clockwisePosition, target) < 0.0002) {
						precomputedBearings.addAll(clockwiseDirections);
						precomputedBearings.add(mostDirectBearing);
						return precomputedBearings.poll();
					}
				} else {
					clockwiseDist = Double.MAX_VALUE;
					continue;
				}
			} else {
				mostDirectBearing = mostDirectBearing(antiClockwisePosition, target);
				var newBearing = bearingScan(antiClockwisePosition, mostDirectBearing, 10, antiClockwiseDirections.isEmpty() ? mostDirectBearing : mod360(antiClockwiseDirections.get(antiClockwiseDirections.size()-1) - 180));
				if (newBearing.isPresent()) {
					antiClockwisePosition = testMove(antiClockwisePosition, newBearing.get()).get();
					antiClockwiseDirections.add(newBearing.get());
					antiClockwiseMoveCount += 1;
					antiClockwiseDist = antiClockwiseMoveCount * moveDist + distanceBetween(antiClockwisePosition, target);
					
					mostDirectBearing = mostDirectBearing(antiClockwisePosition, target);
					
					if ((testMove(antiClockwisePosition, mostDirectBearing).isPresent() && mostDirectBearing != mod360(antiClockwiseDirections.get(antiClockwiseDirections.size() - 1) - 180)) || distanceBetween(antiClockwisePosition, target) < 0.0002) {
						precomputedBearings.addAll(antiClockwiseDirections);
						precomputedBearings.add(mostDirectBearing);						
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
	
}
