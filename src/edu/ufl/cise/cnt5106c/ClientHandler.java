package edu.ufl.cise.cnt5106c;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler extends Thread {
	Socket requestSocket; // socket connect to the server
	ObjectOutputStream out; // stream write to the socket
	ObjectInputStream in; // stream read from the socket

	public Socket connectToFileServerWithRetry(int serverPort) {
		while (true) {
			try {
				requestSocket = new Socket("localhost", serverPort);
				if (requestSocket != null) {
					break;
				}
			} catch (IOException e) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
		return requestSocket;
	}

	// send a message to the output stream
	void requestFileFromServer(ObjectOutputStream out, String fileName) {
		try {
			// stream write the message
			out.reset();
			out.writeObject("GETFILE:" + fileName);
			out.flush();
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	void requestChunkIdFromPeer(ObjectOutputStream out, String fileName) {
		try {
			// stream write the message
			out.reset();
			out.writeObject("GETIDS:" + fileName);
			out.flush();
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	public void writeFileOnDisk(Object sf, String path) throws IOException {
		File f = new File(path);
		FileOutputStream fileOut = new FileOutputStream(f);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.reset();
		out.writeObject(sf);
		out.flush();
		out.close();
		fileOut.close();
	}

}
