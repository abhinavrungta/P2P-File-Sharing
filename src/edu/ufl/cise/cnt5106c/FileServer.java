package edu.ufl.cise.cnt5106c;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class FileServer {
	public static final double chunkSize = 100 * 1024.0;
	public int sPort = 8000; // The server will be listening on
	public HashMap<String, Boolean[]> chunksDelivered = new HashMap<String, Boolean[]>();
	private int maxClientNum = 5;
	public String currentDir = null;

	public FileServer() {
		String dir = this.getClass().getClassLoader().getResource("").getPath();
		String packageName = this.getClass().getPackage().getName().replace(".", File.separator);
		this.currentDir = dir + packageName + File.separator;
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("Must specify listening port and atleast one file name.");
			System.exit(0);
		}
		FileServer server = new FileServer();
		// get listening port for server.
		server.sPort = Integer.parseInt(args[0]);

		// split all the files locally on the file server.
		for (int i = 1; i < args.length; i++) {
			server.split(args[i]);
			int noOfParts = server.getNumberParts(args[i]);
			Boolean[] chunks = new Boolean[noOfParts];
			for (int j = 0; j < noOfParts; j++)
				chunks[j] = false;
			server.chunksDelivered.put(args[i], chunks);
		}
		System.out.println("The server is running.");

		ServerSocket listener = new ServerSocket(server.sPort);
		int clientNum = 1;
		try {
			while (true) {
				new FileServerHandler(listener.accept(), clientNum, server).start();
				System.out.println("Client " + clientNum + " is connected!");
				clientNum++;
			}
		} finally {
			listener.close();
		}
	}

	public int getMaxClientNum() {
		return this.maxClientNum;
	}

	public void split(String filename) throws FileNotFoundException, IOException {
		// open the file
		filename = this.currentDir + filename;
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));

		// get the file length
		File f = new File(filename);
		long fileSize = f.length();

		// loop for each full chunk
		int subfile;
		int noOfChunks = (int) (fileSize / chunkSize);
		for (subfile = 1; subfile <= noOfChunks; subfile++) {
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename + "." + subfile));

			for (int currentByte = 0; currentByte < chunkSize; currentByte++) {
				out.write(in.read());
			}
			out.close();
		}

		// loop for the last chunk (which may be smaller than the chunk size)
		if (fileSize != chunkSize * (subfile - 1)) {
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename + "." + subfile));
			int b;
			while ((b = in.read()) != -1)
				out.write(b);
			out.close();
		}
		in.close();
	}

	public int getNumberParts(String baseFilename) throws IOException {
		File f = new File(this.currentDir + baseFilename);
		long fileSize = f.length();
		return (int) Math.ceil(fileSize / chunkSize);
	}
}

/**
 * A handler thread class. Handlers are spawned from the listening loop and are
 * responsible for dealing with a single client's requests.
 */
class FileServerHandler extends ServerHandler {
	private int clientId; // The index number of the client
	private FileServer server;

	public FileServerHandler(Socket connection, int no, FileServer server) {
		this.connection = connection;
		this.server = server;
		this.clientId = no;
	}

	public void run() {
		try {
			// initialize Input and Output streams
			out = new ObjectOutputStream(connection.getOutputStream());
			out.flush();
			in = new ObjectInputStream(connection.getInputStream());
			while (true) {
				// receive the message sent from the client
				incomingMessage = (String) in.readObject();
				// show the message to the user
				System.out.println("Request: " + incomingMessage + " from client " + clientId);
				// Incoming message is of the type GETFILE:Filename
				if (incomingMessage.startsWith("GETFILE:")) {
					// send few chunks of the file to the client.
					sendChunksToClient(incomingMessage.substring(8));
				}
			}
		} catch (ClassNotFoundException classnot) {
			System.err.println("Data received in unknown format");
		} catch (IOException ioException) {
			System.out.println("Disconnect with Client " + clientId);
		} finally {
			// Close connections
			try {
				in.close();
				out.close();
				connection.close();
			} catch (IOException ioException) {
				System.out.println("Disconnect with Client " + clientId);
			}
		}
	}

	// send few chunks of the file to the client.
	public void sendChunksToClient(String fileName) {
		try {
			double noOfParts = server.getNumberParts(fileName);
			Boolean[] chunksDelievered = server.chunksDelivered.get(fileName);
			ArrayList<Integer> listOfChunksToDeliver = new ArrayList<>();
			int itr = 0;
			int chunkNo = this.clientId + (itr * server.getMaxClientNum());
			synchronized (chunksDelievered) {
				while (chunkNo <= noOfParts) {
					if (!chunksDelievered[chunkNo - 1]) {
						chunksDelievered[chunkNo - 1] = true;
						listOfChunksToDeliver.add(chunkNo);
						itr++;
						chunkNo = this.clientId + (itr * server.getMaxClientNum());
					}
				}
			}
			out.writeInt((int) noOfParts);
			Iterator<Integer> it = listOfChunksToDeliver.iterator();
			out.writeInt((int) listOfChunksToDeliver.size());
			while (it.hasNext()) {
				int part = it.next();
				System.out.println("Sending File: " + fileName + " Part No: " + part + " to Client " + clientId);
				sendFile(out, server.currentDir, fileName, part);
			}
			out.flush();
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}
}