package controllers.simpleGA;

import controllers.diegoAngledTSP.MacroAction;
import controllers.heuristic.GameEvaluator;
import framework.core.Game;

import java.util.Arrays;
import java.util.Random;

/**
 * PTSP-Competition
 * Created by Diego Perez, University of Essex.
 * Date: 17/10/12
 */
public class CMAIndividual
{
    private static int min = 0;
	public double[] m_genome;


    public CMAIndividual(double[] genome)
    {
        m_genome = genome;

    }

  

    public double evaluate(Game a_gameState, GameEvaluator a_gameEvaluator)
    {
        Game thisGameCopy = a_gameState.getCopy();
        //int macro_action_length = geneToNumberOfMacroActions(m_genome[0]);
        
        if(thisGameCopy.getWaypointsLeft() == 1) {
        	//min = 3;
        	//System.out.println("One way point left");
        }

        boolean end = false;
        for(int i = 0; i < m_genome.length; ++i)
        {
            int thisAction = geneToAction(m_genome[i]);
            
            //System.err.print(thisAction + ", ");
            for(int j = 0; !end && j < geneToNumberOfMacroActions(m_genome[0]); ++j)
            {
                thisGameCopy.tick(thisAction);
                //end = thisGameCopy.isEnded();
                end = a_gameEvaluator.isEndGame(thisGameCopy);
            }
        }
        //System.err.println(">> Actions");
        return a_gameEvaluator.scoreGame(thisGameCopy);
    }

  
    public static int geneToAction(double gene) {
    	int thisAction = (int) Math.abs(gene) + min;
        
        if (thisAction > 5) {
        	thisAction = 5;
        }
        return thisAction;
    }
    
    public static int geneToNumberOfMacroActions(double gene) {
//    	int thisAction = (int) Math.abs(gene) + 2;
//        
//        if (thisAction > 15) {
//        	thisAction = 15;
//        }
//        return thisAction;
    	
    	return GameEvaluator.MACRO_ACTION_LENGTH;
    }

    public String toString()
    {
        return Arrays.toString(m_genome);
    }

	public boolean isFeasible(double[] ds) {
//		for (int i = 0; i < ds.length; i++) {
//			if(ds[i] < 0 || ds[i] > 6) {
//				return false;
//			}
//		}
		return true;
	}


}
