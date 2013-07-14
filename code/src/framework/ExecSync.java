package framework;

import controllers.RandomSearch.RS;
import controllers.diegoAngledTSP.MCTS;
import controllers.heuristic.GameEvaluator;
import controllers.keycontroller.KeyController;
import controllers.simpleGA.CMAController;
import controllers.simpleGA.GA;
import framework.core.*;
import framework.utils.JEasyFrame;

import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;


/**
 * This class may be used to execute the game in timed or un-timed modes, with or without
 * visuals. Competitors should implement his controller in a subpackage of 'controllers'.
 * The skeleton classes are already provided. The package
 * structure should not be changed (although you may create sub-packages in these packages).
 */
@SuppressWarnings("unused")
public class ExecSync extends Exec
{

        //m_controllerName = ; //Set here the controller name. Leave it to null to play with KeyController.
        //m_controllerName = ; //Set here the controller name. Leave it to null to play with KeyController.
        //m_controllerName = "controllers.combinedGA.DiegoController"; //Set here the controller name. Leave it to null to play with KeyController.

    static String m_controllerFullNames[] = new String[]{"controllers.diegoAngledTSP.DiegoController", "controllers.simpleGA.CMAController",
    "controllers.diegoAngledTSP.DiegoController", "controllers.simpleGA.GAController", "controllers.RandomSearch.RSController"};

    static String m_controllerShortNames[] = new String[]{"MCTS", "CMA", "UCT", "GA", "RS"};


    /**
     * Run a game in : the game waits ONE map. In order to slow thing down in case
     * the controllers return very quickly, a time limit can be used.
     * Use this mode to play the game with the KeyController.
     *
     * @param delay The delay between time-steps
     */
    public static void playGame(int delay)
    {
        m_controllerName = "controllers.keycontroller.KeyController";

        //Get the game ready.
        if(!prepareGame())
            return;


        //Indicate what are we running
        if(m_verbose) System.out.println("Running " + m_controllerName + " in map " + m_game.getMap().getFilename() + "...");

        JEasyFrame frame;

        //View of the game, if applicable.
        m_view = new PTSPView(m_game, m_game.getMapSize(), m_game.getMap(), m_game.getShip(), m_controller);
        frame = new JEasyFrame(m_view, "PTSP-Game: " + m_controllerName);

        //If we are going to play the game with the cursor keys, add the listener for that.
        if(m_controller instanceof KeyController)
        {
            frame.addKeyListener(((KeyController)m_controller).getInput());
        }


        while(!m_game.isEnded())
        {

            //When the result is expected:
            long then = System.currentTimeMillis();
            long due = then+PTSPConstants.ACTION_TIME_MS;

            //Advance the game.
            m_game.tick(m_controller.getAction(m_game.getCopy(), due));

            long now = System.currentTimeMillis();
            int remaining = (int) Math.max(0, delay - (now-then));     //To adjust to the proper framerate.

            //Wait until de next cycle.
            waitStep(remaining);

            //And paint everything.
            m_view.repaint();
        }

        if(m_verbose)
            m_game.printResults();

        //And save the route, if requested:
        if(m_writeOutput)
            m_game.saveRoute();

    }

    /**
     * Run a game in : the game executes ONE map.
     *
     * @param visual Indicates whether or not to use visuals
     * @param delay Includes delay between game steps.
     */
    public static void runGame(boolean visual, int delay)
    {
        //Get the game ready.
        if(!prepareGame())
            return;


        //Indicate what are we running
        if(m_verbose) System.out.println("Running " + m_controllerName + " in map " + m_game.getMap().getFilename() + "...");

        JEasyFrame frame;
        if(visual)
        {
            //View of the game, if applicable.
            m_view = new PTSPView(m_game, m_game.getMapSize(), m_game.getMap(), m_game.getShip(), m_controller);
            frame = new JEasyFrame(m_view, "PTSP-Game: " + m_controllerName);
        }


        while(!m_game.isEnded())
        {
            //When the result is expected:
            long then = System.currentTimeMillis();
            long due = then + PTSPConstants.ACTION_TIME_MS;

            //Advance the game.
            int actionToExecute = m_controller.getAction(m_game.getCopy(), due);

            //Exceeded time
            long now = System.currentTimeMillis();
            long spent = now - then;

            if(spent > PTSPConstants.TIME_ACTION_DISQ)
            {
                //actionToExecute = 0;
                //System.out.println("Controller disqualified. Time exceeded: " + (spent - PTSPConstants.TIME_ACTION_DISQ));
                //m_game.abort();
                    //System.out.println("Timing out... " + (spent - PTSPConstants.TIME_ACTION_DISQ));
                    m_game.tick(actionToExecute);

            }else{

               /* if(spent > PTSPConstants.ACTION_TIME_MS)
                    actionToExecute = 0;      */
                m_game.tick(actionToExecute);
            }

            int remaining = (int) Math.max(0, delay - (now-then));//To adjust to the proper framerate.
            //Wait until de next cycle.
            waitStep(remaining);

            //And paint everything.
            if(m_visibility)
            {
                m_view.repaint();
                if(m_game.getTotalTime() == 1)
                    waitStep(m_warmUpTime);
            }
        }

        if(m_verbose)
            m_game.printResults();

        //And save the route, if requested:
        if(m_writeOutput)
            m_game.saveRoute();

    }

