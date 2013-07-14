package controllers.diegoAngledTSP;

import framework.core.Controller;
import framework.core.Game;
import framework.core.Ship;
import framework.core.Waypoint;
import framework.utils.Vector2d;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: diego
 * Date: 26/03/12
 * Time: 16:10
 * To change this template use File | Settings | File Templates.
 */
public class GreedyImprovedController extends Controller
{
    /**
     * Graph for this controller.
     */
    private controllers.heuristic.graph.Graph m_graph;

    /**
     * Node in the graph, closest to the ship position.
     */
    private controllers.heuristic.graph.Node m_shipNode;

    /**
     * Path to the closest waypoint in the map
     */
    private controllers.heuristic.graph.Path m_pathToClosest;

    /**
     * Hash map that matches waypoints in the map with their closest node in the graph.
     */
    private HashMap<Waypoint, controllers.heuristic.graph.Node> m_collectNodes;

    /**
     * Path to the closest waypoint in the map
     */
	private double pathExitAngle = 0;

    /**
     * Closest waypoint to the ship.
     */
    private Waypoint m_closestWaypoint;

	private double maxSpeedAchieved = 0;

	private Vector2d coastStart;
	private Vector2d willCoastTo;
	private Vector2d targSpot;
	private boolean needToStop = false;
	private boolean needToStall = false;
	private double stoppedSpeed = 0.12;
	private double stalledSpeed = 0.3;
	private boolean willBumpStop = false;
	private boolean gotStuck = false;
	private int stuckCount = 0;
	private double SteerStep = 0;
	/*
	 * Projections of movement:
	 */
	private static final int NumPreviews = 9;
	private static final int LEFTTURN = 0;
	private static final int LEFTDRIFTTURN = 1;
	private static final int FULLSPEED = 2;
	private static final int COAST = 3;
	private static final int RIGHTDRIFTTURN = 4;
	private static final int RIGHTTURN = 5;
	private static final int HARDSTOP = 6;
	private static final int HARDTURN = 7;
	private static final int HARDERTURN = 8;

	private Vector2d[][] previews; //= new Vector2d[5][10];
	private int previewLength = 200;
	private int previewPosUsed;
	private int previewHitsGoal[];
	private int previewHitsWall[];

    private controllers.diegoAngledTSP.MCTS m_mcts;



	private boolean togglethrust = false;
    /**
     * Constructor, that receives a copy of the game state
     * @param a_gameCopy a copy of the game state
     */
    public GreedyImprovedController(Game a_gameCopy, long a_timeDue)
    {
        m_mcts = new controllers.diegoAngledTSP.MCTS(a_gameCopy, a_timeDue);
        m_graph = m_mcts.m_graph;

		SteerStep = a_gameCopy.getShip().steerStep;

        //Init the structure that stores the nodes closest to all waypoitns
        m_collectNodes = new HashMap<Waypoint, controllers.heuristic.graph.Node>();
        for(Waypoint way: a_gameCopy.getWaypoints())
        {
            m_collectNodes.put(way, m_graph.getClosestNodeTo(way.s.x, way.s.y,true));
        }

		previews = new Vector2d[NumPreviews][previewLength];
		previewHitsGoal = new int[NumPreviews];
		previewHitsWall = new int[NumPreviews];

        calculateClosestWaypoint(a_gameCopy);
		populateWaypointData(a_gameCopy) ;
		planWaypoints(a_gameCopy);

    }

