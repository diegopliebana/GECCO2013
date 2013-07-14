package controllers.simpleGA;

import controllers.diegoAngledTSP.MacroAction;
import controllers.heuristic.GameEvaluator;
import ssamot.utilities.ElapsedCpuTimer;
import fr.inria.optimization.cmaes.CMAEvolutionStrategy;
import fr.inria.optimization.cmaes.CMAEvolutionStrategy.CMAException;
import framework.core.Game;

public class CMARouteFinder {

	CMAEvolutionStrategy cma;
	int dimentions;
	double initialSD;
	double initialMean;
	long intBreakTimeInMillis;
	double bestFitness = Double.POSITIVE_INFINITY;
	double[] bestGnome;
	int lambda = -1;
	int maxLambda = 200;

	private double[] fitness;

	public CMARouteFinder(int dimentions, double d, double e) {
		super();
		this.dimentions = dimentions;
		this.initialSD = d;
		this.initialMean = e;

	}

	public void init() {

		initCma(1);
		lambda = cma.parameters.getPopulationSize();
		clearBest();

	}

	private void initCma(int lambdaMultiplier) {
		cma = new CMAEvolutionStrategy();
		cma.readProperties(); // read options, see file
								// CMAEvolutionStrategy.properties
		cma.options.verbosity = -10;
		// System.out.println(dimentions);
		cma.setDimension(dimentions); // overwrite some loaded properties
		cma.setInitialX(initialSD); // in each dimension, also setTypicalX can
									// be used
		cma.setInitialStandardDeviation(initialMean); // also a mandatory
														// setting
		// cma.options.stopFitness = 1e-14; // optional setting

		if (lambdaMultiplier != 1) {
			int newLabda = lambda * lambdaMultiplier;
			if (newLabda > maxLambda) {
				newLabda = maxLambda;
			}

			cma.parameters.setPopulationSize(newLabda);
		}
		// initialize cma and get fitness array to fill in later
		fitness = cma.init(); // new double[cma.parameters.getPopulationSize()];

	}

	private void clearBest() {
		bestFitness = Double.POSITIVE_INFINITY;
		bestGnome = null;
	}

	public MacroAction run(Game a_gameState, GameEvaluator a_gameEvaluator,
			long intBreakTimeInMillis) {

		// new a CMA-ES and set some initial values
		a_gameEvaluator.updateNextWaypoints(a_gameState, 2);
		ElapsedCpuTimer timer = new ElapsedCpuTimer();
		timer.setMaxTimeMillis(intBreakTimeInMillis-10);

		// initial output to files
		// cma.writeToDefaultFilesHeaders(0); // 0 == overwrites old files
		int gens = 0;
		// iteration loop
		// System.out.println(cma.getDimension());
		while (!timer.exceededMaxTime()) {

			// --- core iteration step ---
			double[][] pop = null;
			try {
				pop = cma.samplePopulation(); // get a new population of
												// solutions
			} catch (CMAException e) {
				initCma(2);
				lambda = cma.parameters.getPopulationSize();
				//System.out.println(lambda);
				pop = cma.samplePopulation();
			}
			for (int i = 0; !timer.exceededMaxTime() && i < pop.length; ++i) { // for each candidate
													// solution i
				// a simple way to handle constraints that define a convex
				// feasible domain
				// (like box constraints, i.e. variable boundaries) via
				// "blind re-sampling"

				CMAIndividual indi = new CMAIndividual(pop[i]);
				// assumes that the feasible domain is convex, the optimum is
				while (!indi.isFeasible(pop[i]))
					// not located on (or very close to) the domain boundary,
					pop[i] = cma.resampleSingle(i); // initialX is feasible and
													// initialStandardDeviations
													// are
													// sufficiently small to
													// prevent quasi-infinite
													// looping here
				// compute fitness/objective value

				fitness[i] = -indi.evaluate(a_gameState, a_gameEvaluator); // fitfun.valueOf()
																			// is
																			// to
																			// be
																			// minimized
				// System.err.println(indi.toString() + ">>>>>>>" + fitness[i]);
			}

            if(!timer.exceededMaxTime())
            {
                cma.updateDistribution(fitness); // pass fitness array to update
                                                    // search distribution
                // --- end core iteration step ---
                gens++;

                // System.out.println(cma.getBestFunctionValue());
                if (bestFitness > cma.getBestFunctionValue()) {
                    bestFitness = cma.getBestFunctionValue();
                    bestGnome = cma.getBestX();
                }
                // output to files and console
                // cma.writeToDefaultFiles();
                // int outmod = 150;
                // if (cma.getCountIter() % (15*outmod) == 1)
                // cma.printlnAnnotation(); // might write file as well
                // if (cma.getCountIter() % outmod == 1)
                // cma.println();
            }
		}

		// final output
		// System.out.println("GENS: " + gens);

		// System.out.println(timer);

		// System.out.println("BEST: ");
		 //for(int i = 0; i < cma.getBestX().length; ++i)
		 //System.out.print( CMAIndividual.geneToAction(cma.getBestX()[i]));
		 //System.out.println();

		int action = CMAIndividual.geneToAction(bestGnome[0]);
		// System.err.println(CMAIndividual.geneToNumberOfMacroActions(cma.getBestX()[0]));
		return new MacroAction(action, GameEvaluator.MACRO_ACTION_LENGTH);

	} // main
}
