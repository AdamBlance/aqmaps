package uk.ac.ed.inf.aqmaps;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.oppositeBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;


public class Drone {
	
	private Point position;
	private HashMap<Point, SensorData> sensors;
	private NoFlyZoneChecker nfzc;
	
	private int timesMoved = 0;
	private int lastBearing = -1;  // This might break if the first move hits a wall
	
	// This is good for logging, probably less helpful for geojson map
	private List<LogData> flightLog = new ArrayList<>();
	
	// This will be the one we give to the geojson thing
	private List<Point> path;
	
	// it would be interesting to know what to abbreviate, maybe look it up
	private static final double MOVE_DISTANCE = 0.0003;
	private static final double FLIGHT_COMPLETION_DISTANCE = 0.0003;
	private static final double SENSOR_READ_DISTANCE = 0.0002;
	private static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition, HashMap<Point, SensorData> sensors, NoFlyZoneChecker nfzc) {
		position = startPosition;
		path = new ArrayList<>(Arrays.asList(position));
		this.nfzc = nfzc;
	}
	
	// TODO: Write common terminology somewhere and standardise it
	public void followPath(List<Point> path) {
		// we ignore the first one because that's where we start				
		for (Point target : path.subList(1, path.size())) {
			navigateTowards(target);
			readSensor(target);
		}
	}
	
	// TODO: Return false or something if drone can't reach sensor
	public void navigateTowards(Point target) {
		
		while (distanceBetween(position, target) >= SENSOR_READ_DISTANCE && !outOfMoves()) {
			
			int bearing = nearestBearing(position, target);
			
			if (move(bearing).isEmpty()) {  // If move was not executed (illegal)
				
				move(bearingScan(bearing, target));
				

				
			}
		}
	}
	
	
	public int bearingScan(int originalBearing, Point target) {
		
		Optional<Point> newClockwisePosition = Optional.empty();
		Optional<Point> newAnticlockwisePosition = Optional.empty();

		int cwBearing = -69;
		int acwBearing = 420;
		
		int invLastBear = oppositeBearing(lastBearing);
		
		for (int offset = 10; mod360(originalBearing + offset) != invLastBear; offset += 10) {
			newClockwisePosition = testMove(mod360(originalBearing + offset));
			if (newClockwisePosition.isPresent()) {
				cwBearing = mod360(originalBearing + offset);
				break;
			}
		}
		
		for (int offset = 10; mod360(originalBearing - offset) != invLastBear; offset += 10) {
			newAnticlockwisePosition = testMove(mod360(originalBearing - offset));
			if (newAnticlockwisePosition.isPresent()) {
				acwBearing = mod360(originalBearing - offset);
				break;
			}
		}
		
		if (newClockwisePosition.isEmpty() && newAnticlockwisePosition.isEmpty()) {
			throw new RuntimeException("We're stuck. Nice.");
		} else if (newClockwisePosition.isEmpty()) {
			move(acwBearing);
		} else if (newAnticlockwisePosition.isEmpty()) {
			move(cwBearing);
		} else {
			
			if (distanceBetween(newClockwisePosition.get(), target) > distanceBetween(newAnticlockwisePosition.get(), target)) {
				move(acwBearing);
			} else {
				move(cwBearing);
			}
		}
		
		return 1;
		
	}

		
	// Maybe some redundancy here
	// Just for safety
	
	public Optional<Point> testMove(int bearing) {
		
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		
		if (nfzc.isMoveLegal(position, destination) && !outOfMoves()) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
		}
	}
	
	public Optional<Point> move(int bearing) {
		
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		
		if (nfzc.isMoveLegal(position, destination) && !outOfMoves()) {
			timesMoved += 1;
//			var log = new LogData(timesMoved, position, bearing, destination, );
			
			position = destination;

			lastBearing = bearing;
			

			

			
			return Optional.ofNullable(position);
		} else {
			return Optional.empty();
		}
	}
	
	public boolean outOfMoves() {
		return timesMoved >= MAX_MOVES;
	}
	
	public int getTimesMoved() {
		return timesMoved;
	}
	
	public Point getPosition() {
		return position;
	}
	
	public int getLastBearing() {
		return lastBearing;
	}
	
	public Optional<String> readSensor(Point sensor) {
		if (distanceBetween(position, sensor) < 0.0002) {
			var data = sensors.get(sensor);
			
			if (data.getBattery() >= 10.0) {
				return Optional.of(data.getReading());
			} else {
				return Optional.empty();
			}
			
		} else {
			throw new RuntimeException("You numpty. Too far away from sensor.");
		}
	}
	
}
