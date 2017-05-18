/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary*/

import java.io.*;
import java.net.*;

public class Receiver2a {
	
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
	
	/*Constructor of receiver instance*/
	public Receiver2a(String iPort, String iFilename) throws Exception  {
		
		this.address = InetAddress.getByName("localhost");
		this.port = Integer.parseInt(iPort);
		this.ackPort = port + ACKOFFSET;
		this.file_name = iFilename;
		
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
		int currSNum = -1;
		int expectedSNum = 0;
		
		/*Continue indicator for while loop. If false, end
		 * of file has been reached*/
		boolean cont = true;
		
		/*Message to be received is a byte array.
		 * A separate byte array is being kept for the message
		 * without the header.
		 *  */
		byte[] msg = new byte[HEADER + LOAD];
		byte[] clipped_message = null;
		
		do {
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
			currSNum = ((msg[1] & 0xFF) << 8) + (msg[2] & 0xFF);
			
			/*If files were received in sequence, continue*/
			if ( expectedSNum == currSNum) {
				
				/*Setting the expected sequence number to the next one*/
				expectedSNum++;
				
				/*Extracting the end of file flag and assigning equivalent
				 * to cont. Technique adapted from the stackoverflow link above.*/
				if ((msg[0] & 0xFF) == ENDOFFILE) {
					
					cont = false;
					clipped_message = new byte[length - HEADER];
					
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
	
				/*Finally, write the message to the file*/
				try {
					
					fos.write(clipped_message);
					
					byte[] acknowledge = new byte[3];
					acknowledge[0] = (byte) ACKN;
					acknowledge[1] = (byte) (currSNum >> 8);
					acknowledge[2] = (byte) (currSNum);
					
					DatagramPacket ackP = new DatagramPacket(acknowledge, acknowledge.length, this.address, this.ackPort);
					this.ds_send.send(ackP);
					
				} catch (Exception e) {
					
					System.out.println("Failed to write message to file. Quitting...");
					e.printStackTrace();
					System.exit(1);
				}
			} else {
				/*Resending previous acknowledgement if the insequential packet is received, fulfilling the design requirements*/

				System.out.println("Insequential packet number " + (currSNum) + " received. Expected " + expectedSNum + " Resending previous acknowledgement for packet number " + (expectedSNum-1));
				
				byte[] acknowledge = new byte[3];
				acknowledge[0] = (byte) ACKN;
				acknowledge[1] = (byte) (expectedSNum-1 >> 8);
				acknowledge[2] = (byte) (expectedSNum-1);
				
				DatagramPacket ackP = new DatagramPacket(acknowledge, acknowledge.length, this.address, this.ackPort);
				
				try {
					
					this.ds_send.send(ackP);
					
				} catch (IOException e) {
					
					System.out.println("Failed to send acknowledgement. Quitting...");
					e.printStackTrace();
					System.exit(1);
				
				}
			}
		} while (cont);
		
			
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

		if(args.length != 2){

			System.out.println("Invalid number of parameters. Quitting...");
			System.exit(1);
			
		}
		else {
			try {
				
				Receiver2a receiver = new Receiver2a(args[0], args[1]);
				receiver.createReceivedFile();
				receiver.receiveFile();
				System.out.println("File received.");
				
			}
			catch (Exception e) {
			
				System.out.println("Exception occured. Quitting...");
				e.printStackTrace();
				System.exit(1);
			
			}
		}
		/*Receiver2a receiver = new Receiver2a("54321", "src/Rtest.txt");
		receiver.createReceivedFile();
		receiver.receiveFile();
		System.out.println("File received.");*/
	}
}
