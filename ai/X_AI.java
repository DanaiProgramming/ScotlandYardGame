package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.function.Consumer;

import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Node;
import uk.ac.bris.cs.scotlandyard.ai.ManagedAI;
import uk.ac.bris.cs.scotlandyard.ai.PlayerFactory;
import uk.ac.bris.cs.scotlandyard.model.*;

import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.SECRET;

//This AI model uses an intricate scoring function which takes into account multiple parameters.
//The best move is based on 3 main factors : 1.The difficulty of guessing Mr. X's next move
// 2. The available moves of Mr. X starting from a specific node 3. The distance between Mr. X and the detectives
//The most important one is the distance between Mr. X and the detectives, which is implemented using BFS algorithm
//to traverse through the graph an an adapted version of Dijkstra's algorithm to find the shortest distance between two nodes.
//From the 3 main factors, multiple other parameters are extracted and have a big effect on the choosing of the best move.

// TODO name the AI
@ManagedAI("X_AI")
public class X_AI implements PlayerFactory {

	// TODO create a new player here
	@Override
	public Player createPlayer(Colour colour) {

		return new MyPlayer();
	}

	// MyPlayer encapsulates the AI and chooses the best move
	private static class MyPlayer implements Player, MoveVisitor {

		//Fields of MyPlayer
		private int futurePossibleLocation; 								//to keep track of the hypothetical future location
		private List<Ticket> futurePossibleUsedTickets = new ArrayList<>(); //to keep track of the hypothetical future used tickets
		private List<Integer> revealRounds = new ArrayList<>(); 			//list with indexes of reveal rounds <= for ticketStrategy()


		//Player has makeMove() -> Called when the player is required to choose a move
		//makeMove calls accept with the specific move that the player chose to play
		@Override
		public void makeMove(ScotlandYardView view, int location, Set<Move> moves,
							 Consumer<Move> callback) {
			// TODO do something interesting here; find the best move

			double score; 			//save the score for each possible move
			double maxScore = 0; 	//maximum score of all possible moves
			Move bestMove; 			//final best move

			//initial distances between Mr. X and detectives before playing any hypothetical moves
			Distance initialDistances = findDistance(view, location);

			//===========================MAKE revealRounds list=================================//
			List<Boolean> rounds = view.getRounds(); //the boolean list of hidden or reveal rounds
			int position = -1;						 //keep track of next position in list
			int i = 0;								 //keep track of index

			//create a list which contains the index of the rounds that are Reveal Rounds
			revealRounds.clear();
			for (Boolean round: rounds){

				if (round){ //if round is reveal add its index to the list
					position +=1;
					revealRounds.add(position);
					revealRounds.set(position, i);

				}
				i+=1;
			}
			//=====================OVER_MAKE revealRounds list=================================//


			//-------------------------------LOGIC TO PICK BEST MOVE-------------------------------//
			bestMove = moves.iterator().next(); //take first move as best (to have something to compare to)

			//iterate through the moves, find the score of the move and update the bestMove if current move is better than previous ones
			for (Move move : moves) {

				futurePossibleUsedTickets.clear(); //clear used tickets - prepare list for new hypothetical move
				move.visit(this); 			   //update the futurePossibleLocation and futurePossibleTickets

				score = scoringFunction(view, initialDistances);

				if (score > maxScore){
					maxScore = score;
					bestMove = move;
				}
			}
			//----------------------------OVER_LOGIC TO PICK BEST MOVE-------------------------------//

			//pick best move, calling accept
			callback.accept(bestMove);
		}


