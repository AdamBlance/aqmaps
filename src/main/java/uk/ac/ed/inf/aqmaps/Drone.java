package uk.ac.ed.inf.aqmaps;

import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;

public class Drone {
	
	private Point position;
	private int timesMoved = 0;
	
	// These are public for easy access by the Pilot object
	public static final double MOVE_DISTANCE = 0.0003;
	public static final double SENSOR_READ_DISTANCE = 0.0002;
	public static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition) {
		position = startPosition;
	}

	// Tries to move and returns Optional of the new position
	// Will return an empty optional if the drone is out of moves
	public boolean move(int bearing) {
		if (timesMoved >= MAX_MOVES) {
			return false;
		}
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		position = destination;
		timesMoved += 1;
		return true;
	}

	// Tries to read the pollution sensor specified
	// Will return Optional of the reading
	public Optional<Double> readSensor(Sensor sensor) {
		Double reading = null;
		if (distanceBetween(position, sensor.getPoint()) < SENSOR_READ_DISTANCE) {
			reading = sensor.getReading();
		}
		return Optional.ofNullable(reading);
	}
	
	public Point getPosition() {
		return position;
	}

	public int getTimesMoved() {
		return timesMoved;
	}
	
}