    /**
     * This function is called every execution step to get the action to execute.
     * @param a_gameCopy Copy of the current game state.
     * @param a_timeDue The time the next move is due
     * @return the integer identifier of the action to execute (see interface framework.core.Controller for definitions)
     */
    public int getAction(Game a_gameCopy, long a_timeDue)
    {
        //Calculate the closest waypoint to the ship.
        //calculateWaypoint(a_gameCopy);
		if (stuckCount > 75) {

			gotStuck = true;
		}
		if (gotStuck && stuckCount > 0) {
			stuckCount--;
			//System.out.println("Emergency Drive: " + stuckCount);
			return ACTION_THR_RIGHT;
		}
		gotStuck = false;

		populatePreviews(a_gameCopy);
        controllers.heuristic.graph.Node oldShipId = m_shipNode;
        m_shipNode = m_graph.getClosestNodeTo(a_gameCopy.getShip().s.x, a_gameCopy.getShip().s.y,true);
        if((oldShipId != m_shipNode || m_pathToClosest == null))
        {
            //Calculate the closest waypoint to the ship.
            calculateClosestWaypoint(a_gameCopy);

            if(m_shipNode == null)
            {
                //No node close enough and collision free. Just go for the closest.
                m_shipNode = m_graph.getClosestNodeTo(a_gameCopy.getShip().s.x, a_gameCopy.getShip().s.y,false);
            }

            //And get the path to it from my location.
            m_pathToClosest = m_graph.getPath(m_shipNode.id(), m_collectNodes.get(m_closestWaypoint).id());
        }

		//We need time to find the new way:
		if (m_closestWaypoint.isCollected()) {
			//Flag the controller:
            m_alive = false;
			return 0;
		}
        //We treat this differently if we can see the waypoint:
        boolean isThereLineOfSight = a_gameCopy.getMap().LineOfSight(a_gameCopy.getShip().s, m_closestWaypoint.s);
        if(isThereLineOfSight)
        {
            return manageStraightTravel(a_gameCopy, m_closestWaypoint.s);
        }

        if(m_pathToClosest != null)  //We should have a path...
        {
			controllers.heuristic.graph.Path pathToClosest = getPathToClosest();
			Vector2d CurPos = a_gameCopy.getShip().s;
			//Node nextNode = getNextNode();
			//Vector2d bestPathPoint = new Vector2d(nextNode.x(),nextNode.y());
			Vector2d bestPathPoint = GetBestVisiblePathPoint(a_gameCopy, CurPos, pathToClosest);

			boolean blocked[][] = new boolean[3][3];
			double xoff = 0, yoff = 0;
			double adjust = 4;
			Ship tShip = a_gameCopy.getShip();

			for (int xp = -1; xp < 2; xp++) {
				for (int yp = -1; yp < 2; yp++) {
					blocked[xp+1][yp+1] = tShip.checkCollisionInPosition(new Vector2d(bestPathPoint.x + xp * adjust,bestPathPoint.y + yp * adjust));
				}
			}
			if (blocked[0][0] || blocked[0][1] || blocked[0][2]) {
				xoff += adjust;
			}
			if (blocked[2][0] || blocked[2][1] || blocked[2][2]) {
				xoff -= adjust;
			}
			if (blocked[0][0] || blocked[1][0] || blocked[2][0]) {
				yoff += adjust;
			}
			if (blocked[0][2] || blocked[1][2] || blocked[2][2]) {
				yoff -= adjust;
			}
			Vector2d offset;
			offset = new Vector2d(xoff, yoff);
			bestPathPoint.add(offset);

			if (targSpot != null && targSpot.equals(bestPathPoint)){
				needToStall = false;
				needToStop = false;
			}


			int bppNodeId = m_graph.getClosestNodeTo(bestPathPoint.x, bestPathPoint.y, true).id();
			controllers.heuristic.graph.Path nextPath = m_graph.getPath(bppNodeId, m_collectNodes.get(m_closestWaypoint).id());
			Vector2d exitPathPoint = GetBestVisiblePathPoint(a_gameCopy, bestPathPoint, nextPath);
			Vector2d dirToWaypoint = bestPathPoint.copy();
			dirToWaypoint.subtract(exitPathPoint);
			dirToWaypoint.rotate(Math.PI);
			pathExitAngle = dirToWaypoint.theta();

			return manageStraightTravel(a_gameCopy, bestPathPoint);
        }

        //Default (something went wrong).
        return Controller.ACTION_NO_FRONT;
    }

    /**
     * Calculates the closest waypoint to the ship.
     * @param a_gameCopy the game copy.
     */
    private void calculateClosestWaypoint(Game a_gameCopy)
    {
        m_closestWaypoint = m_mcts.getNextWaypointInPath(a_gameCopy);
    }

	    /**
     * Returns the first node in the way to the destination
     * @return the node in the way to destination.
     */
    private controllers.heuristic.graph.Node getNextNode()
    {
        controllers.heuristic.graph.Node n0 = m_graph.getNode(m_pathToClosest.m_points.get(0));

        //If only one node in the path, return it.
        if(m_pathToClosest.m_points.size() == 1)
            return n0;

        //Heuristic: Otherwise, take the closest one to the destination
        controllers.heuristic.graph.Node n1 = m_graph.getNode(m_pathToClosest.m_points.get(1));
        controllers.heuristic.graph.Node destination =  m_graph.getNode(m_pathToClosest.m_destinationID);

        if(n0.euclideanDistanceTo(destination) < n1.euclideanDistanceTo(destination))
            return n0;
        else return n1;
    }