    /**
     * For running multiple games without visuals, in several maps (m_mapNames).
     *
     * @param trials The number of trials to be executed
     */
    public static void runGames(int trials)
    {
        //Prepare the average results.
        double avgTotalWaypoints=0;
        double avgTotalTimeSpent=0;
        int totalDisqualifications=0;
        int totalNumGamesPlayed=0;
        boolean moreMaps = true;

        for(int m = 0; moreMaps && m < m_mapNames.length; ++m)
        {
            String mapName = m_mapNames[m];
            double avgWaypoints=0;
            double avgTimeSpent=0;
            int numGamesPlayed = 0;

            if(m_verbose)
            {
                System.out.println("--------");
                System.out.println("Running " + m_controllerName + " in map " + mapName + "...");
            }

            //For each trial...
            for(int i=0;i<trials;i++)
            {
                // ... create a new game.
                if(!prepareGame())
                    continue;

                numGamesPlayed++; //another game

                //PLay the game until the end.
                while(!m_game.isEnded())
                {
                    //When the result is expected:
                    long due = System.currentTimeMillis()+PTSPConstants.ACTION_TIME_MS;

                    //Advance the game.
                    int actionToExecute = m_controller.getAction(m_game.getCopy(), due);

                    //Exceeded time
                    long exceeded = System.currentTimeMillis() - due;
                    if(exceeded > PTSPConstants.TIME_ACTION_DISQ)
                    {
                        //actionToExecute = 0;
                        //numGamesPlayed--;
                        if(m_verbose)
                            System.out.println("Timing out... " + exceeded);
                        //m_game.abort();
                        m_game.tick(actionToExecute);
                    }else{

                        /*if(exceeded > PTSPConstants.ACTION_TIME_MS)
                            actionToExecute = 0;      */

                        m_game.tick(actionToExecute);
                    }

                }

                //Update the averages with the results of this trial.
                avgWaypoints += m_game.getWaypointsVisited();
                avgTimeSpent += m_game.getTotalTime();

                //Print the results.
                if(m_verbose)
                {
                    System.out.print(i+"\t");
                    m_game.printResults();
                }
                System.out.println(m_game.getWaypointsVisited() +","+ m_game.getTotalTime());

                //And save the route, if requested:
                if(m_writeOutput)
                    m_game.saveRoute();
            }

            moreMaps = m_game.advanceMap();

            avgTotalWaypoints += (avgWaypoints / numGamesPlayed);
            avgTotalTimeSpent += (avgTimeSpent / numGamesPlayed);
            totalDisqualifications += (trials - numGamesPlayed);
            totalNumGamesPlayed += numGamesPlayed;

            //Print the average score.
            if(m_verbose)
            {
                System.out.println("--------");
                System.out.format("Average waypoints: %.3f, average time spent: %.3f\n", (avgWaypoints / numGamesPlayed), (avgTimeSpent / numGamesPlayed));
                System.out.println("Disqualifications: " + (trials - numGamesPlayed) + "/" + trials);
            }
        }

        //Print the average score.
        if(m_verbose)
        {
            System.out.println("-------- Final score --------");
            System.out.format("Average waypoints: %.3f, average time spent: %.3f\n", (avgTotalWaypoints / m_mapNames.length), (avgTotalTimeSpent / m_mapNames.length));
            System.out.println("Disqualifications: " + (trials*m_mapNames.length - totalNumGamesPlayed) + "/" + trials*m_mapNames.length);
        }
    }

