package edu.ufl.cise.cnt5106c;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ServerHandler extends Thread {
	String incomingMessage; // message received from the client
	Socket connection;
	ObjectInputStream in; // stream read from the socket
	ObjectOutputStream out; // stream write to the socket

	public void sendFile(ObjectOutputStream out, String currentDir, String fileName, int partNo) throws IOException {
		File f = new File(currentDir + fileName + "." + partNo);
		InputStream in = new FileInputStream(f);
		BufferedInputStream bis = new BufferedInputStream(in);
		byte[] bytes = new byte[(int) f.length()];
		int count;
		count = bis.read(bytes, 0, bytes.length);
		FileData fd = new FileData(fileName, partNo, count, bytes);
		out.reset();
		out.writeObject(fd);
		out.flush();
		in.close();
	}
}