		//scoringFunction() returns a score (specific value) which corresponds to a particular move
		private double scoringFunction(ScotlandYardView view, Distance initialDistances){

			double score;					//Final Score
			double numOfDetectives = view.getPlayers().size()-1;
			double availableMoves;			//available Mr.X moves from futurePossibleLocation
			double wellConnectedLocation;   //number of Nodes connected to futurePossibleLocation
			Distance distances;				//distances between Mr. X and detectives after playing a hypothetical move
			double tickets = 0.8; 			//initial value for using plain tickets
			//tickets =>works like a threshold to differentiate when use plain tickets and when use secret or double tickets

			//-----------------------------------PARAMETER CALCULATION--------------------------------//
			//Distance parameters => bigger odds for using this move when both min and average distances are bigger
			distances = findDistance(view, futurePossibleLocation);
			double weightedAverageDistance = distances.getSumOfDistances()/(numOfDetectives*7.0);
			double weightedMinDistance;
			if (distances.getShortestDistance()==1)	weightedMinDistance = 0; //nullify when detective is 1 hop away
			else weightedMinDistance = distances.getShortestDistance()/7.0;
			//

			//Find score value for each parameter, and normalize between the range of 0-1
			//NORMALIZATION: (divide by maximum number of that the parameter can take)
			//Max available moves = 13
			//Max neighbouring nodes around a node = 13
			//Max of shortest distances = 7

			availableMoves = availableMoves(view)/13.0; //find available moves and normalize it
			if (availableMoves == 0)//if there aren't any available moves just return 0, since it's not possible to play this move at all
				return 0; 			//not possible to play this move

			wellConnectedLocation = wellConnectedLocation(view)/13.0; //find number of nodes around location and normalize it
			//--------------------------------OVER_PARAMETER CALCULATION--------------------------------//


			//In case the current possible move we check is a SECRET or DOUBLE move use ticketStrategy()
			if (futurePossibleUsedTickets.contains(SECRET) || (futurePossibleUsedTickets).size()==2){

				tickets = ticketStrategy(view, distances, initialDistances); //calculate how suitable is to use this ticket
				if (tickets == 0) { //if ticketStrategy() returns 0, assign really small value to score
					score = 0.1;  //small score bc it is possible to play the move but the least preferable
				}
				else{ //if ticketStrategy() returns normally, calculate score taking into account 3 weighted parameters

					//the tickets parameter is there to differentiate the use of specific kind of ticket (Normal, Double, Secret)
					//the other two parameters are there to differentiate specific moves of the same kind
					score = ((tickets*0.6) + (wellConnectedLocation)*0.2) + (availableMoves*0.2);
				}
			}

			//In case the current possible move we check is plain TicketMove
			else{
				//the tickets parameter is there to differentiate the use of specific kind of ticket (Normal, Double, Secret)
				//the two distance weighted parameters are there for the same reason
				//the other two parameters are there to differentiate specific moves of the same kind
				score = (tickets*0.2)+(weightedMinDistance*0.5) + (weightedAverageDistance*0.2) +((wellConnectedLocation)*0.05)+ (availableMoves*0.05);
			}

			return score;
		}


		//returns whether any of the detectives are on a specific location <= used in findDistance()
		private boolean detectiveOnLocation(ScotlandYardView view, int location){

			List<Colour> players = view.getPlayers();
			for (Colour player : players.subList(1,players.size()) ){

				if (location == view.getPlayerLocation(player).get()){
					return true;
				}
			}
			return false;
		}


