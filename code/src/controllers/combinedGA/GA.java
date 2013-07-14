package controllers.combinedGA;

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
    public final int ELITISM = 2;
    public ArrayList<Integer> m_actionList;             //List of available actions to govern the ship.
    public Random m_rnd;                                    //Random number generator
    public GameEvaluator m_gameEvaluator;                   //Game evaluator (score and end-game states)
    public int m_numGenerations;

    public GAIndividual[] m_individuals;

    public GA(Game a_gameState,  GameEvaluator a_gameEvaluator)
    {
        m_rnd = new Random(1);
        m_gameEvaluator = a_gameEvaluator;
        m_actionList = new ArrayList<Integer>();

        // Create actions
        for(int i = Controller.ACTION_NO_FRONT; i <= Controller.ACTION_THR_RIGHT; ++i)
        {
            m_actionList.add(i);
        }
        m_individuals = new GAIndividual[NUM_INDIVIDUALS];
    }

    public void init(Game a_gameState, ArrayList<MacroAction> a_actions)
    {
        //System.out.println("[GA] In action!");
        m_gameEvaluator.updateNextWaypoints(a_gameState, 2);
        m_numGenerations = 0;

        for(int i = 0; i < NUM_INDIVIDUALS; ++i)
        {
            m_individuals[i] = new GAIndividual(a_actions);
            if(i>0)
                m_individuals[i].mutate(m_rnd, GameEvaluator.MACRO_ACTION_LENGTH);

            m_individuals[i].evaluate(a_gameState, m_gameEvaluator);
            //System.out.format("individual i: " + i + ", fitness: %.3f, actions: %s \n", m_individuals[i].m_fitness, m_individuals[i].toString());
        }
        //System.out.println("IND-BASE: " + m_individuals[0]);

        sortPopulationByFitness();
    }

    public AdvancedMacroAction run(Game a_gameState, long a_timeDue)
    {
        m_gameEvaluator.updateNextWaypoints(a_gameState, 2);
        double remaining = (a_timeDue-System.currentTimeMillis());//10;//(a_timeDue-System.currentTimeMillis());

        while(remaining > 5)
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
                nextPop[i].mutate(m_rnd, GameEvaluator.MACRO_ACTION_LENGTH);
                nextPop[i].evaluate(a_gameState,m_gameEvaluator);
            }

            m_individuals = nextPop;
            sortPopulationByFitness();

            //for(i = 0; i < m_individuals.length; ++i)
            //    System.out.format("individual i: " + i + ", fitness: %.3f, actions: %s \n", m_individuals[i].m_fitness, m_individuals[i].toString());

            m_numGenerations++;
            remaining = (a_timeDue-System.currentTimeMillis()); //remaining-1;//(a_timeDue-System.currentTimeMillis());
        }

        //Return the first macro action of the best individual.
        //System.out.println(m_numGenerations);
        if(m_individuals[0].m_genome.size() > 0)
            return new AdvancedMacroAction(m_individuals[0].m_genome,GameEvaluator.MACRO_ACTION_LENGTH);
        else return null;
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
