# ScotlandYardGame
Created for Object Oriented Programming unit from University of Bristol with collaboration with Yau Sze Ying 
https://www.ole.bris.ac.uk/bbcswebdav/courses/COMS10009_2018/content/model/index.html
https://www.ole.bris.ac.uk/bbcswebdav/courses/COMS10009_2018/content/ai/index.html



> Scotland Yard Model 

The project started by creating the essential fields of the model. Consequently, the implementation of the constructor of the model followed. Inside the parameterized constructor, which initializes a newly created ScotlandYardModel object, it was necessary to ensure and check that all the information passed by its parameters, following the rules and restrictions of the game (e.g. not null rounds[] and graph, not two players with the same location or colour, etc.). Lastly, all the provided players information was stored in a list that is mutable in order to access and modify its player’s information during the game.
Subsequently, the methods of the ScotlandYardView interface were implemented so that the respective information about the game and its players could be accessed. Following the implementation guide, it was ensured to return an immutable collection for the methods that returned information that couldn’t be changed after their creation (like getRounds(), getPlayers(), etc.). Then for getCurrentPlayer(), getCurrentRound() and getPlayerLocation(), necessary fields were created to keep track of each of this information. For getPlayerLocation(), Mr. X’s last known location had to be maintained, so a field was created dedicated to this information called blackLatestLocation.
Next, the startRotate() method was implemented so that it is possible to start cycling through all the moves of the game. StartRotate() starts from the first player, Mr. X, generates his valid moves according to his location and tickets and notifies the player to choose his move by calling the makeMove() method. After the player chooses their move, the model calls the accept method, in order to accept the move that the player chose as well as, play it out. The accept method, makes sure that the chosen move is not null and is a valid move. After that, the current player advances to the next one and the visitor pattern is used to deal with the three kinds of moves the player might choose (i.e. PassMove, TicketMove and DoubleMove) in order to actually play the move (update player’s location and tickets in hand), possibly advance to the next round and notify the spectators along the way (about the move made, the start of a round and/or the completion of a rotation). In order to generate the valid moves of a player, the validMove() method was created. For a move to be valid, the player has to have enough of the type of ticket used for the potential valid move and the destination of that move has to be unoccupied. To check this we created two auxiliary methods, adequateTickets() and locationFree(). Using these two methods validMove() finds all the different moves that the player could possibly do, checking specifically for single moves using normal tickets (if player is detective) and on top of that for secret and double moves (if player is Mr. X).


> Scotland Yard Model AI 

The AI logic of the second part of the Scotland Yard project is based on 3 main factors: 
1. The difficulty of guessing Mr. X's next move 
2. The available moves of Mr. X starting from a specific node 
3. The distance between Mr. X and the detectives 

Through implementing logic regarding these three main factors, 5 parameters were extracted. In order for the best move to be chosen, these parameters are entered into a scoring function. This scoring function is a weight function that takes the parameters, normalizes them (range: 0-1) and overall computes a score based on the weights that were assigned from us to every parameter. The best move is the one with the biggest score. The weights for these parameters were relatively arbitrarily chosen, based on the importance and effect that every parameter has upon the final result, These weights were determined after trial and error of experiments playing the game multiple times.


***Parameters Extracted from 3 main factors:***
- Number of nodes around the hypothetical future location of Mr. X: This parameter expresses how well-connected is the possible destination of the move. This is important, since the more possible movements a location has, the more difficult it'll be for the detectives to guess where Mr. X went.
- Mr. X’s available Moves: An additional important parameter is how many moves are actually available for Mr. X to possibly follow. It is vital for Mr. X to have as many options to choose from as possible. This availability depends on the tickets of Mr. X and whether detectives are in the immediate neighbouring nodes. 
- Distance from detectives: The most vital factor in choosing the best move for Mr. X is his distance from the detectives. From this feature we can extract two fundamental parameters. The first one is the shortest distance from Mr. X to any of the detectives. And the second one is the average of the shortest distances from all of the detectives. 
- Use of Double & Secret Tickets: It’s also significant to have a strategy for the use of double and secret tickets. That way, Mr. X can effectively escape from situations where the detectives are approaching fast (double ticket) and hide his transport to make his current location more difficult to guess (secret ticket).


***Logic for Finding Shortest Distance:***

In order to find the information about the distance parameters a main method findDistance() was created that finds the shortest distances between Mr. X and all of the nodes that are occupied by detectives. For the calculation of the distance, the Breadth First Search (BFS) algorithm was used to traverse through the graph and Dijkstra's algorithm was adapted for its use to this game which involved an unweighted graph. Essentially, while traversing through the graph by BFS, 2 auxiliary lists (distance[], visitiedNodes[]) and a queue were used. findDistance() calculates the distances of nodes from a start location (Mr. X’s location) until all detectives’ distances are found The method returns an object which contains two attributes: the shortest distance from Mr. X to one of the detectives and the sum of all of the shortest distances between Mr. X and all of the detectives.


***Logic for Secret/ Double Tickets:***

Effectively the best time to use a secret ticket is right after a reveal round, because the current location of Mr. X was just revealed so whatever transport follows, it would be much easier to guess since the location from which Mr. X started, was known. The best time for a double ticket to be used is during a reveal round (i.e. The first move would be on reveal round and the second move on the next round). That way, the detectives won't have the chance to play during the round that the exact location of Mr. X is known and hence target his node specifically. Of course, both of the use of these tickets, is heavily influenced by the distance of the detectives in respect to Mr. X. If all the detectives are far away, there is less use of spending a double or secret ticket, than when the detectives are really close. In that situation the use of a secret ticket, a double ticket or both could give Mr. X the opportunity to get away. Also, note that the use of a secret or double tickets before the first reveal round is just useless because Mr. X could be anywhere, except of the case that Mr. X happens to be right next to a detective. In order to implement this ticket strategy a method was created that deals with both secret and double tickets, since it's also possible to use a secret ticket within a double move.


***Visitor Pattern:***

We implemented the visitor pattern, so as to update the hypothetical destinations and used tickets for Mr. X, so that the parameters of every possible move can be checked like it was the move was actually been played.
