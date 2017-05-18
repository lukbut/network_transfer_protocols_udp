/*Luke Paul Buttigieg 1651066*/

import java.net.*;
import java.util.TimerTask;


public class pcktTimerTask extends TimerTask {
	
	private DatagramPacket pckt;
	private DatagramSocket sckt;
	
	//Method is analogous to run() in the thread class
	@Override
	public void run() {
		
		try {
			
			this.sckt.send(this.pckt);
			
		} catch (Exception ex) {
			
			System.out.println("Sending packet has failed. Quitting...");
			System.exit(1);
		}
		
	}
	
	//Constructor to populate local variables 
	pcktTimerTask(DatagramPacket iPckt, DatagramSocket iSckt){
		
		this.pckt = iPckt;
		this.sckt = iSckt;
		
	}

}
