/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary*/

import java.io.*;
import java.net.*;

public class Sender1a {
	
	/*Instance variables, populated when initialised*/
	private DatagramSocket ds;
	private byte[] file_array;
	private String filename = "";
	private String address = "";
	private String port = "";
	
	
	/*Static variables*/
	private static final int HEADER = 3; 
	private static final int LOAD = 1024;
	
	/*Constructor of sender instance*/
	public Sender1a(String iAddress, String iPort, String iFilename) throws SocketException {
		
		//Storing parameter values
		this.filename = iFilename;
		this.address = iAddress;
		this.port = iPort;
		
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
		
		if(file_array.length <= 0 ){
			
			System.out.println("File read is empty. Quitting...");
			System.exit(1);
			
		} else try {
			/*sequence number*/
			int sNum = 0;
			
			/*Messages to be sent will be iterated over, in steps of the
			 * payload size*/
			for (int i = 0; i < file_array.length; i += LOAD) {
				
				/*Message to be sent is a byte array.
				 * The sequence number is at position 0.
				 * The size depends on whether the packet is
				 * the last packet of the file or not.*/		
				byte[] msg = null;
			
				int last_packet = i + LOAD;
				
				/*If the last packet is beyond the 
				 * file byte array length, then the end of 
				 * file has been reached. */
				
				if ( last_packet  >= file_array.length) {
					
					/*If only one packet, seq. number is 0 - prevent
					 * division by 0 with the following if statement.*/
					if (sNum > 0 ) {
						msg = new byte[ (file_array.length % sNum) + HEADER];
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
				
				/*Incrementing sequence number*/
				sNum++;
				
				/*Thread is being set to sleep as instructed to prevent packet loss*/
				Thread.sleep(15);
	
			}
			
			
			
		} catch (Exception e) {
			
			System.out.println("Failed to send file. Quitting...");
			e.printStackTrace();
			System.exit(1);
			
		} finally {
			
			try {
				/*closing datagramsocket*/
				this.ds.close();
				
			} catch (Exception e) {
			
				System.out.println("Failed to close data socket. Quitting...");
				e.printStackTrace();
				System.exit(1);
				
			}
		}
	}
	
	/*Main method to run sender*/
	public static void main(String[] args) throws Exception {
		
		if(args.length != 3){

			System.out.println("Invalid number of parameters. Quitting...");
			System.exit(1);
			
		}
		else {
			try {
				
				Sender1a sender = new Sender1a(args[0], args[1], args[2]);
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
