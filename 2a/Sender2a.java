/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary
 * http://javarevisited.blogspot.co.uk/2012/04/how-to-measure-elapsed-execution-time.html
 * https://en.wikipedia.org/wiki/Go-Back-N_ARQ*/

import java.io.*;
import java.net.*;

public class Sender2a {
	
	/*Instance variables, populated when initialised*/
	private DatagramSocket ds_send;
	private DatagramSocket ds_receive;
	private byte[] file_array;
	private String filename = "";
	private InetAddress address;
	private int port = 0;
	private int ackPort = 0;
	private int timeout_period = 0;
	private long file_length = 0;
	private int window_size = 0;
	private int lastSNum = 0;
	
	/*Static variables*/
	private static final int HEADER = 3; 
	private static final int LOAD = 1024;

	private static final int ACKOFFSET = 1;
	private static final int ENDOFFILE = 255;
	private static final int ACKN = 1;
	
	/*Constructor of sender instance*/
	public Sender2a(String iAddress, String iPort, String iFilename, String iTimeout, String iWindowSize) throws Exception {
		
		//Storing parameter values
		this.filename = iFilename;
		this.address = InetAddress.getByName(iAddress);
		this.port = Integer.parseInt(iPort);
		this.ackPort = this.port + ACKOFFSET;
		this.timeout_period = Integer.parseInt(iTimeout);
		this.window_size = Integer.parseInt(iWindowSize);
		
		//Initialising datagram socket to handle sending of packages
		this.ds_send = new DatagramSocket();
		
		//Initialising datagram socket to handle receiving of acknowledgement packages
		this.ds_receive = new DatagramSocket(this.ackPort);
		
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
	
	/*Function defining the acknowledgement packet receipt process*/
	private int receiveAcknowledgement(int base) throws Exception {
		
		/*Initially, the SNum expected is that which is passed as a parameter.*/
		int ackSNum = base;
		
		/*The acknowledgement array and packet are constructed, which are to be populated if 
		 *an acknowledgement packet is received.*/
		byte[] acknowledge = new byte[3];
		DatagramPacket acknowledgePacket = new DatagramPacket(acknowledge, acknowledge.length);
			
			/*Setting timeoutperiod of socket*/
			this.ds_receive.setSoTimeout(this.timeout_period);
			
			/*Expecting acknowledgement packet receipt*/
			this.ds_receive.receive(acknowledgePacket);
			
			/*The value at index 0 is equal to ACKN if an acknowledgement 
			 *package is received*/
			if((int)(acknowledge[0] & 0xFF) == ACKN) 
			{
				/*If ack received, sequence number is parsed*/
				ackSNum = ((acknowledge[1] & 0xFF) << 8) + (acknowledge[2] & 0xFF);
			}
			
			/*If no packet is received, the ackSNum is equal to the base. Therefore,
			 * the second condition holds. In this case, the method is called 
			 * again to wait for the same acknowledgement, until the top, first request times out. 
			 * If the ackSNum is parsed, which would happened if an acknowledgement packet 
			 * is received, and the ackSNum is larger than the base, therefore it is of a later
			 * sNum, the new number is returned. This means the base has moved forward by one*/
			if (base < ackSNum) {
				
				return ackSNum;
				
			} else {
				
				return this.receiveAcknowledgement(base);
				
			}
	}
	
	/*Sending the file through the network*/
	public void rdt_send() {
		
		/*Counters for performance reporting*/
		int total_retransmissions = 0;
		long startTime = System.nanoTime();; //starting the timer
		
		if(file_array.length <= 0 ){
			
			System.out.println("File read is empty. Quitting...");
			System.exit(1);
			
		} else try {
			
			/*Commencing transfer*/
			boolean transferComplete = false; 
			int currsNum = 0; //The first sNum is 0, which is the SNum whose acknowledgement packet is expected
			int baseSNum = -1; /*The base is initially -1, as the acknowledgement of the first packet 
								(0) would not have been received yet*/
			
			/*Calculating the number of the last SNum, which will be used to regulate the 
			 * sending of packets*/
			this.lastSNum = (int)Math.ceil((double) this.file_array.length/(double)LOAD)-1;
			
			/*Continuous sending and re-sending of packages, and waiting for acknowledgements*/
			while(!transferComplete){
				
				/*Sending window of packets while the current sNum is less than the last one (otherwise
				 *it would have exceeded the sNum range which can actually be sent. Sending is also repeated
				 *until the end of the window size for each iteration of the while loop, fulfilling the requirement
				 *to keep sending packets successively in chunks of the window size, whenever they are sent.*/
				while ((currsNum <= this.lastSNum ) && (( currsNum - baseSNum ) <= window_size)) {
					
					boolean endOfFile = false;
					
					if (currsNum == this.lastSNum) {
						endOfFile = true;
					}
					
					//Declaring the msg variable which will contain the contents of the packet to be sent
					byte[] msg = null;
					
					/*If the last packet is being sent, the size might have to be dynamic to the file being sent.
					 * The case of a sent file which is less than one packet long is also catered for.*/
					if (endOfFile) {
						if(currsNum > 0) {
							
							msg = new byte[(file_array.length%currsNum) + HEADER];
								
						} else {
							
							msg = new byte[file_array.length + HEADER];

						}	
						
						//Setting the element at the first index as the end of file flag
						msg[0] = (byte) ENDOFFILE;
						
						/*Iterating through the parsed file contents*/
						for (int j = 0; j < file_array.length - currsNum*LOAD; j++) {
							
							msg[j + 3] = file_array[j + currsNum*LOAD];
							
						}
						
					} else {
						
						msg = new byte[LOAD + HEADER];
						
						//Indicating that the packet is not the last one
						msg[0] = (byte) 0;

						/*Iterating through the parsed file contents*/
						for (int j = 0; j < LOAD; j++) {
							
							msg[j + 3] = file_array[j + currsNum*LOAD];
							
						}
					}
					
					/*Assigning the sNum to the appropriate elements*/
					msg[1] = (byte)(currsNum >> 8);
					msg[2] = (byte)(currsNum);
					
					/*Sending the packet*/
					DatagramPacket pckt = new DatagramPacket(msg, msg.length, this.address, this.port);
					this.ds_send.send(pckt);
					
					/*Incrementing the sNum counter since the package for it would have been sent*/
					currsNum++;
					
				}
				
				//Checking for acknowledgements by calling the appropriate function
				try {
					/*The return value is assigned to the baseSNum variable, which has the implicit
					 * effect of updating it when it is successfully received. Should the acknowledgement
					 * not be received and the operation times out, the base number remains what it was.*/
					baseSNum = this.receiveAcknowledgement(baseSNum);
					
				} catch (SocketTimeoutException ex) {
					
					/*If receiveAcknowledgement timed out, base will have the same value. Therefore, sNum will have to be
					 * reset to this number again.*/
					currsNum = baseSNum+1;
					System.out.println("Acknowledgement receiving operation for sNum " + currsNum + " timed out. Resending...");
					total_retransmissions++;
					
				}
				
				//Upon reaching the last sequence number expected, the transfer is ready
				if (baseSNum == this.lastSNum){
					
					transferComplete = true;
					
				}
			}
			
		} catch (Exception e) {
			
			System.out.println("Failed to send file. Quitting...");
			e.printStackTrace();
			System.exit(1);
			
		} finally {
			
			//Calculating elapsed time
			long elapsedTime = System.nanoTime() - startTime; //Ending the timer
			
			try {
				/*closing datagramsockets*/
				this.ds_send.close();
				this.ds_receive.close();
				
			} catch (Exception e) {
			
				System.out.println("Failed to close data socket. Quitting...");
				e.printStackTrace();
				System.exit(1);
				
			}
			
			//Printing statistics
			System.out.println("Number of re-transmissions is: " + total_retransmissions);
			System.out.println("Elapsed time is: " + (elapsedTime/1000000000.0));
			System.out.println("Throughput rate is " + ((file_length/1024)/(elapsedTime/1000000000.0)));
			
		}
	}
	
	/*Main method to run sender*/
	public static void main(String[] args) throws Exception {
		
		if(args.length != 5){

			System.out.println("Invalid number of parameters. Quitting...");
			System.exit(1);
			
		}
		else {
			try {
				//Parsing parameters and running methods
				Sender2a sender = new Sender2a(args[0], args[1], args[2], args[3], args[4]);
				sender.readFile();
				sender.rdt_send();
				System.out.println("File sent");
				
			}
			catch (Exception e) {
			
				System.out.println("Exception occured. Quitting...");
				e.printStackTrace();
				System.exit(1);
			
			}
		}
		
		/*Sender2a sender = new Sender2a("localhost", "54321", "src/test.txt",  "1000000", "5");
		sender.readFile();
		sender.rdt_send();
		System.out.println("File sent");*/
		
	}
}
