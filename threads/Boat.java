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
		
		
		inital = true;		// inital state: The simulatio has just started no one has lef 
							// island Oahu


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
		/** If the inital state aka the start of the 
		 *  simulation, no adult is allowed to do anything
		 *  untill there is at least one kid on each island
		 **/
		while(inital)
		{
			KThread.yield(); // an adult thread must yiled to eliminate busy waiting 
		}
		while(!(t_adults == adults_at_molokai && t_kids == kids_at_molokai))
		{
		
			if(boat == Island.Oahu){
				// check if there is at least 1 kido on molokai
				// since we know the total num of kids from the start
				// if these dont match we know there least 1 kido on molokai
				if(kids_at_oahu != t_kids /*&& kids_at_molokai != t_kids*/){
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
				}
			}
			KThread.yield(); // no adults could be moved 
		}
		// if the loop terminates then we know that everyone has moved to molokai
		// we can now signal the communicator ... the sum of this should end the loop 
		comlink.speak(t_indiv);
	}
	static void ChildItinerary()
	{
		/** Move children around to have at least one kid on 
		 *  each island if we are in initial state
		 **/
		if(inital)
		{
			myBoat.acquire();
			bg.ChildRowToMolokai();
			bg.ChildRideToMolokai();

			kids_at_molokai +=2 ; 
			kids_at_oahu -= 2;
			boat = Island.Molokai;

			bg.ChildRowToOahu();
			kids_at_oahu +=1;
			kids_at_molokai -= 1;
			boat = Island.Oahu;

			inital = false;
			myBoat.release();
		}
		else { 
		while(!(t_adults == adults_at_molokai && t_kids == kids_at_molokai))
		{
			// if all the aults have reached island molokai
			// get all kids to molokai
			if(adults_at_molokai == t_adults)
			{
				
				// cehck where boat is 
				if(boat == Island.Oahu)
				{
					myBoat.acquire();
					// there is exactly one kid on island
					// with boat so send him to molokai
					if(kids_at_oahu == 1)
					{
						bg.ChildRowToMolokai();
						kids_at_oahu -= 1;
						kids_at_molokai += 1;
						boat = Island.Molokai;
						comlink.speak(t_indiv); // at this stage everything is complete
						myBoat.release();
						break;

					}
					// we know we can send at least 2 at a time 
					else if(kids_at_oahu >1)
					{
						while(kids_at_oahu >0)
						{
							if(kids_at_oahu >1)
							{

								bg.ChildRowToMolokai();
								bg.ChildRideToMolokai();
								kids_at_oahu -= 2;
								kids_at_molokai += 2;
								// now return to get more
								bg.ChildRowToOahu();
								kids_at_molokai -= 1;
								kids_at_oahu += 1;
								// boat = Island.Oahu;
							}
							else if(kids_at_oahu == 1)
							{
								bg.ChildRowToMolokai();
								kids_at_oahu -= 1;
								kids_at_molokai += 1;
								boat = Island.Molokai;
							}
							
						}
						comlink.speak(t_indiv); // at this stage everything is complete
						break;
					}
					else if(kids_at_oahu == 1)
					{
						bg.ChildRowToMolokai();
						kids_at_oahu -= 1;
						kids_at_molokai += 1;
						boat = Island.Molokai;
						comlink.speak(t_indiv); // at this stage everything is complete
						break;
					}
					myBoat.release();
				}
				else if(boat == Island.Molokai)
				{
					if(kids_at_molokai != t_kids)
					{
						// there is one child left on island 
						if(t_kids -kids_at_molokai == 1)
						{
							myBoat.acquire();

							bg.ChildRowToOahu();
							kids_at_molokai -= 1;
							kids_at_oahu += 1;
							boat = Island.Oahu;
							
							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();
							kids_at_molokai += 2;
							kids_at_oahu -= 2;

							boat = Island.Molokai;
							myBoat.release();
							comlink.speak(t_indiv);
							break;
						}
						else 
						{
							myBoat.acquire();

							bg.ChildRowToOahu();
							kids_at_molokai -= 1;
							kids_at_oahu += 1;
							boat = Island.Oahu;
							
							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();
							kids_at_molokai += 2;
							kids_at_oahu -= 2;

							boat = Island.Molokai;
							myBoat.release();
						}	
					}
				}
			}
			if(adults_at_molokai != t_adults)
			{
				myBoat.acquire();
				// send boat back 
				if(boat == Island.Molokai)
				{
					bg.ChildRowToOahu();
					kids_at_molokai -= 1;
					kids_at_oahu += 1;
					boat = Island.Oahu;
				}
				myBoat.release();
			}
			// System.out.print("We stuck here???");
			KThread.yield();
		}
		comlink.speak(t_indiv);
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
	protected static boolean inital;
	protected static int adults_at_oahu; 
	protected static int kids_at_oahu;

	protected  static int adults_at_molokai;
	protected static int kids_at_molokai;

	static Communicator comlink; //communicates threads

	static Lock myBoat; // lock for using boat
	static Island boat;	// location of boat

}

