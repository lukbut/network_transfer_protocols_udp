/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary*/

import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver2b {
	
	/*Static variables*/
	private static final int HEADER = 3; 
	private static final int LOAD = 1024;
	private static final int ACKOFFSET = 1;
	private static final int ENDOFFILE = 255;
	private static final int ACKN = 1;
	
	/*Instance variables*/
	private InetAddress address;
	private int port = 0;
	private int ackPort = 0;
	private String file_name = "";
	private DatagramSocket ds_send;
	private DatagramSocket ds_receive;
	private FileOutputStream fos = null;
	private int window_size;
	
	/*Constructor of receiver instance*/
	public Receiver2b(String iPort, String iFilename, String iWindow_size) throws Exception  {
		
		this.address = InetAddress.getByName("localhost");
		this.port = Integer.parseInt(iPort);
		this.ackPort = port + ACKOFFSET;
		this.file_name = iFilename;
		this.window_size = Integer.parseInt(iWindow_size);
		
		//Initialising datagram socket to handle sending of acknowledgement packages
		this.ds_send = new DatagramSocket();
		
		//Initialising datagram socket to handle receipt of packages
		this.ds_receive = new DatagramSocket(this.port, this.address);		
	}
	
	public void createReceivedFile() {
		
		File rf = new File(this.file_name);
		
		try {
			
			fos = new FileOutputStream(rf);
		
		} catch ( Exception e ) {
			
			System.out.println("Failed to create received file. Quitting...");
			e.printStackTrace();
			
		} 
	}
	
	public void receiveFile() throws Exception {
		
		/*16bit message sequence number*/
		int expectedSNum = 0;
		int lastSNum = -1;
		
		//Buffer data structure as a hashmap 
		HashMap<Integer, byte[]> buff = new HashMap<Integer,byte[]>();
		
		/*Continue indicator for while loop. If false, end
		 * of file has been reached*/
		boolean cont = true;
		
		/*Message to be received is a byte array.
		 * A separate byte array is being kept for the message
		 * without the header.
		 *  */
		byte[] msg = new byte[HEADER + LOAD];
		byte[] clipped_message = null;
		
		while (cont) {
			/*Receiving the packet*/
			DatagramPacket receivedP = new DatagramPacket(msg, msg.length);
			try {
				
				this.ds_receive.receive(receivedP);
				
			} catch (IOException e) {
				
				System.out.println("Receiving file failed. Quitting...");
				e.printStackTrace();
				System.exit(1);
				
			}
			
			/*Getting the data from the received packet*/
			msg = receivedP.getData();
			int length = receivedP.getLength();
			
			/*Parsing the message byte array contents.
			 * Technique adapted from the stackoverflow link above.*/
			int currSNum = ((msg[1] & 0xFF) << 8) + (msg[2] & 0xFF);
			
			/*If files were received in the expected window length, continue by buffering and sending acknowledgement*/
			if ((currSNum >= expectedSNum) && (currSNum < (expectedSNum + this.window_size))) {
				
				/*Extracting the end of file flag and assigning equivalent
				 * to cont. Technique adapted from the stackoverflow link above.*/
				if ((msg[0] & 0xFF) == ENDOFFILE) {
					
					cont = false;
					clipped_message = new byte[length - HEADER];
					lastSNum = currSNum;
					
					/*Extracting the message, reversing the encapsulation process*/
					for (int i = 3; i < length; i++) {
					
						clipped_message[i-3] = msg[i];
						
					}
				} else {
					
					cont = true;
					clipped_message = new byte[LOAD];
					
					/*Extracting the message, reversing the encapsulation process*/
					for (int i = 3; i < LOAD + HEADER; i++) {
						
						clipped_message[i-3] = msg[i];
						
					}
				}
				
				//Adding received message to buffer
				buff.put(currSNum, clipped_message);
	
				/*Finally, write the message to the file*/
				while (buff.get(expectedSNum) != null){
					try {
						
						fos.write(buff.get(expectedSNum));
						
						buff.remove(expectedSNum);
						
						//If this is the last SNum, end while loop
						if(expectedSNum == lastSNum) {
							
							cont = false;
							
						} else {
							
							/*Setting the expected sequence number to the next one*/
							expectedSNum++;
							
						}
						

						
					} catch (Exception e) {
						
						System.out.println("Failed to write message to file. Quitting...");
						e.printStackTrace();
						System.exit(1);
					}
				}
			} 
			
			//Sending acknowledgements
			byte[] acknowledge = new byte[3];
			acknowledge[0] = (byte) ACKN;
			acknowledge[1] = (byte) (currSNum >> 8);
			acknowledge[2] = (byte) (currSNum);
			
			System.out.println("Acknowledging packet number: " + currSNum);
			
			DatagramPacket ackP = new DatagramPacket(acknowledge, acknowledge.length, this.address, this.ackPort);
			
			try {
				
				this.ds_send.send(ackP);
				
			} catch (IOException e) {
				
				System.out.println("Failed to send acknowledgement. Quitting...");
				e.printStackTrace();
				System.exit(1);
			
			}
			
		}
		
			
		if ( fos != null ) {	
			try {
			
				/*Closing fileoutputstream and sockets*/
				ds_send.close();
				ds_receive.close();
				fos.close();
				
			} catch (Exception e) {
			
				System.out.println("Failed to close fileoutputstream and/or socket. Quitting...");
				e.printStackTrace();
				System.exit(1);
				
				}
			}
	}
	
	/*Main method to run receiver*/
	public static void main(String[] args) throws Exception {

		if(args.length != 3){

			System.out.println("Invalid number of parameters. Quitting...");
			System.exit(1);
			
		}
		else {
			try {
				
				Receiver2b receiver = new Receiver2b(args[0], args[1], args[2]);
				receiver.createReceivedFile();
				receiver.receiveFile();
				System.out.println("File received.");
				System.exit(0);
				
			}
			catch (Exception e) {
			
				System.out.println("Exception occured. Quitting...");
				e.printStackTrace();
				System.exit(1);
			
			}
		}
		
		/*Receiver2b receiver = new Receiver2b("54321", "src/Rtest.jpg", "5");
		receiver.createReceivedFile();
		receiver.receiveFile();
		System.out.println("File received.");
		System.exit(0);*/
	}
}