	private void populatePreviews(Game a_gameCopy) {

			Vector2d curPos = a_gameCopy.getShip().s;
			Game forThisAction;
            double bestDot = -2;
			boolean toggleThrust = false;
			boolean hadBump = false;
			boolean hasGoal = false;
			int bestAction = 0;
			willBumpStop = false;
            for(int i = 0; i < NumPreviews; ++i) //Our Path types.
            {
				hadBump = false;
				hasGoal = false;
				previewHitsGoal[i] = -1;
				previewHitsWall[i] = -1;
				toggleThrust = false;
				forThisAction = a_gameCopy.getCopy();
				for (int l = 0; l < previewLength; l++) {
					switch (i) {
						case LEFTTURN:  //Always thrust left.
							bestAction = ACTION_THR_LEFT;
							if (l > 300)
								bestAction = ACTION_THR_FRONT;
							break;
						case LEFTDRIFTTURN:  //Half thrust left.
							bestAction = ACTION_THR_LEFT;
							if (toggleThrust)
								bestAction = ACTION_NO_LEFT;
							toggleThrust = !toggleThrust;
							break;
						case FULLSPEED:  //Full Forward
							bestAction = ACTION_THR_FRONT;
							break;
						case COAST:  //Coast
							bestAction = ACTION_NO_FRONT;
							break;
						case RIGHTDRIFTTURN: // Half Right
							bestAction = ACTION_THR_RIGHT;
							if (toggleThrust)
								bestAction = ACTION_NO_RIGHT;
							toggleThrust = !toggleThrust;
							break;
						case RIGHTTURN:  //Full Right
							bestAction = ACTION_THR_RIGHT;
							if (l > 300)
								bestAction = ACTION_THR_FRONT;
							break;
						case HARDSTOP:  //Turn around and go
							//Slow down.
							bestAction = manageHardStop(forThisAction);
							if (!willBumpStop && forThisAction.getShip().getCollLastStep())
								willBumpStop = true;
							break;
						case HARDTURN:
							bestAction = manageHardTurn(forThisAction);
							break;
						case HARDERTURN:
							bestAction = manageHarderTurn(forThisAction);
							break;
						default:
							bestAction = ACTION_NO_FRONT;
							break;
					}
					if (!hadBump && l < 100 && forThisAction.getShip().getCollLastStep()){
						previewHitsWall[i] = l;
						hadBump = true;
					}


					forThisAction.getShip().update(bestAction);
					previews[i][l] = forThisAction.getShip().s.copy();
					if (!hasGoal && targSpot != null && m_closestWaypoint != null && l < 100) {
						hasGoal = (targSpot.dist(previews[i][l]) < m_closestWaypoint.RADIUS);
						if (hasGoal)
							previewHitsGoal[i] = l;

					}

				}
            }
	}

	private double angleDiff(double ang1, double ang2) {
		 double pi = Math.PI;
		 double pi2 = pi*2;
		 double res = 0;
		 //if (ang1 >=0 && ang2 >= 0 || ang1 <=0 && ang2 <= 0) {
			res = Math.abs(ang1-ang2);
		 //}
		 //Eliminate the extra circles.
		 while (res > pi2)
			res -= pi2;
		//Get the shorter arc.
		 if (res > pi)
			res = pi2-res;
		return res;
	}
	private double angleDiffSigned(double ang1, double ang2) {
		 double pi = Math.PI;
		 double pi2 = pi*2;
		 double res = 0;
		 //if (ang1 >=0 && ang2 >= 0 || ang1 <=0 && ang2 <= 0) {
			res = ang1-ang2;
		 //}
		 //Eliminate the extra circles.
		 while (res > pi2)
			res -= pi2;
		 while (res < -pi2)
			res += pi2;

		//Get the shorter arc.
		 if (res > pi)
			res = res - pi2;
		 if (res < -pi)
			res = res + pi2;
		return res;
	}

