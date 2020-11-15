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
	public Optional<Point> move(int bearing) {
		if (timesMoved >= MAX_MOVES) {
			return Optional.empty();
		}
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		position = destination;
		timesMoved += 1;
		return Optional.of(position);
	}

	// Tries to read the pollution sensor specified
	// Will return Optional of the reading, or an empty Optional if the sensor's battery is too low
	public Optional<Double> readSensor(Sensor sensor) {
		if (distanceBetween(position, sensor.getPoint()) < SENSOR_READ_DISTANCE) {
			if (sensor.getBattery() >= 10.0) {
				return Optional.of(sensor.getReading());
			} else {
				return Optional.empty();
			}
		} else {
			// This prevents an invalid program state
			// Should never happen as pilot is validating the instructions it sends to the drone
			throw new IllegalStateException("Drone is too far away to read sensor.");
		}
	}
	
	public Point getPosition() {
		return position;
	}

	public int getTimesMoved() {
		return timesMoved;
	}
	
}
