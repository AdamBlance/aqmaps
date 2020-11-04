package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import com.mapbox.geojson.Point;

public class Pilot {

	private Drone drone;
	
	// Given to gjg
	private List<Point> path = new ArrayList<>();
	private List<String> log = new ArrayList<>();
	
	private int lastBearingTaken = -1;  // This might break if the first move hits a wall
	private Point previousPosition;
	
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	// When looking for the best bearing, look this many steps ahead
	private final int LOOKAHEAD = 1;
	
	public Pilot(Drone drone) {
		this.drone = drone;
		for (var sensor : drone.getSensors()) {
			sensorReports.put(sensor, new SensorReport(false, false));
		}
		path.add(drone.getPosition());
	}
	
	// TODO: Write common terminology somewhere and standardise it
	public boolean followRoute(List<Sensor> route) {
		var start = new Waypoint(drone.getPosition());
		for (Sensor s : route) {
			boolean arrived = navigateTowards(s);
			if (!arrived) {
				return false;
			}
			var reading = drone.readSensor(s);
			var report = sensorReports.get(s);
			if (reading.isPresent()) {
				report.setValid(true);
			}
			report.setVisited(true);
		}
		return navigateTowards(start);  // True if we make it back, false if not
	}
	
	// TODO: Return false or something if drone can't reach sensor
	private boolean navigateTowards(Waypoint waypoint) {
		
		boolean arrived = false;
		while (!arrived) {
			
			if (drone.outOfMoves()) {
				break;
			}
			
			previousPosition = drone.getPosition();
			
			var targetPoint = waypoint.getPoint();
			
			int bearing = bestLegalBearing(targetPoint, LOOKAHEAD);
			
			drone.move(bearing);
			
			path.add(drone.getPosition());
			
			lastBearingTaken = bearing;
			
			String w3wLocation = null;
			if (distanceBetween(drone.getPosition(), targetPoint) <= drone.SENSOR_READ_DISTANCE) {
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
	
	// This will return a bearing that does not collide with a building
	// TODO: Add lookahead
	// TODO: Always just call this first. If the nearest bearing is already legal just return it. 
	// TODO: Caching for lookahead
	private int bestLegalBearing(Point target, int lookahead) {
		
		int nearestBearing = nearestBearing(drone.getPosition(), target);
		if (drone.testMove(nearestBearing).isPresent()) {
			return nearestBearing;
		}
		
		Optional<Point> newCWPosition = Optional.empty();
		Optional<Point> newACWPosition = Optional.empty();

		int backtrackBearing = mod360(lastBearingTaken - 180);  // Bearing that would return the drone to where it just was
		
		int cwBearing = mod360(nearestBearing + 10);
		while (cwBearing != backtrackBearing) {
			newCWPosition = drone.testMove(cwBearing);
			if (newCWPosition.isPresent()) break;
			cwBearing = mod360(cwBearing + 10);
		}
		
		int acwBearing = mod360(nearestBearing - 10);
		while (acwBearing != backtrackBearing) {
			newACWPosition = drone.testMove(acwBearing);
			if (newACWPosition.isPresent()) break;
			acwBearing = mod360(acwBearing - 10);
		}
		
		
		if (newCWPosition.isEmpty() && newACWPosition.isEmpty()) {
			throw new RuntimeException("We're stuck. Nice.");
		} else if (newCWPosition.isEmpty()) {
			return acwBearing;
		} else if (newACWPosition.isEmpty()) {
			return cwBearing;
		} else {
			var cwDistance = distanceBetween(newCWPosition.get(), target);
			var acwDistance = distanceBetween(newACWPosition.get(), target); 
			
			if (cwDistance > acwDistance) {
				return acwBearing;
			} else {
				return cwBearing;
			}
		}
	}
	
}
