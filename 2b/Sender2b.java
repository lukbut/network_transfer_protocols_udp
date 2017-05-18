/*The following material was used for inspiration 
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/datagrams/clientServer.html
 * http://stackoverflow.com/questions/1735840/how-do-i-split-an-integer-into-2-byte-binary
 * http://javarevisited.blogspot.co.uk/2012/04/how-to-measure-elapsed-execution-time.html
 * https://en.wikipedia.org/wiki/Go-Back-N_ARQ*/

import java.io.*;
import java.net.*;
import java.util.*;

public class Sender2b {
	
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
	private Timer tmr;
	private HashMap<Integer,TimerTask> tmrList;
	
	/*Static variables*/
	private static final int HEADER = 3; 
	private static final int LOAD = 1024;

	private static final int ACKOFFSET = 1;
	private static final int ENDOFFILE = 255;
	private static final int ACKN = 1;
	
	/*Constructor of sender instance*/
	public Sender2b(String iAddress, String iPort, String iFilename, String iTimeout, String iWindowSize) throws Exception {
		
		//Storing parameter values
		this.filename = iFilename;
		this.address = InetAddress.getByName(iAddress);
		this.port = Integer.parseInt(iPort);
		this.ackPort = this.port + ACKOFFSET;
		this.timeout_period = Integer.parseInt(iTimeout);
		this.window_size = Integer.parseInt(iWindowSize);
		this.tmr = new Timer();
		this.tmrList = new HashMap<Integer, TimerTask>();
		
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
	private void receiveAcknowledgement() throws Exception {
		
		/*The acknowledgement array and packet are constructed, which are to be populated if 
		 *an acknowledgement packet is received.*/
		byte[] acknowledge = new byte[3];
		DatagramPacket acknowledgePacket = new DatagramPacket(acknowledge, acknowledge.length);
		
			/*This time round the receiver socket does not wait for an exception as it
			 * does not have to, as it is constantly receiving different packets.*/
			
			/*Expecting acknowledgement packet receipt*/
			this.ds_receive.receive(acknowledgePacket);
			
			int ackSNum = ((acknowledge[1] & 0xFF) << 8) + (acknowledge[2] & 0xFF);
			
			/*The value at index 0 is equal to ACKN if an acknowledgement 
			 *package is received. If this is true, and the SNum of the received
			 *acknowledgement packet is in the timer list, the timer is cancelled and removed*/
			if((int)(acknowledge[0] & 0xFF) == ACKN && (this.tmrList.get(ackSNum) != null)) 
			{

				this.tmrList.get(ackSNum).cancel();
				this.tmrList.remove(ackSNum);
				
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
			int currsNum = 0; //The first sNum is 0, which is the SNum whose acknowledgement packet is expected
			
			/*Calculating the number of the last SNum, which will be used to regulate the 
			 * sending of packets*/
			this.lastSNum = (int)Math.ceil((double) this.file_array.length/(double)LOAD)-1;
			
			/*Continuous sending and re-sending of packages, and waiting for acknowledgements*/
			for(;;){
					//Checking whether the end of line has been reached
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
					
					/*Using an implemented TimerTask class extension, pcktTimerTask to implement multi-threading
					 * and send the packets instead of sending the packets directly at this point*/
					pcktTimerTask pTT = new pcktTimerTask(pckt, this.ds_send);
				
					//Starting the timer which will be used to schedule the sending of the pTT 
					this.tmr.scheduleAtFixedRate(pTT, 0, this.timeout_period);
					
					//Adding the timer to the list of timers, to keep track of the current ones
					this.tmrList.put(currsNum, pTT);
					
				
				/*If the sNum being processed is past the size of the window, or past the final SNum, the acknowledgement is expected*/
				if((currsNum >= this.window_size) || currsNum >= lastSNum){

					/*The code is repeated as long as the SNum being processed is less than the last SNum, and the timer list
					 * is not empty.*/
					while(currsNum < lastSNum || !this.tmrList.isEmpty()){
						
						//Receiving acknowledgements and removing entries in timer list if appropriate
						this.receiveAcknowledgement();
						
						//If not all packets in the window size have been sent, they're sent now
						if(currsNum < lastSNum && this.tmrList.size() < this.window_size) {
							
							/*Incrementing the sNum counter since the package for it would have been sent*/		
							currsNum++;
									
							boolean endOfFile2 = false;
									
								if (currsNum == this.lastSNum) {
										
									endOfFile2 = true;
								
								}
									
								//Declaring the msg variable which will contain the contents of the packet to be sent
								byte[] msg2 = null;
									
								/*If the last packet is being sent, the size might have to be dynamic to the file being sent.
								* The case of a sent file which is less than one packet long is also catered for.*/
								if (endOfFile2) {
									if(currsNum > 0) {
											
										msg2 = new byte[(file_array.length%currsNum) + HEADER];
												
									} else {
											
										msg2 = new byte[file_array.length + HEADER];

									}	
										
									//Setting the element at the first index as the end of file flag
									msg2[0] = (byte) ENDOFFILE;
										
									/*Iterating through the parsed file contents*/
									for (int j = 0; j < file_array.length - currsNum*LOAD; j++) {
											
										msg2[j + 3] = file_array[j + currsNum*LOAD];
										
									}
			
								} else {
										
									msg2 = new byte[LOAD + HEADER];
										
									//Indicating that the packet is not the last one
									msg2[0] = (byte) 0;

									/*Iterating through the parsed file contents*/
									for (int j = 0; j < LOAD; j++) {
											
										msg2[j + 3] = file_array[j + currsNum*LOAD];
										
									}
								}
									
								/*Assigning the sNum to the appropriate elements*/
								msg2[1] = (byte)(currsNum >> 8);
								msg2[2] = (byte)(currsNum);
								
								System.out.println("Sending packet number: " + currsNum);
								
								/*Sending the packet*/
								DatagramPacket pckt2 = new DatagramPacket(msg2, msg2.length, this.address, this.port);
									
								/*Using an implemented TimerTask class extension, pcktTimerTask to implement multi-threading
								 * and send the packets*/
								pcktTimerTask pTT2 = new pcktTimerTask(pckt2, this.ds_send);
								
								//Starting the timer which will be used to schedule the sending of the pTT 
								this.tmr.scheduleAtFixedRate(pTT2, 0, this.timeout_period);
									
								//Adding the timer to the list of timers, to keep track of the current ones
								this.tmrList.put(currsNum, pTT2);
									
						}
					}
					//Processing it over, therefore it can terminate.
					break;
				} else {
				
				/*Incrementing the sNum counter since the package for it would have been sent*/
				currsNum++;
				
				}
			}
		} catch (SocketTimeoutException ex)
		{
			System.out.println("Experienced SocketTimeoutException waiting for packet");
			ex.printStackTrace();
			System.exit(1);
			
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
			
			System.exit(0);
			
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
				Sender2b sender = new Sender2b(args[0], args[1], args[2], args[3], args[4]);
				sender.readFile();
				sender.rdt_send();
				System.out.println("File sent");
				System.exit(0);
				
			}
			catch (Exception e) {
			
				System.out.println("Exception occured. Quitting...");
				e.printStackTrace();
				System.exit(1);
			
			}
		}
		
		/*Sender2b sender = new Sender2b("localhost", "54321", "src/test.jpg",  "10000000", "5");
		sender.readFile();
		sender.rdt_send();
		System.out.println("File sent");*/
		
	}
}