     /**
     * For running multiple games without visuals, in several maps (m_mapNames).
     *
     * @param trials The number of trials to be executed
     */
    public static void runTests(int trials, int[] macroActionLengths, int[] controllerNames, int[] actionsDepth)
    {
        //Prepare the average results.
        boolean moreMaps = true;
        ArrayList<String> csvOutput;
        String route = "testData/";

        for(int c = 0; c < controllerNames.length; ++c)
        {
            //Controller to execute.
            m_controllerName  = m_controllerFullNames[controllerNames[c]];

            for(int macroLengthIdx = 0; macroLengthIdx < macroActionLengths.length; ++macroLengthIdx)
            {
                //Macro action length
                GameEvaluator.MACRO_ACTION_LENGTH = macroActionLengths[macroLengthIdx];
                int numActions = 0;

                //This sets the DEPTH (whatever comes in  actionsDepth[])
                switch (controllerNames[c])
                {
                    case 0: //MCTS
                        MCTS.ROLLOUT_DEPTH=actionsDepth[macroLengthIdx];
                        numActions = MCTS.ROLLOUT_DEPTH;
                        break;
                    case 1: //CMA
                    	CMAController.ROLLOUT_DEPTH=actionsDepth[macroLengthIdx];
                        numActions = CMAController.ROLLOUT_DEPTH;
                        break;
                    case 2: //UCT
                        MCTS.ROLLOUT_DEPTH=0;
                        numActions = 0;
                        break;
                    case 3: //GA
                        GA.NUM_ACTIONS_INDIVIDUAL=actionsDepth[macroLengthIdx];
                        numActions = GA.NUM_ACTIONS_INDIVIDUAL;
                        break;
                    case 4: //RS
                        RS.NUM_ACTIONS_INDIVIDUAL=actionsDepth[macroLengthIdx];
                        numActions = RS.NUM_ACTIONS_INDIVIDUAL;
                        break;
                }


                String filename = m_controllerShortNames[controllerNames[c]] + "-" + GameEvaluator.MACRO_ACTION_LENGTH;
                csvOutput = new ArrayList<String>();

                for(int m = 0;/*moreMaps &&*/ m < m_mapNames.length; ++m)
                {
                    String mapName = m_mapNames[m];

                    double avgWaypoints=0;
                    double avgTimeSpent=0;

                    if(m_verbose)
                    {
                        System.out.println("--------");
                        System.out.println("Running " + m_controllerShortNames[controllerNames[c]] + " in map " + mapName +
                                " with macro action length " + GameEvaluator.MACRO_ACTION_LENGTH + " and num. macro-actions: " + numActions);
                    }

                    //For each trial...
                    for(int i=0;i<trials;i++)
                    {
                        // ... create a new game.
                        if(!prepareGame())
                            continue;

                        //Play the game until the end.
                        while(!m_game.isEnded())
                        {
                            //When the result is expected:
                            long due = System.currentTimeMillis()+PTSPConstants.ACTION_TIME_MS;

                            //Advance the game.
                            int actionToExecute = m_controller.getAction(m_game.getCopy(), due);

                            m_game.tick(actionToExecute);
                        }

                        //Update the averages with the results of this trial.
                        avgWaypoints += m_game.getWaypointsVisited();
                        avgTimeSpent += m_game.getTotalTime();

                        //Print the results.
                        if(m_verbose)
                        {
                            System.out.println(m_game.getWaypointsVisited() + "," + m_game.getTotalTime());
                        }

                        csvOutput.add(m_game.getWaypointsVisited() + "," + m_game.getTotalTime());

                        //And save the route, if requested:
                        if(m_writeOutput)
                        {
                            String gameFile = filename + "-m" + m + "-t" + i + ".txt";
                            m_game.saveRoute(route + gameFile);
                        }

                    }

                    moreMaps = m_game.advanceMap();
                    if(!moreMaps)
                        m_game.goToFirstMap();

                    avgWaypoints /= trials;
                    avgTimeSpent /= trials;

                    if(m_verbose)
                    {
                        System.out.print("=====> ");
                        System.out.format("Controller %s, Macro action length: %d, Map: %s, ", m_controllerShortNames[controllerNames[c]], GameEvaluator.MACRO_ACTION_LENGTH, mapName);
                        System.out.format("Average waypoints: %.3f, average time spent: %.3f\n", avgWaypoints, avgTimeSpent);
                    }
                }

                //Print the results for this macro-action length:
                try{
                    filename = filename + ".csv";
                    PrintWriter out = new PrintWriter(new FileWriter(route + m_controllerShortNames[controllerNames[c]] + "/" + filename));
                    int nLines = csvOutput.size();
                    for(int i = 0; i < nLines; ++i)
                        out.println(csvOutput.get(i));
                    out.close();

                }catch(Exception e)
                {
                    if(m_verbose)
                        e.printStackTrace();
                }

            }
        }

    }

