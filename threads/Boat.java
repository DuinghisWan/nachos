package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	enum Island {		// Locatoion of boat
		Oahu,Molokai	// molokai or oahu
	}; 

	static BoatGrader bg;

	public static void selfTest()
	{
		// This initializes the BoatGrader that we need to make calls to
		// in order to get graded
		BoatGrader b = new BoatGrader();
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		adults_at_molokai = 0;          // initially no one here
		kids_at_molokai = 0;            // initially no one here

		t_adults = adults;              // the overall # in sim
		t_kids = children;              // the overall # in sim

		t_indiv = adults + children;    // the total sum
		adults_at_oahu = adults;    
		kids_at_oahu = children;

		myBoat = new Lock();            // lock boat while in use
		boat = Island.Oahu;             // default island
		comlink = new Communicator();

		// create runnables for adults and cildren
		Runnable adultS = new Runnable()
		{
			public void run(){AdultItinerary();}
		};
		Runnable kidS = new Runnable()
		{
			public void run(){ChildItinerary();}
		};
		// create the number of threads
		for (int i = 0; i < t_adults; i++)
		{
			KThread a = new KThread(adultS);
			a.setName("adult:" + i);
			a.fork();
		}
		for (int i = 0; i < t_kids; i++)
		{
			KThread c = new KThread(kidS);
			c.setName("kid:" + i);
			c.fork();
		}
		while (comlink.listen() != (t_indiv)) {if(comlink.listen() == (t_indiv)) {break;}}
	}
	static void AdultItinerary()
	{
		// while the similation is complete 
		while(!(t_adults == adults_at_molokai && t_kids == kids_at_molokai))
		{
			// For adults, if they are on this island
			// yiled, since they cannot go back to Oahu
			if(boat == Island.Molokai){
				//KThread.yield();
				break;
				// by the thread yielding 
				// we prevent busy waiting 
			}
			else if(boat == Island.Oahu){
				// check if there is at least 1 kido on molokai
				// since we know the total num of kids from the start
				// if these dont match we know there least 1 kido on molokai
				if(kids_at_oahu != t_kids && kids_at_molokai != t_kids){
					if(adults_at_oahu >0){
						// we need to send some adults to molokai

						myBoat.acquire(); 
						bg.AdultRowToMolokai();

						adults_at_oahu = adults_at_oahu - 1;
						adults_at_molokai =  adults_at_molokai + 1;

						boat = Island.Molokai; // update the boat location

						// everytime we move an adult to molokai
						// we need to return the boat to oahu everytime
						
						bg.ChildRowToOahu();
						kids_at_molokai =  kids_at_molokai -1;
						kids_at_oahu = kids_at_oahu + 1;

						boat = Island.Oahu; // update the boat location
						myBoat.release();
					}
					else // if we  know there are no adults then break...yileding  
					{
						break;
					}
				}
			}
			KThread.yield(); // no adults could be moved 
		}
		// if the loop terminates then we know that everyone has moved to molokai
		// we can now signal the communicator ... the sum of this should end the loop 
		comlink.speak(kids_at_molokai + kids_at_molokai);
	}
	static void ChildItinerary()
	{
		if(boat == Island.Molokai)
		{
			//myBoat.acquire(); // get lock 


			// since for every adult that is sent... one child is sent back 
			// we only need to check if all adults have arrived on island
			if(adults_at_molokai == t_adults)
			{ // if true we know that there is atleast one kid on oahu
				while( kids_at_molokai != t_kids)
				{
					bg.ChildRowToOahu();
					kids_at_molokai =  kids_at_molokai -1;
					kids_at_oahu = kids_at_oahu + 1;
					boat = Island.Oahu;

					bg.ChildRowToMolokai();            // brind back the kids 
					bg.ChildRideToMolokai();            // you just sent 
					kids_at_oahu = kids_at_oahu - 2;
					kids_at_molokai = kids_at_molokai + 2;
					boat = Island.Molokai;

				}
				comlink.speak(t_adults +t_kids);
				
			}
			if(kids_at_molokai != t_kids)
			{   
				myBoat.acquire();
				bg.ChildRowToOahu();
				kids_at_molokai =  kids_at_molokai -1;
				kids_at_oahu = kids_at_oahu + 1;
				boat = Island.Oahu;

				bg.ChildRowToMolokai();            // brind back the kids 
				bg.ChildRideToMolokai();            // you just sent 
				kids_at_oahu = kids_at_oahu - 2;
				kids_at_molokai = kids_at_molokai + 2;
				boat = Island.Molokai;
				myBoat.release();
			}
			if(kids_at_molokai != t_kids && t_adults != adults_at_molokai)
			{
				myBoat.acquire();
				bg.ChildRowToOahu();
				kids_at_molokai =  kids_at_molokai -1;
				kids_at_oahu = kids_at_oahu + 1;
				boat = Island.Oahu;
				myBoat.release();
			}
		}
		else if (boat == Island.Oahu)
		{
			// get lock want hold on to it unless nothing can be done
			if(adults_at_oahu == 0)
			{
				// we know to just move all kids 
				while (kids_at_oahu > 0) {
					if(kids_at_oahu == 1)
					{
						bg.ChildRowToMolokai();
						kids_at_oahu -= 1;
						kids_at_molokai += 1;
						boat = Island.Molokai;
					}
					else if(kids_at_oahu >=2)
					{
						bg.ChildRowToMolokai();
						bg.ChildRideToMolokai();
						kids_at_oahu -= 2;
						kids_at_molokai += 2;
						boat = Island.Molokai;
						// bring the boat back 
						bg.ChildRowToOahu();
						kids_at_molokai -= 1;
						kids_at_oahu += 1;
						boat = Island.Oahu;
					}

				}
			}
			//			if(adults_at_oahu >0)
			//			{
			//				myBoat.acquire(); 
			//				bg.AdultRowToMolokai();
			//
			//				adults_at_oahu = adults_at_oahu - 1;
			//				adults_at_molokai =  adults_at_molokai + 1;
			//
			//				boat = Island.Molokai; // update the boat location
			//
			//				// everytime we move an adult to molokai
			//				// we need to return the boat to oahu everytime
			//				if(kids_at_oahu != t_kids && kids_at_molokai != t_kids) {
			//					bg.ChildRowToOahu();
			//					kids_at_molokai =  kids_at_molokai -1;
			//					kids_at_oahu = kids_at_oahu + 1;
			//
			//					boat = Island.Oahu; // update the boat location
			//				}
			//				myBoat.release();
			//			}
			if( kids_at_oahu > 0)
			{
				myBoat.acquire();
				// send the kdis to molokai
				// if no kids has left the island 
				// force some to leave
				if(kids_at_oahu != t_kids)
				{
					bg.ChildRowToMolokai();
					bg.ChildRideToMolokai();
					kids_at_oahu -= 2;
					kids_at_molokai += 2;
					boat = Island.Molokai;
					// bring the boat back 
					bg.ChildRowToOahu();
					kids_at_molokai -= 1;
					kids_at_oahu += 1;
					boat = Island.Oahu;
				}
				if(kids_at_oahu == 1 )
				{
					bg.ChildRowToMolokai();
					kids_at_oahu -= 1;
					kids_at_molokai += 1;
					boat = Island.Molokai;
				}
				else if(kids_at_oahu >=2)
				{
					bg.ChildRowToMolokai();
					bg.ChildRideToMolokai();
					kids_at_oahu -= 2;
					kids_at_molokai += 2;
					boat = Island.Molokai;
					// bring the boat back 
					bg.ChildRowToOahu();
					kids_at_molokai -= 1;
					kids_at_oahu += 1;
					boat = Island.Oahu;
				}
				myBoat.release();

			}
		}
		else 
		{
			KThread.yield();
		}

	}
	static void SampleItinerary()
	{
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

	/** When the simulation is started it is on island oahu 
	 *  we are able to take a head count of the number 
	 *  of adults and children ... and the # total individuals
	 *
	 */
	protected static int t_adults;
	protected static int t_kids;
	protected static int t_indiv;

	protected static int adults_at_oahu; 
	protected static int kids_at_oahu;

	protected  static int adults_at_molokai;
	protected static int kids_at_molokai;

	static Communicator comlink; //communicates threads

	static Lock myBoat; // lock for using boat
	static Island boat;	// location of boat

}

