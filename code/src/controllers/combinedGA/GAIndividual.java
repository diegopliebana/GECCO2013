package controllers.combinedGA;

import controllers.heuristic.GameEvaluator;
import framework.core.Game;

import java.util.ArrayList;
import java.util.Random;

/**
 * PTSP-Competition
 * Created by Diego Perez, University of Essex.
 * Date: 17/10/12
 */
public class GAIndividual
{
    public ArrayList<Integer> m_genome;
    public double m_fitness;
    //public final double MUTATION_PROB = 0.001;

    public GAIndividual()
    {
        m_fitness = 0;
        m_genome = new ArrayList<Integer>();
    }

    public GAIndividual(ArrayList<MacroAction> a_baseValues)
    {
        m_fitness = 0;
        m_genome = new ArrayList<Integer>();

        for(MacroAction ma : a_baseValues)
        {
            int action = ma.buildAction();
            for(int i = 0; i < GameEvaluator.MACRO_ACTION_LENGTH; ++i)
                 m_genome.add(action);
        }

    }

    public void randomize(Random a_rnd, int a_numActions, int a_limit)
    {
        for(int i = 0; i < a_limit; ++i)
        {
            Integer action = a_rnd.nextInt(a_numActions);
            m_genome.set(i,action);
        }
    }

    public void evaluate(Game a_gameState, GameEvaluator a_gameEvaluator)
    {
        Game thisGameCopy = a_gameState.getCopy();
        for(int i = 0; i < m_genome.size(); ++i)
        {
            int thisAction = m_genome.get(i);
            //for(int j =0; j < GameEvaluator.MACRO_ACTION_LENGTH; ++j)
            thisGameCopy.tick(thisAction);
        }
        m_fitness = a_gameEvaluator.scoreGame(thisGameCopy);
    }

    public void mutate(Random a_rnd, int a_limit)
    {
        for (int i = 0; i < a_limit; i++) {
            if(a_rnd.nextDouble() < 0.5)
            {
                Integer action = m_genome.get(i);
                if(a_rnd.nextDouble() < 0.5)  //mutate thrust
                    action = MacroAction.mutateThrust(action);
                else  //mutate steering
                    action = MacroAction.mutateSteer(action, a_rnd.nextDouble()>0.5);
                m_genome.set(i,action);
            }

        }

        /*int gene = a_rnd.nextInt(a_limit);
        Integer action = m_genome.get(gene);
        if(a_rnd.nextDouble() < 0.5)  //mutate thrust
            action = MacroAction.mutateThrust(action);
        else  //mutate steering
            action = MacroAction.mutateSteer(action, a_rnd.nextDouble()>0.5);
        m_genome.set(gene,action);  */
    }

    public GAIndividual copy()
    {
        GAIndividual gai = new GAIndividual();
        for(int i = 0; i < this.m_genome.size(); ++i)
        {
            gai.m_genome.add(this.m_genome.get(i));
        }
        return gai;
    }

    public String toString()
    {
        String st = new String();
        for(int i = 0; i < m_genome.size(); ++i)
            st += this.m_genome.get(i);
        return st + ": " + m_fitness;
    }


}
