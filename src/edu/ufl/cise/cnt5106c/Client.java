package edu.ufl.cise.cnt5106c;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class SummaryFile implements java.io.Serializable {
	private static final long serialVersionUID = 123654321L;
	String baseFileName = null;
	Set<Integer> listOfChunks = null;
	int totalNoOfParts;

	public SummaryFile(String baseFile, int noOfParts) {
		this.baseFileName = baseFile;
		this.listOfChunks = new HashSet<>();
		this.totalNoOfParts = noOfParts;
	}

	public void addChunk(int chunkPartNo) {
		listOfChunks.add(chunkPartNo);
	}

	public Set<Integer> getChunksId() {
		return this.listOfChunks;
	}

	public String getBaseFileName() {
		return this.baseFileName;
	}

	public String toString() {
		String list = baseFileName + " : List : ";
		Iterator<Integer> itr = listOfChunks.iterator();
		while (itr.hasNext()) {
			list += itr.next() + ", ";
		}
		return list;
	}
}

public class Client extends ClientHandler {
	public HashMap<String, SummaryFile> summaryFile = new HashMap<String, SummaryFile>();
	int serverPort;
	String clientDir;

	public Client(int serverPort, int uploadPort) {
		this.serverPort = serverPort;
		String dir = this.getClass().getClassLoader().getResource("").getPath();
		String packageName = this.getClass().getPackage().getName().replace(".", File.separator);
		this.clientDir = dir + packageName + File.separator + "data-" + uploadPort + File.separator;
		File f = new File(this.clientDir);
		f.mkdirs();
	}

