package uk.ac.ed.inf.aqmaps;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;

public class Drone {
	
	// maybe use Point idk
	
	private Point position;
	private HashMap<Point, SensorData> sensors;
	private NoFlyZoneChecker nfzc;
	
	private int timesMoved = 0;
	private int lastBearing = -1;  // This might break if the first move hits a wall
	
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
			position = destination;
			timesMoved += 1;
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
	
	// For the now
	// not sure about this one
//	public Optional<Float> getReading(Point sensor) {
//		Sensor sensorData = sensors.get(sensor);
//		if ((distance(position, sensor) < 0.0002) && (sensorData.getBattery() >= 10)) {
//			return Float.parseFloat(sensorData.getReading());
//		} else {
//			return null;
//		}
//	}
	
}