		//Returns a Distance object which contains the shortest distance between Mr. X and any of the detectives,
		//and the sum of all shortest distances between Mr. X and the detectives
		//It finds the distance between Mr. X and every detective, using BFS to traverse through the graph and using
		//an adapted version of Dijkstra's algorithm to find the distance
		private Distance findDistance(ScotlandYardView view, int location_start){

			//--------------------INITIALIZATIONS--------------------------//
			List<Colour> players = view.getPlayers(); //all players
			List<Node<Integer>> allNodes;			  //all nodes in graph
			allNodes = view.getGraph().getNodes();


			List<Integer> distances = new ArrayList<>(allNodes.size());     //stores the distance of every node in the graph from the start location node
																		    //capacity the number of all nodes
			List<Boolean> visitedNodes = new ArrayList<>(allNodes.size());  //keeps track of which node from the graph was visited at least once
																			//capacity the number of all nodes
			List<Integer> queue = new ArrayList<>();					    //contains the nodes from which we have to check the neighbouring nodes of

			//set initial values to distances[] and visitedNodes[]
			for (Node<Integer> node : allNodes){

				distances.add(200);				  //all distances with arbitrary value 200
				visitedNodes.add(false); 		  //all nodes are unvisited
			}
			//---------------OVER_INITIALIZATIONS--------------------------//


			queue.add(location_start); 					//start from start_location (location of Mr. X after using a specific move)
			visitedNodes.set(location_start-1 , true);	//start_location now is visited
			distances.set(location_start-1 , 0);		//Start and finish nodes are the same, hence 0

			int current_search_node;
			int detectivesLocationsFound = 0; //number of detectives from which we have found their shortest distance to Mr. X
			int sumDistances = 0;			  //sum of shortest distances of all detectives
			int minDistance = 200;			  //minimum shortest distance between all detectives

			while (!queue.isEmpty()){ //when the queue is empty, there aren't any nodes left (that we haven't visited)

				current_search_node = queue.get(0); //take the first element in the queue as current search node
				queue.remove(0);				//and remove it

				//All edges around the current Search Node
				Collection<Edge<Integer, Transport>> edges = view.getGraph().getEdgesFrom(view.getGraph().getNode(current_search_node));

				//visit every neighbouring node of current search node
				for (Edge<Integer,Transport> edge : edges){

					if (!visitedNodes.get( edge.destination().value()-1 )){ //if neighbour node not visited

						visitedNodes.set( edge.destination().value()-1, true); //set it to visited

						//set distance of neighbour node to the distance of search node incremented by 1
						distances.set( edge.destination().value()-1,  distances.get(current_search_node-1) +1 );
						queue.add(edge.destination().value()); //add this neighbour node to queue, so as to check its own neighbour nodes in the future

						//Stop calculating distances and traversing through the graph
						//when all the detectives' shortest distances are found
						if (detectiveOnLocation(view, edge.destination().value())){ //if a detective is on this neighbour node -> his shortest distance from Mr. X is found

							sumDistances += distances.get( edge.destination().value()-1); 	//update sum of shortest distances from detectives
							if (distances.get( edge.destination().value()-1) < minDistance) //update minimum shortest distance
								minDistance = distances.get( edge.destination().value()-1);

							detectivesLocationsFound +=1;
							if (detectivesLocationsFound == players.size()-1){ //STOP and return, if all detective distances found
								return new Distance(minDistance, sumDistances);
							}
						}
					}
				}
			}
			throw new IllegalStateException("No other nodes left to search");
		}



		//with how many nodes is the future possible location connected  <= used by scoringFunction()
		private int wellConnectedLocation (ScotlandYardView view){

			Collection<Edge<Integer, Transport>> edges = view.getGraph().getEdgesFrom(view.getGraph().getNode(futurePossibleLocation));
			List<Integer> neighbourNodes = new ArrayList<>();

			//create list neighbourNodes[] with locations of neighbouring nodes
			for (Edge<Integer, Transport> edge : edges){

				int nodeLoc = edge.destination().value();
				if (!neighbourNodes.contains(nodeLoc)) //multiple edges go to the same node
					neighbourNodes.add (nodeLoc);
			}
			return neighbourNodes.size();
		}


		//number of available moves for Mr. X if he goes to future Possible Location and uses specific ticket <= used by scoringFunction()
		private double availableMoves (ScotlandYardView view){

			int possibleMoves = 0;
			int temp;

			//collection of edges around the possible future location
			Collection<Edge<Integer, Transport>> edges = view.getGraph().getEdgesFrom(view.getGraph().getNode(futurePossibleLocation));

			//check every edge to check if it's a possible move for Mr. X
			for (Edge<Integer, Transport> edge : edges){

				temp = 0;

				//check that no detective is on that destination i.e. location is free to be on
				if ( !detectiveOnLocation(view, edge.destination().value()) ){

					//NORMAL TICKET_MOVE
					//take into account the normal tickets used for the future possible move
					for (Ticket ticket: futurePossibleUsedTickets){
						if (ticket.equals( fromTransport(edge.data()) ))
							temp+=1;
					}

					//check that Mr. X has enough tickets for that specific move
					if ( view.getPlayerTickets(BLACK, fromTransport(edge.data())).get() > temp) {
						possibleMoves+=1;
					}
					//

					//SECRET TICKET_MOVE
					//if not, check if he has a secret ticket, if yes he can do that ticketMove using secret ticket
					else {

						//take into account the secret tickets used for the future possible move
						temp = 0;
						for (Ticket ticket: futurePossibleUsedTickets){
							if (ticket.equals( SECRET))
								temp+=1;
						}

						//check that Mr. X has enough secret tickets for that specific move
						if (view.getPlayerTickets(BLACK, SECRET).get() > temp){
							possibleMoves+=1;
						}
					}
					//
				}
			}
			return possibleMoves;
		}


