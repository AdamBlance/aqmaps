package uk.ac.ed.inf.aqmaps;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;


public class Drone {
	
	// maybe use Point idk
	
	private Point position;
	private HashMap<Point, SensorData> sensors;
	private NoFlyZoneChecker nfzc;
	
	private int timesMoved = 0;
	private int lastBearing = -1;  // This might break if the first move hits a wall
	
	private List<LogData> flightLog = new ArrayList<>();
	
	private List<Point> path;
	
	private static final double MOVE_DISTANCE = 0.0003;
	private static final int MAX_MOVES = 150;
	
	// should maybe standardise lat(itude) etc
	
	public Drone(Point initialPos, HashMap<Point, SensorData> sensors, NoFlyZoneChecker nfzc) {
		position = initialPos;
		path = new ArrayList<>(Arrays.asList(position));
		this.nfzc = nfzc;
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
