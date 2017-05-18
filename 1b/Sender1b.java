/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary
 * http://javarevisited.blogspot.co.uk/2012/04/how-to-measure-elapsed-execution-time.html*/

import java.io.*;
import java.net.*;

public class Sender1b {
	
	/*Instance variables, populated when initialised*/
	private DatagramSocket ds;
	private byte[] file_array;
	private String filename = "";
	private String address = "";
	private String port = "";
	private int timeout_period = 0;
	private long file_length = 0;
	
	/*Static variables*/
	private static final int HEADER = 3; 
	private static final int LOAD = 1024;
	
	/*Constructor of sender instance*/
	public Sender1b(String iAddress, String iPort, String iFilename, String iTimeout) throws SocketException {
		
		//Storing parameter values
		this.filename = iFilename;
		this.address = iAddress;
		this.port = iPort;
		this.timeout_period = Integer.parseInt(iTimeout);
		
		//Initialising datagram socket to handle sending of packages
		this.ds = new DatagramSocket();
		
	}
	
	/*Reading file from disk into a stream of bytes*/
	private void readFile() {
		
		File f = new File(filename);
		FileInputStream fis = null;
		
		try {

			fis = new FileInputStream(f);
	        file_array = new byte[(int) f.length()];
	        
	        fis.read(file_array);
	        file_length = f.length();

		} catch (IOException e) {
			
			System.out.println("Failed to read file. Quitting...");
			e.printStackTrace();

		} finally {

			try {
				/*closing  fileinputstream*/
				if (fis != null) {
					
					fis.close();
				
				}	
				
			} catch (IOException ex) {
				
				System.out.println("Failed to close fileinputstream. Quitting...");
				ex.printStackTrace();
				
			}

		}
	}
	
	/*Sending the file through the network*/
	public void sendFile() {
		int resentCount = 0;
		
		long startTime = 0;
		boolean timing = false;
		
		if(file_array.length <= 0 ){
			
			System.out.println("File read is empty. Quitting...");
			System.exit(1);
			
		} else try {
			
			int sNum = 0;
			int currentAckSNum = 0;
			int packCount = 0;
			
			/*Messages to be sent will be iterated over, in steps of the
			 * payload size*/
			for (int i = 0; i < file_array.length; i += LOAD) {
				
				/*Message to be sent is a byte array.
				 * The sequence number is at position 0.
				 * The size depends on whether the packet is
				 * the last packet of the file or not.*/		
				byte[] msg = null;
				boolean correctlyReceivedAck = false;
			
				int last_packet = i + LOAD;
				
				/*If the last packet is beyond the 
				 * file byte array length, then the end of 
				 * file has been reached. */
				
				if ( last_packet  >= file_array.length) {
					
					/*If only one packet, seq. number is 0 - prevent
					 * division by 0 with the following if statement.*/
					if (packCount > 0 ) {
						
						msg = new byte[ (file_array.length % packCount) + HEADER];
						
					} else {
						
						msg = new byte[ (file_array.length) + HEADER];
						
					}
					
					/*Setting end of line flag to 1*/
					msg[2] = (byte) 1;
					
					/*Iterating through the parsed file contents*/
					for (int j = 0; j < file_array.length - i; j++) {
						
						msg[j + 3] = file_array[j + i];
						
					}
					
				} else {

					msg = new byte[LOAD + HEADER];
					
					/*Setting end of line flag to 0*/
					msg[2] = (byte) 0;
					
					/*Iterating through the parsed file contents*/
					for (int j = 0; j < LOAD; j++) {
						
						msg[j + 3] = file_array[j + i];
						
					}
				}
				
				/*Splitting sequence number into 8 bit parts*/
				msg[0] = (byte) (sNum >> 8);
				msg[1] = (byte) (sNum);
				
				/*Sending packet while parsing address and port*/
				DatagramPacket pckt = new DatagramPacket(msg, msg.length, InetAddress.getByName(address), Integer.parseInt(port));
				this.ds.send(pckt);
				
				//Starting timer if previously not started
				if(!timing) {
					
					startTime = System.nanoTime();
					timing = true;
					
				}
				
				/*Send and Wait protocol acknowledgement handling*/
				while (!correctlyReceivedAck) {
					
					byte[] acknowledge = new byte[2];
					DatagramPacket acknowledgePacket = new DatagramPacket(acknowledge, acknowledge.length);
					
					try {
						
						/*Setting timeoutperiod of socket*/
						this.ds.setSoTimeout(this.timeout_period);
						
						/*Expecting acknowledgement packet receipt*/
						this.ds.receive(acknowledgePacket);
						
						/*If received, sequence number is parsed*/
						currentAckSNum = ((acknowledge[0] & 0xFF) << 8) + (acknowledge[1] & 0xFF);
						
						if (currentAckSNum == sNum) {
							
							/*Setting correctly received flag to true*/
							correctlyReceivedAck = true;
							
						}
						
					} catch (SocketTimeoutException ex) {
						
						/*Packet could have been lost and will be resent*/
						resentCount++;
						this.ds.send(pckt);
						System.out.println("Packet number " + sNum + " has to be resent. Resending...");
						
					} 
				}
				
				/*Setting to next sequence number*/
				if (sNum == 0) {
					
					sNum = 1;
					
				} else if (sNum == 1) {
					
					sNum = 0;
					
				}
				
				/*Incrementing packet count*/
				packCount++;
	
			}
			
		} catch (Exception e) {
			
			System.out.println("Failed to send file. Quitting...");
			e.printStackTrace();
			System.exit(1);
			
		} finally {
			
			//Calculating elapsed time
			long elapsedTime = System.nanoTime() - startTime;
			
			try {
				/*closing datagramsocket*/
				this.ds.close();
				
			} catch (Exception e) {
			
				System.out.println("Failed to close data socket. Quitting...");
				e.printStackTrace();
				System.exit(1);
				
			}
			
			//Printing statistics
			System.out.println("Number of re-transmissions is: " + resentCount);
			System.out.println("Elapsed time is: " + (elapsedTime/1000000000.0));
			System.out.println("Throughput rate is " + ((file_length/1024)/(elapsedTime/1000000000.0)));
			
		}
	}
	
	/*Main method to run sender*/
	public static void main(String[] args) throws Exception {
		
		if(args.length != 4){

			System.out.println("Invalid number of parameters. Quitting...");
			System.exit(1);
			
		}
		else {
			try {
				//Parsing parameters and running methods
				Sender1b sender = new Sender1b(args[0], args[1], args[2], args[3]);
				sender.readFile();
				sender.sendFile();
				System.out.println("File sent");
				
			}
			catch (Exception e) {
			
				System.out.println("Exception occured. Quitting...");
				e.printStackTrace();
				System.exit(1);
			
			}
		}
	}
}
