package edu.ufl.cise.cnt5106c;

public class FileData implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 198712345L;
	String baseFileName;
	int partNo;
	int length;
	byte[] data;

	public FileData(String baseFileName, int partNo, int length, byte[] data) {
		this.baseFileName = baseFileName;
		this.partNo = partNo;
		this.length = length;
		this.data = data;
	}
}
