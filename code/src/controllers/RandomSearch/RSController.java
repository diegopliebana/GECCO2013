package controllers.RandomSearch;

import controllers.diegoAngledTSP.MacroAction;
import controllers.heuristic.GameEvaluator;
import controllers.heuristic.TSPGraphPhysicsEst;
import framework.core.Controller;
import framework.core.Game;

import java.awt.*;

/**
 * PTSP-Competition
 * Created by Diego Perez, University of Essex.
 * Date: 17/10/12
 */
public class RSController extends Controller {

    public controllers.heuristic.graph.Graph m_graph;       //Graph for pathfinding.
    public TSPGraphPhysicsEst m_tspGraph;                   //Pathfinder.
    public int[] m_bestRoute;                               //Best route of waypoints.
    private int m_currentMacroAction;                       //Current action in the macro action being executed.
    private MacroAction m_lastAction;                       //Last macro action to be executed.
    private RS m_rs;                                        //Genetic algorithm
    boolean m_resetRS;

    public RSController(Game a_game, long a_timeDue)
    {
        m_resetRS = true;
        m_graph = new controllers.heuristic.graph.Graph(a_game);
        m_tspGraph = new TSPGraphPhysicsEst(a_game, m_graph); //new TSPGraphSight(a_game, m_graph);
        m_tspGraph.solve();
        m_bestRoute = m_tspGraph.getBestPath();
        m_rs = new RS(a_game, new GameEvaluator(m_tspGraph, m_graph, true));
        m_currentMacroAction = 10;
        m_lastAction = new MacroAction(false,0,GameEvaluator.MACRO_ACTION_LENGTH);
    }

    @Override
    public int getAction(Game a_game, long a_timeDue)
    {
        int cycle = a_game.getTotalTime();
        MacroAction nextAction;

        if(cycle == 0)
        {
            m_lastAction = new MacroAction(0, GameEvaluator.MACRO_ACTION_LENGTH);
            nextAction =  m_lastAction;
            m_resetRS = true;
            m_currentMacroAction = GameEvaluator.MACRO_ACTION_LENGTH-1;
        }else
        {
            prepareGameCopy(a_game);
            if(m_currentMacroAction > 0)
            {
                if(m_resetRS)
                {
                    m_rs.init(a_game);
                }
                m_rs.run(a_game, a_timeDue);
                nextAction = m_lastAction;
                m_currentMacroAction--;
                m_resetRS = false;
            }else if(m_currentMacroAction == 0)
            {
                nextAction = m_lastAction;
                MacroAction suggestedAction = m_rs.run(a_game, a_timeDue);
                m_resetRS = true;
                if(suggestedAction != null)
                    m_lastAction = suggestedAction;

                if(m_lastAction != null)
                    m_currentMacroAction = GameEvaluator.MACRO_ACTION_LENGTH-1;


                //System.out.println("[RS] WILL EXECUTE: " + suggestedAction.buildAction());

            }else{
                throw new RuntimeException("This should not be happening: " + m_currentMacroAction);
            }

        }

       // System.out.println("Executing: " + nextAction.buildAction());
        return nextAction.buildAction();
    }


    public void prepareGameCopy(Game a_game)
    {
        if(m_lastAction != null)
        {
            int first = GameEvaluator.MACRO_ACTION_LENGTH - m_currentMacroAction - 1;
            for(int i = first; i < GameEvaluator.MACRO_ACTION_LENGTH; ++i)
            {
                int singleMove = m_lastAction.buildAction();
                a_game.tick(singleMove);
            }
        }
    }

    public void paint(Graphics2D a_gr)
    {

    }
}