	/**
     * Manages straight travelling.
     * @param a_gameCopy the game copy
     * @return the id of the best action to execute.
     */
    private int manageStraightTravel(Game a_gameCopy, Vector2d targ)
    {
		targSpot = targ.copy();
        int bestAction = Controller.ACTION_NO_FRONT;
		Vector2d curPos = a_gameCopy.getShip().s;
        Vector2d dirToWaypoint = targ.copy();
        dirToWaypoint.subtract(curPos);
        double distance = dirToWaypoint.mag();
        dirToWaypoint.normalise();

        //Check if we are facing the waypoint we are going after.
        Vector2d dir = a_gameCopy.getShip().d.copy();
		dir.normalise();
		dir.mul(distance);
		dir.add(curPos);
		//if (
        boolean notFacingWaypoint = false;
		double facingAngle = a_gameCopy.getShip().d.theta();
		double needAngle = dirToWaypoint.theta();
		//If the angle is too great, turn the ship more.
		notFacingWaypoint = !((angleDiff(needAngle, facingAngle) <= SteerStep * 1));
        double maxSpeed = 0.4;
        if(distance>100 || a_gameCopy.getStepsLeft() < 50)
            maxSpeed = 0.8;
        else if(distance<30) maxSpeed = 0.25;


		double curSpeed = a_gameCopy.getShip().v.mag();
		if (curSpeed < stoppedSpeed)
		{
			stuckCount++;
		}
		else
			stuckCount = 0;
		if (/*willBumpStop ||*/ curSpeed < stoppedSpeed || a_gameCopy.getShip().getCollLastStep()) {
			needToStop = false;
			needToStall = false;
		}
		if (/*willBumpStop ||*/ distance < 3 || previewHitsGoal[HARDSTOP] == -1) {
			needToStall = false;
		}
		maxSpeedAchieved = Math.max(maxSpeedAchieved, curSpeed);
		//System.out.println(curSpeed + " " + maxSpeedAchieved);

		coastStart = a_gameCopy.getShip().s.copy();
		double coastDistance = curSpeed / (1 - a_gameCopy.getShip().loss);
		maxSpeed = 0.8;
		if(distance>100 || a_gameCopy.getStepsLeft() < 50)
            maxSpeed = Math.max(maxSpeed, 1.4);
        else if(distance<30)
			maxSpeed = stoppedSpeed; //0.25;

		maxSpeed = Math.min(maxSpeed, 2.0);
		if (distance > 100)
			maxSpeed = 10.4;
		else if (distance > 50)
			maxSpeed = 2.0;
		else if (distance > 0)
			maxSpeed = 0.6;

		maxSpeed = 10.4;


		if (needToStall)
			maxSpeed = stalledSpeed;

		maxSpeed = Math.max(maxSpeed, stoppedSpeed);
		willCoastTo = a_gameCopy.getShip().v.copy();
		willCoastTo.normalise();
		willCoastTo.set(willCoastTo.x * coastDistance, willCoastTo.y * coastDistance);
		willCoastTo.add(curPos);

		//We don't have a preview populated, we're confused.
		double bestDist = Double.MAX_VALUE;
		int usefulPreviewLength = Math.min(100, previewLength);
		int bestPathType = 3;
		previewPosUsed = previewLength + 1;
		for(int i = 0; i < NumPreviews; ++i) //Our Path types.
		{
			double bestLineDist = Double.MAX_VALUE;
			int bestLinePos = previewLength + 1;
			if (i == HARDSTOP) continue;
			//if (i == COAST) continue;
			if (i == HARDERTURN && previewHitsWall[i] > 0 && previewHitsWall[i] < 10 /*|| bestPathType == FULLSPEED*/) continue;
			if (i == HARDTURN && (targ.dist(curPos) < 150 || curSpeed < stalledSpeed))
				continue;
			usefulPreviewLength = Math.min(100, previewLength);
			for (int l = 0; l < usefulPreviewLength; l++) {
				double dist = previews[i][l].dist(targ);
				if (dist < bestLineDist) {
					bestLineDist = dist;
					bestLinePos = l;
				}
			}
			if (bestDist > bestLineDist && bestDist > 4.0
				|| bestLineDist <= 4.0 && bestLinePos < previewPosUsed) {
				bestDist = bestLineDist;
				bestPathType = i;
				previewPosUsed = bestLinePos;
			}
		}
		double y1 = 15.0, y2 = 20.0;
		double x2 = 0.4;
		double m = (y1 - y2)/(1.0 - x2);
		double b = y1 - m;
		double catchPreviewDist = m_closestWaypoint.RADIUS;
		double stopFocus = Math.min(Math.max(curSpeed * m + b, y1), 60);

		stopFocus = 28;
		Vector2d slide = a_gameCopy.getShip().v.copy();

		//If we're going to need a hard stop, plan for it.
		if (!needToStop && !needToStall) {
			Vector2d stopSpot = previews[HARDSTOP][previewLength-1];

			int wayVal = findWaypointID(targ);
			double attackAngle = 0;
			int nextVal = -1;
			if (wayVal > -1 && wayVal < numWaypoints) {
				for(int ord = 0; ord < numWaypoints - 1; ord++) {
					if (waypointOrder[ord] == wayVal) {
						nextVal = waypointOrder[ord + 1];
						break;
					}
				}
				if (nextVal > -1 && nextVal < numWaypoints) {
					attackAngle = angleDiff(waypointExitAngle[wayVal][nextVal], slide.theta());
				}
				else {
					attackAngle = angleDiff(pathExitAngle, slide.theta()); ;
				}

			} else {
				nextVal = numWaypoints;
				attackAngle = angleDiff(pathExitAngle, slide.theta()); ;
			}

			//Don't stall on last waypoint.
			if (nextVal > -1 && distance > 40 && previewHitsGoal[HARDSTOP] > 10 && curSpeed > stalledSpeed
				&& attackAngle > SteerStep * 20 ) {
				needToStall = true;
			}

		}

		if (!needToStop /*&& !willBumpStop*/ && curSpeed > x2 && (angleDiff(needAngle, slide.theta()) >= SteerStep * stopFocus)
			&& bestDist > catchPreviewDist && (curPos.dist(targ) >= m_closestWaypoint.RADIUS * 2)) {
			needToStop = true;
		}
		if (needToStall) {
			//System.out.println("Stalling Distance: " + distance + " Speed: " + curSpeed);
			if (curSpeed > maxSpeed)
				bestAction = manageHardStop(a_gameCopy);
			else
				bestAction = ACTION_NO_FRONT;
		}
		else if (needToStop) {
			//System.out.println("Hard Stopping");
			bestAction = manageHardStop(a_gameCopy);
		} else if(curSpeed > maxSpeed || ((/*needToStop ||*/ notFacingWaypoint || curSpeed > maxSpeed) && (bestDist > catchPreviewDist || bestPathType == FULLSPEED)  ))
        {
            double bestDot = -2;
			//Select the action that maximises my dot product with the target (aka. makes the ship face the target better).
			double ang = angleDiffSigned(needAngle, facingAngle);
			//System.out.println("Turning: F:" + facingAngle + " N: " + needAngle + " D:" + ang + " B: " + (needAngle - facingAngle));
			double steerStep = SteerStep;
			if (ang > steerStep)
				bestAction = ACTION_NO_RIGHT;
			else if (ang < -steerStep)
				bestAction = ACTION_NO_LEFT;
			else  //Facing the right way, just too fast.
				bestAction = ACTION_NO_FRONT;

			if (curSpeed > stoppedSpeed && curSpeed < maxSpeed)
				bestAction = manageHarderTurn(a_gameCopy);

        } else { //We can thrust

			if (previews[0][0] == null)
				return ACTION_THR_FRONT;


			switch (bestPathType) {
				case LEFTTURN:  //Always thrust left.
					//System.out.println("Thrust: Left Turn, Speed: " + curSpeed + "");
					bestAction = ACTION_THR_LEFT;
					break;
				case LEFTDRIFTTURN:  //Half thrust left.
					//System.out.println("Thrust: Drift Left Turn, Speed: " + curSpeed + "");
					bestAction = ACTION_THR_LEFT;
					if (a_gameCopy.getShip().turning() != 0)
						bestAction = ACTION_THR_FRONT;
					break;
				case FULLSPEED:  //Full Forward
					//System.out.println("Thrust: Forward, Speed: " + curSpeed + "");
					bestAction = ACTION_THR_FRONT;
					break;
				case COAST:  //Coast
					//System.out.println("Thrust: Coast, Speed: " + curSpeed + "");
					bestAction = ACTION_NO_FRONT;
					break;
				case RIGHTDRIFTTURN: // Half Right
					//System.out.println("Thrust: Drift Right Turn, Speed: " + curSpeed + "");
					bestAction = ACTION_THR_RIGHT;
					if (a_gameCopy.getShip().turning() != 0)
						bestAction = ACTION_THR_FRONT;
					break;
				case RIGHTTURN:  //Full Right
					//System.out.println("Thrust: Right Turn, Speed: " + curSpeed + "");
					bestAction = ACTION_THR_RIGHT;
					break;
				case HARDTURN:  //Complex turning
					//System.out.println("Thrust: Hard Turn, Speed: " + curSpeed + "");
					bestAction = manageHardTurn(a_gameCopy);
					break;
				case HARDERTURN:
					//System.out.println("Thrust: Harder Turn, Speed: " + curSpeed + "");
					bestAction = manageHarderTurn(a_gameCopy);
					break;
			}
		}
        //There we go!
        return bestAction;

    }