    /**
     * The main method. Several options are listed - simply remove comments to use the option you want.
     *
     * @param args the command line arguments. Not needed in this class.
     */
    public static void main(String[] args)
    {

        /*m_mapNames = new String[]{"maps/StageA/ptsp_map56.map",
                                  "maps/StageA/ptsp_map02.map",
                                  "maps/StageA/ptsp_map08.map",
                                  "maps/StageA/ptsp_map19.map",
                                  "maps/StageA/ptsp_map24.map",
                                  "maps/StageA/ptsp_map35.map",
                                  "maps/StageA/ptsp_map40.map",
                                  "maps/StageA/ptsp_map45.map",
                                  "maps/StageA/ptsp_map56.map",
                                  "maps/StageA/ptsp_map61.map"};  */


    	
        m_mapNames = new String[]{"maps/Stage10A/ptsp_map02.map",
                "maps/Stage10A/ptsp_map02.map",
                "maps/Stage10A/ptsp_map08.map",
                "maps/Stage10A/ptsp_map19.map",
                "maps/Stage10A/ptsp_map24.map",
                "maps/Stage10A/ptsp_map35.map",
                "maps/Stage10A/ptsp_map40.map",
                "maps/Stage10A/ptsp_map45.map",
                "maps/Stage10A/ptsp_map56.map",
                "maps/Stage10A/ptsp_map61.map"};

    	
        //m_controllerName = "controllers.diegoAngledTSP.DiegoController"; //Set here the controller name. Leave it to null to play with KeyController.
        //m_controllerName = "controllers.simpleGA.GAController"; //Set here the controller name. Leave it to null to play with KeyController.
        //m_controllerName = "controllers.combinedGA.DiegoController"; //Set here the controller name. Leave it to null to play with KeyController.

        m_controllerName = "controllers.RandomSearch.RSController";
        
        m_visibility = true; //Set here if the graphics must be displayed or not (for those modes where graphics are allowed).
        m_writeOutput = true; //Indicate if the actions must be saved to a file after the end of the game.
        m_verbose = true;
        //m_warmUpTime = 750; //Change this to modify the wait time (in milliseconds) before starting the game in a visual mode

        /////// 1. To play the game with the key controller.
        //int delay = PTSPConstants.DELAY;  //PTSPConstants.DELAY: best human play speed
        //playGame(delay);

        /////// 2. Executes one game.
        //int delay = 0;  //0: quickest; PTSPConstants.DELAY: human play speed, PTSPConstants.ACTION_TIME_MS: max. controller delay
        //runGame(m_visibility, delay);

        ////// 3. Executes N games (numMaps x numTrials), graphics disabled.
        //int numTrials=5;
        //runGames(numTrials);

        ////// 4. Tests

        /****************************************************************************************************/
        // DON'T DO IT AGAIN, THIS IS THE DROPBOX VERSION OF THE CODE!!!!!!!!!!!!!!!!!!!!!!!!
        /****************************************************************************************************/
        int numTrials = 20;     //20
        int macroActionLength[] = new int[]{1,5,10,15,20};    //1,5,10,15,20
        int actionsDepth[] = new int[]{50,24,12,8,6};    //50,24,12,8,6            //This should be number of rollouts / size of genome
        int []controllers = new int[]{4};  //0,1,2,3,4        //0:mcts, 1:cma, 2:uct, 3:ga, 4:rs
        runTests(numTrials, macroActionLength, controllers, actionsDepth);

    }





}