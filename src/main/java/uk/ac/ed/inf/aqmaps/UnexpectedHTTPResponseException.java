package uk.ac.ed.inf.aqmaps;

public class UnexpectedHTTPResponseException extends Exception {

	private static final long serialVersionUID = 1L;

	public UnexpectedHTTPResponseException(String message) {
		super(message);
	}
	
}