	private int manageHardTurn(Game a_gameCopy) {
			Vector2d slide = a_gameCopy.getShip().v.copy();
			Vector2d face = slide.copy();
			if (targSpot == null) return ACTION_NO_FRONT;
			double facingAngle = a_gameCopy.getShip().d.theta();
			double slidingAngle = slide.theta();
			Vector2d dirToWaypoint = targSpot.copy();
			Vector2d curPos = a_gameCopy.getShip().s;
			dirToWaypoint.subtract(curPos);
			double targetAngle = dirToWaypoint.theta();
			double ang = angleDiffSigned(targetAngle, slidingAngle);
			if (ang > 0) {
				//Right Turn
				face.rotate(HALF_PI);
			} else {
				//Left Turn
				face.rotate(-HALF_PI);
			}

			double needAngle = face.theta();

			//Rotate 1/2 circle.
            boolean notFacingCorrectly = !((angleDiff(needAngle, facingAngle) <= SteerStep * 2));// && dir.dot(dirToWaypoint) > 0.90);
			int bestAction = ACTION_NO_FRONT;
			if (!notFacingCorrectly) {
				bestAction = ACTION_THR_FRONT;
			}
			else
            {
				ang = angleDiffSigned(needAngle, facingAngle);
				if (ang > 0)
					bestAction = ACTION_NO_RIGHT;
				else
					bestAction = ACTION_NO_LEFT;
				if (Math.abs(ang) <= SteerStep * 5)
					bestAction += 3;  //Add thrust!
            }
		return bestAction;
	}


