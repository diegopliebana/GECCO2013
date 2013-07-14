package controllers.simpleGA;

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
public class GA
{
    public final int NUM_INDIVIDUALS = 10;
    static public int NUM_ACTIONS_INDIVIDUAL = 8;
    public final int ELITISM = 2;
    public ArrayList<MacroAction> m_actionList;             //List of available actions to govern the ship.
    public Random m_rnd;                                    //Random number generator
    public GameEvaluator m_gameEvaluator;                   //Game evaluator (score and end-game states)
    public int m_numGenerations;

    public GAIndividual[] m_individuals;

    public GA(Game a_gameState,  GameEvaluator a_gameEvaluator)
    {
        m_rnd = new Random();
        m_gameEvaluator = a_gameEvaluator;
        m_actionList = new ArrayList<MacroAction>();

        // Create actions
        for(int i = Controller.ACTION_NO_FRONT; i <= Controller.ACTION_THR_RIGHT; ++i)
        //for(int i = Controller.ACTION_THR_FRONT; i <= Controller.ACTION_THR_RIGHT; ++i)
        {
            boolean t = Controller.getThrust(i);
            int s = Controller.getTurning(i);
            m_actionList.add(new MacroAction(t, s, GameEvaluator.MACRO_ACTION_LENGTH));
        }
        m_individuals = new GAIndividual[NUM_INDIVIDUALS];
        init(a_gameState);
    }

    public void init(Game a_gameState)
    {
        //System.out.println(" --------- Starting GA --------- ");
        m_gameEvaluator.updateNextWaypoints(a_gameState, 2);
        m_numGenerations = 0;
        for(int i = 0; i < NUM_INDIVIDUALS; ++i)
        {
            m_individuals[i] = new GAIndividual(NUM_ACTIONS_INDIVIDUAL);
            m_individuals[i].randomize(m_rnd, m_actionList.size());
            m_individuals[i].evaluate(a_gameState, m_gameEvaluator);
            //System.out.format("individual i: " + i + ", fitness: %.3f, actions: %s \n", m_individuals[i].m_fitness, m_individuals[i].toString());
        }

        sortPopulationByFitness();
    }

    public MacroAction run(Game a_gameState, long a_timeDue)
    {
        m_gameEvaluator.updateNextWaypoints(a_gameState, 2);
        double remaining = (a_timeDue-System.currentTimeMillis());

        while(remaining > 10)
        {
            GAIndividual[] nextPop = new GAIndividual[m_individuals.length];
            //System.out.println(" --------- New generation " + m_numGenerations + " --------- ");

            int i;
            for(i = 0; i < ELITISM; ++i)
            {
                nextPop[i] = m_individuals[i];
            }

            for(;i<m_individuals.length;++i)
            {
                nextPop[i] = m_individuals[i-ELITISM].copy();
                nextPop[i].mutate(m_rnd);
                nextPop[i].evaluate(a_gameState,m_gameEvaluator);
            }

            m_individuals = nextPop;
            sortPopulationByFitness();

           // for(i = 0; i < m_individuals.length; ++i)
           //     System.out.format("individual i: " + i + ", fitness: %.3f, actions: %s \n", m_individuals[i].m_fitness, m_individuals[i].toString());


            m_numGenerations++;
            remaining = (a_timeDue-System.currentTimeMillis());
        }


        //Return the first macro action of the best individual.
        /*System.out.println(m_numGenerations);
        System.out.println("Best: " + m_individuals[0]);
        Game f =  m_individuals[0].evaluate(a_gameState,m_gameEvaluator);

        System.out.println(" --------- Last generation " + m_numGenerations + " " +  f.getWaypointsVisited() + "--------- ");
        for(int i = 0; i < m_individuals.length; ++i)
                 System.out.format("individual i: " + i + ", fitness: %.3f, actions: %s \n", m_individuals[i].m_fitness, m_individuals[i].toString());
         */

        return new MacroAction(m_individuals[0].m_genome[0],GameEvaluator.MACRO_ACTION_LENGTH);
    }

    private void sortPopulationByFitness() {
        for (int i = 0; i < m_individuals.length; i++) {
            for (int j = i + 1; j < m_individuals.length; j++) {
                if (m_individuals[i].m_fitness < m_individuals[j].m_fitness) {
                    GAIndividual gcache = m_individuals[i];
                    m_individuals[i] = m_individuals[j];
                    m_individuals[j] = gcache;
                }
            }
        }
    }

}