		//Calculation of tickets parameter <= used by scoringFunction()
		//based on strategy for the use of double and secret tickets at the right moment
		//returns a value which represents the suitability of using a secret, double or both tickets respectively to the move
		private double ticketStrategy(ScotlandYardView view, Distance distances, Distance initialDistances) {

			//-----Some restrictions that give automatically really low value for suitability of secret or/and double----//
			int currentRound = view.getCurrentRound();
			//before first reveal round => NO SECRET / DOUBLE MOVE
			if (currentRound < revealRounds.get(0)) {
				//Exception if it's before the first round, but it happened that a detective is one hop away, use double move
				if (initialDistances.getShortestDistance()>1)
					return 0;
			}

			//not necessary to use a double move, if initially Mr. X is relatively far from the closest detective
			if (initialDistances.getShortestDistance() >=2 && futurePossibleUsedTickets.size() == 2){
				//don't use double move if the shortest distance after the move is really small
				if ((distances.getShortestDistance() == 1))
					return 0;
				return 0;
			}
			//-------------------------------------------------------------------------------------------------------------//


			//-------------find closer smaller reveal round from current round---------------//
			int position = -1; 				  //keep track of index in revealRounds[]
			int closerSmallerRevealRound = 0; //closest reveal round to the current round which is smaller than the current round

			for (Integer revealRound : revealRounds){
				position+=1;
				if (revealRound <= currentRound){
					closerSmallerRevealRound = revealRound;
				}
				else break;
			}
			position = position-1;
			//---------OVER_find closer smaller reveal round from current round---------------//


			//========================================SECRET TICKET=============================================//
			double secretYesValue; //value for suitability of using a SECRET move

			//TICKET_MOVE: SECRET
			//if uses a Single ticket then necessarily uses SECRET ticket in TicketMove, since this function is called inside a condition (see scoringFunction())
			if (futurePossibleUsedTickets.size()==1){

				//Suitability of Secret Move:
				// > Don't play secret in reveal round / in round before a reveal round
				if ((revealRounds.contains(currentRound)) || ( (revealRounds.get(position+1)-1)==currentRound )){
					return 0;
				}
				else{
					secretYesValue = secretYesValue(view, currentRound, closerSmallerRevealRound, position, distances);
					return secretYesValue;
				}
			}
			//======================================OVER_SECRET TICKET=============================================//


			//------------------------------------DOUBLE TICKET -------------------------------------------//
			double doubleYesValue = doubleYesValue(view, currentRound, closerSmallerRevealRound, position, distances); //suitability of DOUBLE move

			//DOUBLE: SECRET + SECRET
			if ((futurePossibleUsedTickets.get(0) == SECRET) && (futurePossibleUsedTickets.get(1)==SECRET)){

				//don't use a double with first move a secret ticket, if the round of the secret ticket is a reveal round
				//or one round before a reveal round
				if ((revealRounds.contains(currentRound)) || (revealRounds.contains(currentRound+1))){
					return 0;
				}
				else{
					//calculate secret suitability for first move
					double secretYesValue_first = secretYesValue(view, currentRound, closerSmallerRevealRound, position, distances);
					//calculate secret suitability for second move
					double secretYesValue_second = secretYesValue(view, currentRound+1, closerSmallerRevealRound, position, distances);
					//as a final more general value for using a 2 secret tickets, take their average
					secretYesValue = (secretYesValue_first + secretYesValue_second)/2;
					//Finally, connect the suitability for a double move and two secret ones
					return (doubleYesValue + secretYesValue)/2;
				}
			}
			//

			//DOUBLE: SECRET + NON_SECRET
			if (futurePossibleUsedTickets.get(0) == SECRET){

				//don't use a double with first move a secret ticket, if the round of the secret ticket is a reveal round
				//or one round before a reveal round
				if ((revealRounds.contains(currentRound)) || (revealRounds.contains(currentRound+1))){
					return 0;
				}
				else{
					//calculate secret suitability for first move
					secretYesValue = secretYesValue(view, currentRound, closerSmallerRevealRound, position, distances);
					//Connect the suitability for a double move and a secret first move
					return (doubleYesValue + secretYesValue)/2;
				}
			}
			//

			//DOUBLE: NON_SECRET + SECRET
			if (futurePossibleUsedTickets.get(1) == SECRET) {

				//don't use a double with second move a secret ticket, if the round of the secret ticket is a reveal round
				//or one round before a reveal round
				if ((revealRounds.contains(currentRound+1)) || ( (revealRounds.get(position+1)-1)==(currentRound+1) )){
					return 0;
				}
				else{
					//calculate secret suitability for second move
					secretYesValue = secretYesValue(view, currentRound+1, closerSmallerRevealRound, position, distances);
					//Connect the suitability for a double move and a secret second move
					return (doubleYesValue + secretYesValue)/2;
				}

			}
			//

			//DOUBLE: NON_SECRET + NON_SECRET
			return doubleYesValue;
			//---------------------------------OVER_DOUBLE TICKET -------------------------------------------//

		}