	private int manageHarderTurn(Game a_gameCopy) {
			Vector2d slide = a_gameCopy.getShip().v.copy();
			Vector2d face = slide.copy();
			double steerStep = SteerStep;
			if (targSpot == null) return ACTION_NO_FRONT;
			double facingAngle = a_gameCopy.getShip().d.theta();
			double slidingAngle = slide.theta();
			double distance = a_gameCopy.getShip().s.dist(targSpot);
			Vector2d dirToWaypoint = targSpot.copy();
			Vector2d curPos = a_gameCopy.getShip().s;
			dirToWaypoint.subtract(curPos);
			double targetAngle = dirToWaypoint.theta();
			double ang = angleDiffSigned(targetAngle, slidingAngle);
			double ang2 = ang * 2;
			if (ang > 0) {
				ang2 = Math.min(HALF_PI, ang2);
				face.rotate(ang2);
			} else {
				ang2 = Math.max(-HALF_PI, ang2);
				face.rotate(ang2); //(-steerStep);
			}

			double needAngle = face.theta();

            boolean notFacingCorrectly = !((angleDiff(needAngle, facingAngle) <= steerStep * 1.5));
			int bestAction = ACTION_NO_FRONT;
			if (!notFacingCorrectly) {
				bestAction = ACTION_THR_FRONT;
			}
			else
            {
				ang = angleDiffSigned(needAngle, facingAngle);
				if (ang > 0)
					bestAction = ACTION_NO_RIGHT;
				else
					bestAction = ACTION_NO_LEFT;
				if (Math.abs(ang) <= steerStep * 4 && angleDiff(targetAngle, facingAngle) > SteerStep * 4 && distance > 15)
					bestAction += 3;  //Add thrust!

            }
		return bestAction;
	}

	private int manageHardStop(Game a_gameCopy) {
			Vector2d slide = a_gameCopy.getShip().v.copy();
			Vector2d face = slide.copy();
			face.rotate(Math.PI);
			double needAngle = face.theta();
			double facingAngle = a_gameCopy.getShip().d.theta();
			//Rotate 1/2 circle.
			double turnAngle = angleDiff(needAngle, facingAngle);
			double steerStep = SteerStep;
			double curSpeed = slide.mag();
			double coastDistance = curSpeed / (1 - a_gameCopy.getShip().loss);

            boolean notFacingWaypoint = !(turnAngle <= steerStep * 2);
			int bestAction = ACTION_NO_FRONT;
			if (slide.mag() < stoppedSpeed)
				return bestAction;
			if (!notFacingWaypoint) {
				bestAction = ACTION_THR_FRONT;
			}
			else {
				double ang = angleDiffSigned(needAngle, facingAngle);
				if (ang > 0)
					bestAction = ACTION_NO_RIGHT;
				else
					bestAction = ACTION_NO_LEFT;
            }
		return bestAction;
	}

	private Waypoint waypoints[];
	private double waypointDistances[];
	private double waypointDist2[][];
	private double waypointEntryAngle[][];
	private double waypointExitAngle[][];
	private boolean waypointVisibility[][];
	private int numWaypoints;
	private int waypointOrder[];

	private void populateWaypointData(Game a_gameCopy) {


		int cnt = numWaypoints;
		int activeCnt = 0;
		//Initialize
		if (waypoints == null) {
			cnt = a_gameCopy.getWaypoints().size();
			numWaypoints = cnt;
			waypoints = new Waypoint[cnt];
			waypointDistances = new double[cnt];
			waypointDist2 = new double[cnt][cnt];
			waypointEntryAngle = new double[cnt][cnt];
			waypointExitAngle = new double[cnt][cnt];
			waypointVisibility = new boolean[cnt][cnt];
			waypointOrder = new int[cnt];
			cnt = 0;
			for(Waypoint way: a_gameCopy.getWaypoints()) {
				waypoints[cnt] = way.getCopy(a_gameCopy);
				waypointDistances[cnt] = 0;
				waypointOrder[cnt] = -1;
				cnt++;
			}
		}

		for (int first = 0; first < numWaypoints; first++) {
			waypointDistances[first] = 0;
		}

		//Find the total distances from waypoints.
		controllers.heuristic.graph.Path aPath;
		for (int first = 0; first < numWaypoints; first++) {
			if (isWaypointCollected(waypoints[first], a_gameCopy)) continue;
			activeCnt++;
			for (int second = first + 1; second < numWaypoints; second++) {
				if (isWaypointCollected(waypoints[second], a_gameCopy)) continue;
				if (waypointDist2[first][second] == 0) {

					waypointVisibility[first][second] = a_gameCopy.getMap().LineOfSight(waypoints[first].s,waypoints[second].s);
					waypointVisibility[second][first] = waypointVisibility[first][second];
					if (waypointVisibility[first][second]) {  //Easy way

						waypointDist2[first][second] = waypoints[first].s.dist(waypoints[second].s);

						Vector2d dirToWaypoint = waypoints[first].s.copy();
						dirToWaypoint.subtract(waypoints[second].s);
						double TwoToOneAngle = dirToWaypoint.theta();
						dirToWaypoint.rotate(Math.PI);
						double OneToTwoAngle = dirToWaypoint.theta();

						waypointEntryAngle[first][second] = TwoToOneAngle;
						waypointExitAngle[first][second] = OneToTwoAngle;

						waypointEntryAngle[second][first] = OneToTwoAngle;
						waypointExitAngle[second][first] = TwoToOneAngle;


					} else {
						aPath = m_graph.getPath(m_collectNodes.get(waypoints[first]).id(), m_collectNodes.get(waypoints[second]).id());
						waypointDist2[first][second] = aPath.m_cost;

						Vector2d bestPathPoint = GetBestVisiblePathPoint(a_gameCopy, waypoints[first].s, aPath);
						Vector2d dirToWaypoint = waypoints[first].s.copy();
						dirToWaypoint.subtract(bestPathPoint);
						waypointEntryAngle[first][second] = dirToWaypoint.theta();
						dirToWaypoint.rotate(Math.PI);
						waypointExitAngle[first][second] = dirToWaypoint.theta();

						bestPathPoint = GetBestVisiblePathPoint(a_gameCopy, waypoints[second].s, aPath);
						dirToWaypoint = waypoints[second].s.copy();
						dirToWaypoint.subtract(bestPathPoint);
						waypointEntryAngle[second][first] = dirToWaypoint.theta();
						dirToWaypoint.rotate(Math.PI);
						waypointExitAngle[second][first] = dirToWaypoint.theta();

					}

					waypointDist2[second][first] = waypointDist2[first][second];
				}
				waypointDistances[first] += waypointDist2[first][second];
				waypointDistances[second] += waypointDist2[first][second];
			}
		}

		//Calculate average distance from other active waypoints.
		if (activeCnt > 0 )
		for (int first = 0; first < numWaypoints; first++) {
			waypointDistances[first] = waypointDistances[first] / activeCnt;
		}
	}

