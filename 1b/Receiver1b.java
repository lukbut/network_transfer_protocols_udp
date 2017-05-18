/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary*/

import java.io.*;
import java.net.*;

public class Receiver1b {
	
	/*Static variables*/
	private static final int HEADER = 3; 
	private static final int LOAD = 1024;
	
	/*Instance variables*/
	private String address;
	private String port = "";
	private String file_name = "";
	private DatagramSocket ds;
	private FileOutputStream fos = null;
	
	/*Constructor of receiver instance*/
	public Receiver1b(String iPort, String iFilename) throws Exception  {
		
		this.address = "localhost";
		this.port = iPort;
		this.file_name = iFilename;
		
		//Initialising datagram socket to handle received packages
		this.ds = new DatagramSocket(Integer.parseInt(this.port), InetAddress.getByName(this.address));
		
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
	
	public void receiveFile() {
		
		/*16bit message sequence number*/
		int currSNum = -1;
		int expectedSNum = 0;
		InetAddress ackAddress = null;
		int ackPort = 0;
		
		/*Continue indicator for while loop. If false, end
		 * of file has been reached*/
		boolean cont = true;
		
		while (cont) {
			
			/*Message to be received is a byte array.
			 * A separate byte array is being kept for the message
			 * without the header.
			 *  */
			byte[] msg = new byte[HEADER + LOAD];
			byte[] clipped_message = null;
			
			/*Receiving the packet*/
			DatagramPacket receivedP = new DatagramPacket(msg, msg.length);
			try {
				
				this.ds.receive(receivedP);
				
			} catch (IOException e) {
				
				System.out.println("Receiving file failed. Quitting...");
				e.printStackTrace();
				System.exit(1);
				
			}
			
			/*Getting the data from the received packet*/
			msg = receivedP.getData();
			int length = receivedP.getLength();
			
			/*Getting address information from the received packet*/
			ackPort = receivedP.getPort();
			ackAddress = receivedP.getAddress();
			
			/*Parsing the message byte array contents.
			 * Technique adapted from the stackoverflow link above.*/
			currSNum = ((msg[0] & 0xFF) << 8) + (msg[1] & 0xFF);
			
			/*If files were received in sequence, continue*/
			if ( expectedSNum == currSNum) {
				
				/*Setting the expected sequence number to the next one*/
				if( expectedSNum == 0 ) {
					
					expectedSNum = 1;
					
				} else if ( expectedSNum == 1 ) {
					
					expectedSNum = 0;
					
				}
				
				/*Extracting the end of file flag and assigning equivalent
				 * to cont. Technique adapted from the stackoverflow link above.*/
				if ((msg[2] & 0xFF)== 1) {
					
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
					
					byte[] acknowledge = new byte[2];
					acknowledge[0] = (byte) (currSNum >> 8);
					acknowledge[1] = (byte) (currSNum);
					
					DatagramPacket ackP = new DatagramPacket(acknowledge, acknowledge.length, ackAddress, ackPort);
					this.ds.send(ackP);
					
				} catch (Exception e) {
					
					System.out.println("Failed to write message to file. Quitting...");
					e.printStackTrace();
					System.exit(1);
				}
			} else {
				/*Resending previous acknowledgement*/

				System.out.println("Insequential packet number " + (currSNum) + " received. Resending previous acknowledgement...");
				
				byte[] acknowledge = new byte[2];
				acknowledge[0] = (byte) (currSNum >> 8);
				acknowledge[1] = (byte) (currSNum);
				
				DatagramPacket ackP = new DatagramPacket(acknowledge, acknowledge.length, ackAddress, ackPort);
				
				try {
					
					this.ds.send(ackP);
					
				} catch (IOException e) {
					
					System.out.println("Failed to send acknowledgement. Quitting...");
					e.printStackTrace();
					System.exit(1);
				
				}
			}
		}
		
			
		if ( fos != null ) {	
			try {
			
				/*Closing fileoutputstream and socket*/
				ds.close();
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
				
				Receiver1b receiver = new Receiver1b(args[0], args[1]);
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
	}
}