	public void run(String fileName) {
		requestSocket = connectToFileServerWithRetry(serverPort);
		System.out.println("Connected to localhost in port" + serverPort);
		try {
			// initialize inputStream and outputStream
			out = new ObjectOutputStream(requestSocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(requestSocket.getInputStream());

			// Send the sentence to the server
			requestFileFromServer(out, fileName);
			System.out.println("GETFILE: " + fileName);

			// get the response. first is the total number of parts of the file.
			int noOfParts = in.readInt();
			// create a summary File
			SummaryFile sf = new SummaryFile(fileName, noOfParts);
			summaryFile.put(fileName, sf);
			// process all the requested chunks from fileServer.
			recieveFiles(in);
			// update summary file on disk.
			String path = this.clientDir + fileName + ".summary";
			writeFileOnDisk(summaryFile.get(fileName), path);
		} catch (IOException ioException) {
			ioException.printStackTrace();
		} finally {
			// Close connections
			try {
				in.close();
				out.close();
				requestSocket.close();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	void recieveFiles(ObjectInputStream in) {
		try {
			int noOfChunks = in.readInt();
			int ct = 0;
			while (ct < noOfChunks) {
				FileData fd = (FileData) in.readObject();
				System.out.println("File Recieved: " + fd.baseFileName + "." + fd.partNo);
				String path = this.clientDir + fd.baseFileName + "." + fd.partNo;

				File f = new File(path);
				FileOutputStream out = new FileOutputStream(f);
				BufferedOutputStream bos = new BufferedOutputStream(out);

				bos.write(fd.data, 0, fd.length);
				bos.flush();
				bos.close();
				out.close();

				synchronized (summaryFile) {
					summaryFile.get(fd.baseFileName).addChunk(fd.partNo);
				}
				ct++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void join(String fileName, int parts) throws IOException {
		// open the file
		fileName = this.clientDir + fileName;
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fileName));

		// loop for each chunk
		for (int subfile = 1; subfile <= parts; subfile++) {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileName + "." + subfile));
			int b;
			while ((b = in.read()) != -1)
				out.write(b);
			in.close();
		}
		out.close();
	}

	// main method
	public static void main(String args[]) throws InterruptedException {
		int serverPort = Integer.parseInt(args[0]);
		int uploadPort = Integer.parseInt(args[1]);
		int downloadPort = Integer.parseInt(args[2]);
		String fileName = args[3];
		Client client = new Client(serverPort, uploadPort);
		client.run(fileName);
		new Uploader(client, uploadPort).start();
		new Downloader(client, downloadPort, fileName).start();
	}
}

class Uploader extends ServerHandler {
	Client client = null;
	int uploadPort;

	public Uploader(Client client, int uploadPort) {
		this.client = client;
		this.uploadPort = uploadPort;
		System.out.println("Uploader Started");
	}

	public void run() {
		Socket connection = null;
		ServerSocket listener = null;
		try {
			listener = new ServerSocket(this.uploadPort);
			connection = listener.accept();
			// initialize Input and Output streams
			out = new ObjectOutputStream(connection.getOutputStream());
			out.flush();
			in = new ObjectInputStream(connection.getInputStream());
			while (true) {
				// receive the message sent from the client
				incomingMessage = (String) in.readObject();
				// show the message to the user
				System.out.println("Request Recd: " + incomingMessage);
				// Incoming message is of the type GETFILE:Filename
				if (incomingMessage.startsWith("GETFILE:")) {
					// send few chunks of the file to the client.
					sendFileToClient(incomingMessage.substring(8));
				} else if (incomingMessage.startsWith("GETIDS:")) {
					// send summary.
					sendSummaryToClient(incomingMessage.substring(7));
				}
			}
		} catch (ClassNotFoundException classnot) {
			System.err.println("Data received in unknown format");
		} catch (IOException ioException) {
			System.out.println("Disconnect with Client ");
		} finally {
			// Close connections
			try {
				in.close();
				out.close();
				connection.close();
				listener.close();
			} catch (IOException ioException) {
				System.out.println("Disconnect with Client ");
			}
		}
	}

	// send the file to the client.
	public void sendFileToClient(String fileName) {
		try {
			out.writeInt(1);
			int index = fileName.lastIndexOf(".");
			int partNo = Integer.parseInt(fileName.substring(index + 1));
			System.out.println("Sending File: " + fileName.substring(0, index) + "." + partNo);
			sendFile(out, client.clientDir, fileName.substring(0, index), partNo);
		} catch (IOException ioException) {
			ioException.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendSummaryToClient(String baseFileName) {
		synchronized (client.summaryFile) {
			SummaryFile sf;
			if (client.summaryFile.containsKey(baseFileName)) {
				sf = client.summaryFile.get(baseFileName);
			} else {
				sf = new SummaryFile(baseFileName, 0);
			}
			System.out.println("Sending SummaryFile: " + sf.toString());
			try {
				out.reset();
				out.writeObject(sf);
				out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

class Downloader extends ClientHandler {
	Client client;
	String fileName;
	int downloadPort;

	public Downloader(Client client, int downloadPort, String fileName) {
		this.client = client;
		this.fileName = fileName;
		this.downloadPort = downloadPort;
	}

	public void run() {
		requestSocket = connectToFileServerWithRetry(this.downloadPort);
		System.out.println("Connected to Download Port :" + this.downloadPort);
		try {
			// initialize inputStream and outputStream
			out = new ObjectOutputStream(requestSocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(requestSocket.getInputStream());
			while (true) {
				requestChunkIdFromPeer(out, fileName);
				System.out.println("Requesting : GETIDS:" + fileName);
				Set<Integer> recievedList = recieveNewChunksList(in);
				Iterator<Integer> itr = recievedList.iterator();
				while (itr.hasNext()) {
					int part = itr.next();
					// Send the sentence to the server
					System.out.println("Requesting File: GETFILE:" + fileName + "." + part);
					requestFileFromServer(out, fileName + "." + part);
					// process the requested chunk from fileServer.
					client.recieveFiles(in);
				}
				// update summary file on disk.
				String path = client.clientDir + fileName + ".summary";
				writeFileOnDisk(client.summaryFile.get(fileName), path);
				SummaryFile currentSummary = client.summaryFile.get(fileName);
				if (currentSummary.totalNoOfParts == currentSummary.listOfChunks.size()) {
					System.out.println("Got all parts.");
					client.join(fileName, currentSummary.totalNoOfParts);
					break;
				}
				Thread.sleep(2000);
			}
		} catch (IOException ioException) {
			ioException.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			// Close connections
			try {
				in.close();
				out.close();
				requestSocket.close();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	Set<Integer> recieveNewChunksList(ObjectInputStream in) {
		try {
			SummaryFile sf = (SummaryFile) in.readObject();
			System.out.println("Recieved : ChunksList:" + sf.toString());
			Set<Integer> currentList = client.summaryFile.get(sf.baseFileName).getChunksId();
			Set<Integer> recievedList = sf.getChunksId();
			recievedList.removeAll(currentList);
			return recievedList;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return new HashSet<Integer>();
	}
}