	private void planWaypoints(Game a_gameCopy) {
		waypointOrder[0] = findWaypointID(m_closestWaypoint);
		double bestDist;
		double bestDist2;
		double bestDist3;
		double bestDist4;
		double thisDist;
		int curw, best1, best2, best3, best4, cnt;
		int bests[] = new int[4];
		for(int w1 = 0; w1 < numWaypoints-1; w1++) {
			bestDist = Double.MAX_VALUE;
			bestDist2 = Double.MAX_VALUE;
			bestDist3 = Double.MAX_VALUE;
			bestDist4 = Double.MAX_VALUE;
			best1 = -1; best2 = -1; best3 = -1; best4 = -1;

			curw = waypointOrder[w1];
			cnt = 0;
			for(int aw = 0; aw < numWaypoints; aw++) {
				if (isWaypointPlanned(aw))
					continue;
				thisDist = waypointDist2[curw][aw];
				//We are the third best.
				if (bestDist4 > thisDist && bestDist3 < thisDist) {
					bestDist4 = thisDist;
					best4 = aw;
					cnt++;
				}
				if (bestDist3 > thisDist && bestDist2 < thisDist) {
					bestDist4 = bestDist3;
					best4 = best3;
					bestDist3 = thisDist;
					best3 = aw;
					cnt++;
				}
				if (bestDist2 > thisDist && bestDist < thisDist) {
					bestDist4 = bestDist3;
					best4 = best3;
					bestDist3 = bestDist2;
					best3 = best2;
					bestDist2 = thisDist;
					best2 = aw;
					cnt++;
				}
				if (bestDist > thisDist) {
					bestDist4 = bestDist3;
					best4 = best3;
					bestDist3 = bestDist2;
					best3 = best2;
					bestDist2 = bestDist;
					best2 = best1;
					bestDist = thisDist;
					best1 = aw;
					cnt++;
				}
			}
			bests[0] = best1;
			bests[1] = best2;
			bests[2] = best3;
			bests[3] = best4;

			//Find the best distance from here, to next, to next, to next;
			cnt = Math.min(4, cnt);
			bestDist = Double.MAX_VALUE;
			best1 = 0;
			//System.out.println(w1);
			boolean angleBoost[] = new boolean[4];
			double boostMod = 0.85, stepMod = 10;
			if (cnt > 2) {
				for (int next = 0; next < cnt; next++) {
					for (int mid = 0; mid < cnt; mid++) {
						if (next == mid) continue;
						for (int last = 0; last < cnt; last++) {
							if (last == mid || last == next) continue;
							angleBoost[0] = false;
							if (w1 > 0)
								angleBoost[0] = angleDiff(waypointEntryAngle[curw][waypointOrder[w1-1]], waypointExitAngle[curw][bests[next]]) < SteerStep * stepMod;
							angleBoost[1] = angleDiff(waypointEntryAngle[bests[next]][curw], waypointExitAngle[bests[next]][bests[mid]]) < SteerStep * stepMod;
							angleBoost[2] = angleDiff(waypointEntryAngle[bests[mid]][bests[next]], waypointExitAngle[bests[mid]][bests[last]]) < SteerStep * stepMod;
							thisDist = waypointDist2[curw][bests[next]] * (angleBoost[0] ? boostMod: 1);
							thisDist += waypointDist2[bests[next]][bests[mid]] * (angleBoost[1] ? boostMod: 1);
							thisDist += waypointDist2[bests[last]][bests[mid]] * (angleBoost[2] ? boostMod: 1);
							if (cnt == 3) {
								//System.out.println(curw + " to " + bests[next] + " to " + bests[mid] + " to " + bests[last] + " is " + thisDist);
								if (thisDist < bestDist) {
									best1 = next;
									bestDist = thisDist;
								}
							} else {
								double savedDist = thisDist;
								for (int ex = 0; ex < cnt; ex++) {
									if (last == mid || last == next || ex == mid || ex == next || ex == last) continue;
									angleBoost[3] = angleDiff(waypointEntryAngle[bests[last]][bests[mid]], waypointExitAngle[bests[last]][bests[ex]]) < SteerStep * stepMod;
									thisDist = savedDist + waypointDist2[bests[last]][bests[ex]] * (angleBoost[3] ? boostMod: 1);
									//System.out.println(curw + " to " + bests[next] + " to " + bests[mid] + " to " + bests[last] + " to " + bests[ex] + " is " + thisDist);
									if (thisDist < bestDist) {
										best1 = next;
										bestDist = thisDist;
									}
								}
							}
						}
					}
				}
				waypointOrder[w1+1]=bests[best1];
			} else if (cnt == 2) {
				for (int next = 0; next < cnt; next++) {
					for (int mid = 0; mid < cnt; mid++) {
						if (next == mid) continue;
						angleBoost[0] = false;
						if (w1 > 0)
							angleBoost[0] = angleDiff(waypointEntryAngle[curw][waypointOrder[w1-1]], waypointExitAngle[curw][bests[next]]) < SteerStep * stepMod;
						angleBoost[1] = angleDiff(waypointEntryAngle[bests[next]][curw], waypointExitAngle[bests[next]][bests[mid]]) < SteerStep * stepMod;
						thisDist = waypointDist2[curw][bests[next]] * (angleBoost[0] ? boostMod: 1);
						thisDist += waypointDist2[bests[next]][bests[mid]] * (angleBoost[1] ? boostMod: 1);
						//System.out.println(curw + " to " + bests[next] + " to " + bests[mid] + " is " + thisDist);
						if (thisDist < bestDist) {
							best1 = next;
							bestDist = thisDist;
						}
					}
				}
				waypointOrder[w1+1]=bests[best1];
			} else {
				waypointOrder[w1+1]=bests[0];
			}


			if (cnt >= 3) {

			}
		}
		thisDist = 0;
		for(int w1 = 0; w1 < numWaypoints-1; w1++) {
			int first = waypointOrder[w1], second = waypointOrder[w1+1];
			thisDist += waypointDist2[first][second];
		}
	}

