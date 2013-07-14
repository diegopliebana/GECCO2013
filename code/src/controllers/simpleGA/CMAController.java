package controllers.simpleGA;

import controllers.combinedGA.AdvancedMacroAction;
import controllers.diegoAngledTSP.MacroAction;
import controllers.heuristic.GameEvaluator;
import controllers.heuristic.TSPGraphPhysicsEst;
import framework.core.Controller;
import framework.core.Game;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

/**
 * PTSP-Competition Created by Diego Perez, University of Essex. Date: 17/10/12
 */
public class CMAController extends Controller {

	public static int ROLLOUT_DEPTH = 50;
	public controllers.heuristic.graph.Graph m_graph; // Graph for pathfinding.
	public TSPGraphPhysicsEst m_tspGraph; // Pathfinder.
	public int[] m_bestRoute; // Best route of waypoints.
	private int m_currentMacroAction; // Current action in the macro action
										// being executed.
	private MacroAction m_lastAction; // Last macro action to be executed.
	private CMARouteFinder m_ga; // Genetic algorithm
	boolean m_resetGA;
	private GameEvaluator m_gameEvaluator;

	public CMAController(Game a_game, long a_timeDue) {
		m_resetGA = true;
		m_graph = new controllers.heuristic.graph.Graph(a_game);
		m_tspGraph = new controllers.heuristic.TSPGraphPhysicsEst(a_game,
				m_graph); // new TSPGraphSight(a_game, m_graph);
		m_tspGraph.solve();
		m_bestRoute = m_tspGraph.getBestPath();
		m_gameEvaluator = new GameEvaluator(m_tspGraph, m_graph, true);
		m_currentMacroAction = GameEvaluator.MACRO_ACTION_LENGTH;
		m_lastAction = new MacroAction(false, 0,
				GameEvaluator.MACRO_ACTION_LENGTH);


        //GameEvaluator.MACRO_ACTION_LENGTH = 5;   // This needs to be commented out for batch experiments!


		//System.err.println(ROLLOUT_DEPTH/GameEvaluator.MACRO_ACTION_LENGTH);
        m_ga = new CMARouteFinder(ROLLOUT_DEPTH, 3, 1);
	}

	@Override
	public int getAction(Game a_game, long a_timeDue) {
		int cycle = a_game.getTotalTime();
		MacroAction nextAction;

		a_timeDue = (a_timeDue - System.currentTimeMillis());
		// System.err.println(a_timeDue);
		/*
		 * MacroAction action = m_ga.run(a_game, a_timeDue);
		 * 
		 * if(m_currentMacroAction == 0) { m_lastAction = action;
		 * m_currentMacroAction = m_lastAction.m_repetitions - 1;
		 * m_ga.init(a_game); }else { m_currentMacroAction--; }
		 */

		if (cycle == 0) {
            m_ga.init();
			m_lastAction = m_ga.run(a_game, m_gameEvaluator, a_timeDue);//new MacroAction(0, GameEvaluator.MACRO_ACTION_LENGTH);
			nextAction = m_lastAction;
			m_resetGA = true;
			m_currentMacroAction = GameEvaluator.MACRO_ACTION_LENGTH - 1;
		} else {
		    prepareGameCopy(a_game);
			if (m_currentMacroAction > 0) {
				if (m_resetGA) {
					m_ga.init();
				}
				m_ga.run(a_game, m_gameEvaluator, a_timeDue);
				//System.err.println(m_gameEvaluator.bestScore +  ", ");
				m_gameEvaluator.bestScore = 0;
				nextAction = m_lastAction;
				m_currentMacroAction--;
				m_resetGA = false;
			} else if (m_currentMacroAction == 0) {
				nextAction = m_lastAction;
				MacroAction suggestedAction = m_ga.run(a_game, m_gameEvaluator,
						a_timeDue);
				//System.err.println(m_gameEvaluator.bestScore + ", ");
				m_gameEvaluator.bestScore = 0;
				//System.out.println(suggestedAction);
				m_resetGA = true;
				if (suggestedAction != null)
					m_lastAction = suggestedAction;

				if (m_lastAction != null)
					m_currentMacroAction = GameEvaluator.MACRO_ACTION_LENGTH - 1;

			} else {
				throw new RuntimeException("This should not be happening: "
						+ m_currentMacroAction);
			}

		}

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

	public void paint(Graphics2D a_gr) {

	}
}
