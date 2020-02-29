package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Graph;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {

    //Fields of ScotlandYardModel
    private final List<Boolean> rounds;
    private final Graph<Integer, Transport> graph;
    private ArrayList<ScotlandYardPlayer> players;
    private int blackLatestLocation = 0;  //keeps track Mr. X's latest location
    private int currentRound = 0;
    private int currentPlayer = 0;
    private Set<Spectator> spectators = new HashSet<>();
    //

    //Constructor of ScotlandYardModel
    public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
                             PlayerConfiguration mrX, PlayerConfiguration firstDetective,
                             PlayerConfiguration... restOfTheDetectives) {

        this.rounds = requireNonNull(rounds);
        this.graph = requireNonNull(graph);

        //check if rounds and graph are empty
        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("Empty rounds");
        }
        if (graph.isEmpty()) {
            throw new IllegalArgumentException("Empty graph");
        }
        //

        //check Mr. X is black
        if (mrX.colour != BLACK) {
            throw new IllegalArgumentException("MrX should be Black");
        }
        //

        //create temporary list with all the players and their features
        ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
        for (PlayerConfiguration playerConf : restOfTheDetectives)
            configurations.add(requireNonNull(playerConf));
        configurations.add(0, firstDetective);
        configurations.add(0, mrX);
        //


        Set<Integer> locset = new HashSet<>();      //checking duplicate location
        Set<Colour> colourSet = new HashSet<>();    //checking duplicate colour
        players = new ArrayList<>();                //final list with all player info accessible to the game
        for (PlayerConfiguration playerConf : configurations) {

            //Check for duplicate location and colour
            if (locset.contains(playerConf.location))
                throw new IllegalArgumentException("Duplicate location");
            locset.add(playerConf.location);

            if (colourSet.contains(playerConf.colour))
                throw new IllegalArgumentException("Duplicate colour");
            colourSet.add(playerConf.colour);
            //

            //check that all players have all ticket types
            if (playerConf.tickets.size() < 5) {
                throw new IllegalArgumentException("Some ticket types don't exist");
            }
            //

            //check that detectives don't have double or secret tickets
            if (playerConf.colour != BLACK) {

                if (playerConf.tickets.get(DOUBLE) > 0) {
                    throw new IllegalArgumentException("Detective has Double tickets");
                }

                if (playerConf.tickets.get(SECRET) > 0) {
                    throw new IllegalArgumentException("Detective has Secret tickets");
                }
            }
            //


            //Create list which can be accessible during the game and stores players' information
            ScotlandYardPlayer SYplayer = new ScotlandYardPlayer
                    (playerConf.player, playerConf.colour, playerConf.location, playerConf.tickets);
            players.add(SYplayer);
            //
        }
    }


    @Override
    public void registerSpectator(Spectator spectator) {

        if (spectator != null){

            if (spectators.contains(spectator)) {
                throw new IllegalArgumentException("spectator is already registered");
            }
            else{
                spectators.add(spectator);
            }
        }
        else {
            throw new NullPointerException("spectator is null");
        }
    }

    @Override
    public void unregisterSpectator(Spectator spectator) {

        if (spectator != null){
            if (spectators.contains(spectator)){
                spectators.remove(spectator);
            }
            else {
                throw new IllegalArgumentException("spectator is not registered at the first place");
            }
        }
        else {
            throw new NullPointerException("spectator is null");
        }

    }


    public void accept(Move chosenMove){

        Set<Move> moves; //set with all valid moves
        moves = validMove(players.get(currentPlayer).location(), getCurrentPlayer());

        //check if chosen move is null
        if (chosenMove == null) {
            throw new NullPointerException("chosen move is null");
        }

        //check that chosen move is a valid move
        if (!moves.contains(chosenMove)) {
            throw new IllegalArgumentException("Illegal move");
        }


        //player rotation is not complete
        if (currentPlayer+1 < players.size()) {

            currentPlayer += 1; //advance player
            chosenMove.visit(this); //play the move

            //after playing the move check if gameOver
			if (isGameOver()){
                Set<Colour> winningPlayers;
                winningPlayers = getWinningPlayers();
                for (Spectator spectator:spectators){ //notify spectators with winning players that game is over
                    spectator.onGameOver(this, winningPlayers);
                }
            }
			//

            else{

                //ask user to choose the move of next player
                int location;
                location = players.get(currentPlayer).location(); //current player's (next player) location
                Player player;
                player = players.get(currentPlayer).player();     //current player
                moves = validMove(location, getCurrentPlayer());  //generate the valid moves
                player.makeMove(this, location, moves, this); //ask for move
            }
        }

        //The player rotation is complete
        else {
            currentPlayer=0; //start from Mr. X again
            chosenMove.visit(this); //play the move

            //after playing the move check if gameOver
            if (isGameOver()){
                Set<Colour> winningPlayers;
                winningPlayers = getWinningPlayers();
                for (Spectator spectator:spectators){ //notify spectators with winning players that game is over
                    spectator.onGameOver(this, winningPlayers);
                }
            }

            //notify spectators that rotation is complete
            else{

                for (Spectator spectator:spectators){
                    spectator.onRotationComplete(this);
                }
            }
        }
    }


    //check if player has adequate tickets for specific transportation <= used in validMoves()
    private boolean adequateTickets(Ticket ticket, Colour colourOfPlayer,int temp) {
        //temp variable to take into account the tickets used for double moves of MrX, since the move hasn't been played yet

        for (ScotlandYardPlayer player : players){
            if (player.colour() == colourOfPlayer){
                return (player.tickets().get(ticket) - temp > 0); //check if adequate tickets
            }
        }
        return false;
    }


    //None of the other players is already on the destination of the current player <= used in validMoves()
    private boolean locationFree(int destination, Colour playerColour){

        int numberOfPlayers = players.size();
        int temp=0; //keep track of all players that aren't on the destination location

        for (ScotlandYardPlayer player : players.subList(1,players.size())){ //check if all other players aren't currently in the destination position
            if (player.colour() == playerColour){ //if we check player which is the current players
                temp+=1;
            }
            else if (player.location() != destination){ //for rest of players checks that they're on different location than the destination
                temp+=1;
            }
        }

        return (temp == numberOfPlayers-1); //no player (other than MrX) is on destination location = true
    }

    //generates the valid moves of a player from a specific location
    private Set<Move> validMove(int location, Colour colourOfPlayer) {

        Set<Move> moves = new HashSet<>(); //set for all valid moves
        Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(graph.getNode(location)); //collection of edges from location
        int temp = 0; //<= used for adequateTickets()


        //====================================SINGLE MOVE=========================================
        //iterates through all of the edges and finds valid single moves
        for (Edge<Integer, Transport> edge : edges) {

            Ticket ticket = fromTransport(edge.data());
            int destination = edge.destination().value();
            TicketMove move = new TicketMove(colourOfPlayer, ticket , destination);

            //NORMAL TICKET
            if (adequateTickets(ticket, colourOfPlayer,temp)){ //check for adequate tickets on player
                if (locationFree(destination, colourOfPlayer)){ //check if destination location is free

                    moves.add(move);
                }
            }
            //

            //SECRET TICKET
            if (adequateTickets(SECRET, colourOfPlayer,temp)){ //check for adequate secret tickets
                if (locationFree(destination, colourOfPlayer)) { //check if destination location is free

                    moves.add(new TicketMove(colourOfPlayer, SECRET, destination));
                }
            }
            //
        }

        //If detective has no place to go just PASS
        if (!colourOfPlayer.equals(BLACK)) {
            if (moves.isEmpty())
                moves.add(new PassMove(colourOfPlayer));
        }
        //=======================================OVER_SINGLE MOVE=====================================


        //=======================================DOUBLE MOVES=====================================
        else { //If current player is Mr. X check for double moves

            //checks for adequate double tickets & that there are enough rounds to play a double move
            if ((adequateTickets(DOUBLE, colourOfPlayer,temp)) && (rounds.size()-currentRound>=2))  {

                //Checking for first move
                Collection<Edge<Integer, Transport>> edges_firstmove = graph.getEdgesFrom(graph.getNode(location));
                for (Edge<Integer, Transport> edge_firstmove : edges_firstmove) {

                    Ticket ticket_first = fromTransport(edge_firstmove.data());
                    int destination_first = edge_firstmove.destination().value();

                    //Checking for second move
                    Collection<Edge<Integer, Transport>> edges_secondmove = graph.getEdgesFrom(graph.getNode(destination_first));
                    for (Edge<Integer, Transport> edge_secondmove : edges_secondmove) {

                        Ticket ticket_second = fromTransport(edge_secondmove.data());
                        int destination_second = edge_secondmove.destination().value();
                        temp = 0; //<= used for adequateTickets()

                        //1:NORMAL - 2:NORMAL
                        //1st move normal ticket?
                        if (adequateTickets(ticket_first, colourOfPlayer,temp)) { //check for adequate tickets on player
                            if (locationFree(destination_first, colourOfPlayer)) { //check if first move destination location is free

                                if (ticket_first == ticket_second) temp = 1; //the first and second tickets used are the same


                                //2nd move normal ticket?
                                if (adequateTickets(ticket_second, colourOfPlayer, temp)) { //check for adequate tickets for second move taking into consideration the first move
                                    if (locationFree(destination_second, colourOfPlayer)) { //check if second move destination location is free

                                        moves.add(new DoubleMove(colourOfPlayer, ticket_first, destination_first, ticket_second, destination_second));
                                    }
                                }
                            }
                        }


                        //1:SECRET - 2:NORMAL
                        //1st move secret?
                        if (adequateTickets(SECRET,colourOfPlayer, 0)) { //check for adequate tickets for first move taking into consideration the first move
                            if (locationFree(destination_first, colourOfPlayer)) { //check if first move destination location is free

                                //2nd move normal?
                                if (adequateTickets(ticket_second, colourOfPlayer, 0)) { //check for adequate tickets for second move taking into consideration the first move
                                    if (locationFree(destination_second, colourOfPlayer)) { //check if second move destination location is free

                                        moves.add(new DoubleMove(colourOfPlayer, SECRET, destination_first, ticket_second, destination_second));
                                    }
                                }
                            }
                        }


                        //1:NORMAL - 2:SECRET
                        //1st move normal ticket?
                        if (adequateTickets(ticket_first, colourOfPlayer,0)) { //check for adequate tickets on player
                            if (locationFree(destination_first, colourOfPlayer)) { //check if first move destination location is free

                                //2nd move SECRET?
                                if (adequateTickets(SECRET, colourOfPlayer, 0)) { //check for adequate tickets for second move taking into consideration the first move
                                    if (locationFree(destination_second, colourOfPlayer)) { //check if second move destination location is free

                                        moves.add(new DoubleMove(colourOfPlayer, ticket_first, destination_first, SECRET, destination_second));
                                    }
                                }
                            }
                        }


                        //1:SECRET - 2:SECRET
                        if (adequateTickets(SECRET, colourOfPlayer,1)) { //check for adequate tickets on player
                            if (locationFree(destination_first, colourOfPlayer)) { //check if first move destination location is free

                                if (locationFree(destination_second, colourOfPlayer)) { //check if second move destination location is free

                                    moves.add(new DoubleMove(colourOfPlayer, SECRET, destination_first, SECRET, destination_second));

                                }
                            }
                        }
                    }
                }
                //================================================OVER_DOUBLE MOVES============================================
            }
        }
        return moves; //all the valid moves the method generated
    }


    @Override
    public void startRotate() {

        currentPlayer = 0; //start from Mr. X

        //check if game is over before game starts
        if (isGameOver()){
            Set<Colour> winningPlayers;
            winningPlayers = getWinningPlayers();
            for (Spectator spectator:spectators){ //notify spectators with winning players that game is over
                spectator.onGameOver(this, winningPlayers);
            }
            throw new IllegalStateException("GAME IS OVER");
        }

        else{
            int location;
            location = players.get(currentPlayer).location();
            Player player;
            player = players.get(currentPlayer).player();

            Set<Move> moves;
            moves = validMove(location, getCurrentPlayer());

            player.makeMove(this, location, moves, this); //ask for move
        }
    }

    @Override
    public Collection<Spectator> getSpectators() {

        return Collections.unmodifiableSet(spectators);
    }

    @Override
    public List<Colour> getPlayers() {

        ArrayList<Colour> colourList = new ArrayList<>();
        for (ScotlandYardPlayer player : players) {
            colourList.add(player.colour());
        }

        return Collections.unmodifiableList(colourList);
    }

    @Override
    //get winning players returns a set of the winning players if game is over, otherwise the list is empty
    public Set<Colour> getWinningPlayers() {

        Set<Colour> winningPlayers= new HashSet<>();
        Set<Move> moves;

        //===============================MrX WINS===========================
        //Detectives are stuck
        int temp=0;
        for (ScotlandYardPlayer player : players.subList(1,players.size())){

            moves = validMove(player.location(), player.colour()); //generates the valid moves for player
            PassMove passmove = new PassMove(player.colour());     //creates a pass move object

            if (moves.contains(passmove)) //checks if detective has nowhere to go
                temp+=1;
        }
        if (temp == (players.size()-1)){ //if all detectives are stuck -> Mr. X wins
            winningPlayers.add(BLACK);
        }
        //

        //MrX not captured in last round
        if (currentRound == rounds.size() && currentPlayer == 0){ //if current round is the last & current player is Mr.X
            winningPlayers.add(BLACK);
        }
        //
        //============================OVER_MrX WINS===========================


        //---------------------------------DETECTIVES WIN------------------------------
        //MrX cornered or MrX cannot move
        moves = validMove(players.get(0).location(), BLACK); //generate valid moves for Mr. X
        //If Mr. X has nowhere to go -> detectives win
        if (moves.isEmpty() && currentPlayer==0){
            for (ScotlandYardPlayer player: players.subList(1,players.size())){
                winningPlayers.add(player.colour());
            }
        }
        //

        //MrX captured
        //check if at least one detective is on the same location as Mr. X -> detectives win
        for (ScotlandYardPlayer player : players.subList(1,players.size())){
            if (player.location() == players.get(0).location()){
                for (ScotlandYardPlayer playerCol: players.subList(1,players.size())){
                    winningPlayers.add(playerCol.colour());
                }
                break;
            }
        }
        //
        //------------------------------OVER_DETECTIVES WIN------------------------------

        return Collections.unmodifiableSet(winningPlayers);
    }

    @Override
    public Optional<Integer> getPlayerLocation(Colour colour) {

        //Location of Mr.X
        if (colour == BLACK){
            return Optional.of(blackLatestLocation);
        }

        //Location of detective
        for (ScotlandYardPlayer playerConf : players){
            if (playerConf.colour()==colour) {
                return Optional.of(playerConf.location());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {

        for (ScotlandYardPlayer playerConf : players){
            if (playerConf.colour()==colour) {
                return Optional.of(playerConf.tickets().get(ticket));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isGameOver() {

        if (getWinningPlayers().isEmpty())  //if the set is empty, then the game is not over
            return false;
        else
            return true;
    }

    @Override
    public Colour getCurrentPlayer() {

        return players.get(currentPlayer).colour();
    }

    @Override
    public int getCurrentRound() {

        return currentRound;
    }

    @Override
    public List<Boolean> getRounds() {

        return Collections.unmodifiableList(rounds);
    }

    @Override
    public Graph<Integer, Transport> getGraph() {

        ImmutableGraph<Integer, Transport> graph = new ImmutableGraph<>(this.graph);
        return graph;
    }





    //================================VISITOR PATTERN - MOVE VISITOR IMPLEMENTATIONS====================================
    //For the PassMove the only thing that happens is that the spectators are notified about the move <= by onMoveMade()
    @Override
    public void visit(PassMove move) {

        for (Spectator spectator : spectators){
            spectator.onMoveMade(this, move);
        }

    }


    // Every time after Mr. X plays the round is started, so in that case the field that keeps the current round advances
    // and the onRoundStarted method is called to notify all spectators. Finally, all the spectators are notified
    // that a move was made by the onMoveMade method.
    @Override
    public void visit(TicketMove move) {

        //finds which player plays the move
        ScotlandYardPlayer player_move = players.get(0);
        for (ScotlandYardPlayer player : players){
            if (player.colour() == move.colour()){
                player_move = player;
                break;
            }
        }
        //

        //updates the player’s location
        player_move.location(move.destination());
        TicketMove hiddenMove = move;

        //If the player is a detective, then remove the used ticket from its disposal & give it to Mr. X
        if (!move.colour().equals(BLACK)) {

            players.get(0).addTicket(move.ticket());
            player_move.removeTicket(move.ticket());
        }


        //If player is Mr. X
        else{

            player_move.removeTicket(move.ticket());

            if (!rounds.get(currentRound)){ //if current round is hidden -> update the move with latest location of Mr. X
                hiddenMove = new TicketMove(move.colour(), move.ticket(), blackLatestLocation);
            }
            else{ //if current round is revealed, update latest location of Mr. X
                blackLatestLocation = move.destination();
            }


            // After Mr. X plays, a new round is started, so current round advances
            // and the onRoundStarted method is called to notify all spectators
            currentRound+=1;
            for (Spectator spectator : spectators){
                spectator.onRoundStarted(this, currentRound);
            }
            //
        }

        //All spectators are notified for the new move played by the detectives or Mr. X
        for (Spectator spectator : spectators){
            spectator.onMoveMade(this, hiddenMove);
        }
    }



    //For a DoubleMove the visit method functions in the same way as the TicketMove for Mr. X,
    // Difference is that the onMoveMade method is called for the overall double move,
    // and also separately for the first and second move of this double move. Also, it advances the rounds
    // one at a time at the same time as it deals with the moves separately.
    @Override
    public void visit(DoubleMove move) {

        //Only Mr. X plays double moves
        ScotlandYardPlayer player_move = players.get(0);

        //updates Mr. X's location to second move's destination
        player_move.location(move.secondMove().destination());

        TicketMove first_hiddenMove = move.firstMove();
        TicketMove second_hiddenMove = move.secondMove();

        int tempLatestLocation = blackLatestLocation;
        if (!rounds.get(currentRound)){ //if first move's round is hidden
            first_hiddenMove = new TicketMove(move.firstMove().colour(), move.firstMove().ticket(), tempLatestLocation);
        }
        else{ //first move round is reveal
            tempLatestLocation = move.firstMove().destination();
        }

        if (!rounds.get(currentRound+1)){ //if second move's round is hidden
            second_hiddenMove = new TicketMove(move.secondMove().colour(), move.secondMove().ticket(), tempLatestLocation);
        }

        DoubleMove double_hiddenMove = new DoubleMove(move.firstMove().colour(), first_hiddenMove, second_hiddenMove);

        //DOUBLE MOVE
        //remove Double ticket from Mr.X
        player_move.removeTicket(DOUBLE);
        //Notify all spectators that DOUBLE move was played
        for (Spectator spectator : spectators){
            spectator.onMoveMade(this, double_hiddenMove);
        }
        //

        //FIRST MOVE
        //remove First move ticket from Mr.X
        player_move.removeTicket(move.firstMove().ticket());
        if (rounds.get(currentRound)){ //if the round is reveal, update Mr. X's latest location
            blackLatestLocation = move.firstMove().destination();
        }

        currentRound+=1; //current round advances
        //All spectators are notified that a new round has started and that a new move was made
        for (Spectator spectator : spectators){

            spectator.onRoundStarted(this, currentRound);
            spectator.onMoveMade(this, first_hiddenMove);
        }
        //


        //SECOND MOVE
        //remove Second move ticket from Mr.X
        player_move.removeTicket(move.secondMove().ticket());
        if (rounds.get(currentRound)){ //current round reveal, update Mr. X's latest location
            blackLatestLocation = move.secondMove().destination();
        }

        currentRound+=1; //current round advances
        //All spectators are notified that a new round has started and that a new move was made
        for (Spectator spectator : spectators){

            spectator.onRoundStarted(this, currentRound);
            spectator.onMoveMade(this, second_hiddenMove);
        }
        //
    }
    //============================OVER_VISITOR PATTERN - MOVE VISITOR IMPLEMENTATIONS====================================

}