	private int findWaypointID(Waypoint testWay) {
		if (testWay == null) return -1;
		return findWaypointID(testWay.s);
	}

	private int findWaypointID(Vector2d testWay) {
		if (testWay == null) return -1;
        for(int w = 0; w < numWaypoints; w++)
        {
			Waypoint way = waypoints[w];
			if (way.s.x == testWay.x && way.s.y == testWay.y) {
				return w;
			}
		}
		return -1;
	}


	private boolean isWaypointCollected(Waypoint testWay, Game a_gameCopy) {
		if (testWay == null) return false;
		for(Waypoint way: a_gameCopy.getWaypoints()) {
			if (way.s.x == testWay.s.x && way.s.y == testWay.s.y) {
				return way.isCollected();
			}
		}
		return false;
	}

	private boolean isWaypointPlanned(int testWay) {
		if (testWay < 0 || testWay >= numWaypoints) return false;
        for(int w = 0; w < numWaypoints; w++)
        {
			if (waypointOrder[w] < 0 )
				continue;
			if (testWay == waypointOrder[w]) {
				return true;
			}
		}
		return false;
	}


	public Vector2d GetBestVisiblePathPoint(Game a_gameCopy, Vector2d CurPos, controllers.heuristic.graph.Path pathToClosest) {
		if(pathToClosest == null) return CurPos;

		controllers.heuristic.graph.Node aNode = m_graph.getNode(pathToClosest.m_points.get(0));
		Vector2d bestPathPoint = new Vector2d(aNode.x(), aNode.y());
		double minDistance = 0;
		double dist = 0;

		for(int i = 0; i <pathToClosest.m_points.size()-1 ; i++)
		{
			controllers.heuristic.graph.Node thisNode = m_graph.getNode(pathToClosest.m_points.get(i));
			Vector2d pathp = new Vector2d(thisNode.x(), thisNode.y());
			boolean isThereLineOfSight = a_gameCopy.getMap().LineOfSight(CurPos, pathp);
			dist = CurPos.dist(pathp);
			if(isThereLineOfSight && dist > minDistance)
			{
				bestPathPoint = pathp;
				minDistance = dist;
			}

		}
		return bestPathPoint;
	}


    /**
     * Returns the path to the closest waypoint. (for debugging purposes)
     * @return the path to the closest waypoint
     */
    public controllers.heuristic.graph.Path getPathToClosest() {return m_pathToClosest;}


}