		//return a value that represents the suitability at this point to use generally a SECRET Move
		private double secretYesValue(ScotlandYardView view, int currentRound, int closerSmallerRevealRound, int position, Distance distances){

			double secretYesValue; //final value of how suitable is to play a secret ticket

			//Distance parameters => bigger odds for using this move when both min and average distances are bigger
			double weightedAverageDistance = distances.getSumOfDistances()/((view.getPlayers().size()-1)*7.0);
			double weightedMinDistance;
			if (distances.getShortestDistance()==1) weightedMinDistance = 0; //nullify is detective is 1 hop away
			else weightedMinDistance = distances.getShortestDistance()/7.0;
			//

			//Suitability of Secret Move:
			// > Secret is best after reveal round, and gradually worse as it gets closer to next reveal round
			double differenceRevRounds =  ((revealRounds.get(position+1)-1) - closerSmallerRevealRound) ;
			secretYesValue = currentRound - closerSmallerRevealRound -1;
			secretYesValue = ((differenceRevRounds - secretYesValue) / differenceRevRounds);

			//extra parameters are the distances
			return secretYesValue*0.4 + weightedAverageDistance*0.3 + weightedMinDistance*0.3;

		}


		//return a value that represents the suitability at this point to use generally a DOUBLE Move
		private double doubleYesValue(ScotlandYardView view,int currentRound, int closerSmallerRevealRound, int position, Distance distances) {

			double doubleYesValue; //final value of how suitable is to play a double ticket

			//Distance parameters => bigger odds for using this move when both min and average distances are bigger
			double weightedAverageDistance = distances.getSumOfDistances()/((view.getPlayers().size()-1)*7.0);
			double weightedMinDistance;
			if (distances.getShortestDistance()==1) weightedMinDistance = 0; //nullify if it's 1 hop away
			else weightedMinDistance = distances.getShortestDistance()/7.0;
			//

			int differenceRevRounds =  ((revealRounds.get(position+1)-1) - closerSmallerRevealRound) ;
			doubleYesValue = currentRound - closerSmallerRevealRound;
			doubleYesValue = (differenceRevRounds - doubleYesValue) / differenceRevRounds;

			//weighted final value for using double Move
			//extra parameters are the distances
			doubleYesValue = doubleYesValue*0.15 +  weightedAverageDistance*0.2 + weightedMinDistance*0.65;

			return doubleYesValue;
		}


		//update the hypothetical destinations and used tickets for Mr. X,
		// so that we can check the parameters of every possible move like it was actually been played
		@Override
		public void visit(TicketMove move) {

			futurePossibleLocation = move.destination();
			futurePossibleUsedTickets.add(move.ticket());
		}

		@Override
		public void visit(DoubleMove move) {

			futurePossibleLocation = move.finalDestination();
			futurePossibleUsedTickets.add(move.firstMove().ticket());
			futurePossibleUsedTickets.add(move.secondMove().ticket());
		}
	}
}
