package controllers.RandomSearch;

import controllers.diegoAngledTSP.MacroAction;
import controllers.heuristic.GameEvaluator;
import framework.core.Controller;
import framework.core.Game;

import java.util.ArrayList;
import java.util.Random;

/**
 * PTSP-Competition
 * Created by Diego Perez, University of Essex.
 * Date: 17/10/12
 */
public class RS
{
    static public int NUM_ACTIONS_INDIVIDUAL = 8;
    public ArrayList<MacroAction> m_actionList;             //List of available actions to govern the ship.
    public Random m_rnd;                                    //Random number generator
    public GameEvaluator m_gameEvaluator;                   //Game evaluator (score and end-game states)
    public RSIndividual m_bestIndividial;

    public RS(Game a_gameState, GameEvaluator a_gameEvaluator)
    {
        m_rnd = new Random();
        m_gameEvaluator = a_gameEvaluator;
        m_actionList = new ArrayList<MacroAction>();

        // Create actions
        for(int i = Controller.ACTION_NO_FRONT; i <= Controller.ACTION_THR_RIGHT; ++i)
        {
            boolean t = Controller.getThrust(i);
            int s = Controller.getTurning(i);
            m_actionList.add(new MacroAction(t, s, GameEvaluator.MACRO_ACTION_LENGTH));
        }

        init(a_gameState);
    }

    public void init(Game a_gameState)
    {
        //System.out.println("Initializing the RS.");
        m_bestIndividial = new RSIndividual(NUM_ACTIONS_INDIVIDUAL);
    }

    public MacroAction run(Game a_gameState, long a_timeDue)
    {
        m_gameEvaluator.updateNextWaypoints(a_gameState, 2);
        double remaining = (a_timeDue-System.currentTimeMillis());

        while(remaining > 10)
        {
            RSIndividual nextSolution = new RSIndividual(NUM_ACTIONS_INDIVIDUAL); //Or mutate from RSIndividual.
            nextSolution.randomize(m_rnd, m_actionList.size());
            nextSolution.evaluate(a_gameState,m_gameEvaluator);

            if(nextSolution.m_fitness > m_bestIndividial.m_fitness)
            {
                m_bestIndividial = nextSolution;
                //System.out.println("[RS] New best found: " + m_bestIndividial.m_fitness);
            }
            remaining = (a_timeDue-System.currentTimeMillis());
        }

        //System.out.println("[RS] Best this cycle: " + m_bestIndividial.toString());
        return new MacroAction(m_bestIndividial.m_genome[0],GameEvaluator.MACRO_ACTION_LENGTH);
    }


}
