package controllers.diegoAngledTSP;

import framework.core.Controller;
import framework.core.Game;
import framework.core.Waypoint;
import framework.utils.Vector2d;

import java.awt.*;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: diego
 * Date: 26/03/12
 * Time: 16:10
 * To change this template use File | Settings | File Templates.
 */
public class GreedyController extends Controller
{

    private controllers.diegoAngledTSP.MCTS m_mcts;
    private Vector2d[] m_waypointLocations;
    private Game m_game;

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
     * Closest waypoint to the ship.
     */
    private Waypoint m_closestWaypoint;


    public GreedyController(Game a_gameCopy, long a_timeDue)
    {
        m_mcts = new controllers.diegoAngledTSP.MCTS(a_gameCopy, a_timeDue);
        m_graph = m_mcts.m_graph;
        m_waypointLocations = new Vector2d[a_gameCopy.getWaypoints().size()];
        for(int i = 0; i < m_waypointLocations.length; ++i)
            m_waypointLocations[i] = a_gameCopy.getWaypoints().get(i).s.copy();

        //Init the structure that stores the nodes closest to all waypoitns
        m_collectNodes = new HashMap<Waypoint, controllers.heuristic.graph.Node>();
        for(Waypoint way: a_gameCopy.getWaypoints())
        {
            m_collectNodes.put(way, m_graph.getClosestNodeTo(way.s.x, way.s.y));
        }

    }

    public int getAction(Game a_gameCopy, long a_timeDue)
    {
        //Get the path to the closest node, if my ship moved.
        controllers.heuristic.graph.Node oldShipId = m_shipNode;
        m_shipNode = m_graph.getClosestNodeTo(a_gameCopy.getShip().s.x, a_gameCopy.getShip().s.y);
        if(oldShipId != m_shipNode || m_pathToClosest == null)
        {
            //Calculate the closest waypoint to the ship.
            calculateClosestWaypoint(a_gameCopy);

            if(m_shipNode == null)
            {
                //No node close enough and collision free. Just go for the closest.
                m_shipNode = m_graph.getClosestNodeTo(a_gameCopy.getShip().s.x, a_gameCopy.getShip().s.y);
            }

            //And get the path to it from my location.
            m_pathToClosest = m_graph.getPath(m_shipNode.id(), m_collectNodes.get(m_closestWaypoint).id());
        }

       /* m_mcts.m_currentGameCopy = a_gameCopy;
        int stuckAction = m_mcts.manageStuckLoS(m_closestWaypoint); //manageStuckNaive();
        if(stuckAction != -1)
        {
            return stuckAction;
        }        */

        //Wanna know the distance to the closest obstacle ahead of the ship? Try:
        //double distanceToColl = a_gameCopy.getMap().distanceToCollision(a_gameCopy.getShip().s, a_gameCopy.getShip().d, 1000);
        //System.out.println("DIST: " + distanceToColl);

        //We treat this differently if we can see the waypoint:
        boolean isThereLineOfSight = a_gameCopy.getMap().LineOfSight(a_gameCopy.getShip().s,m_closestWaypoint.s);
        if(isThereLineOfSight)
        {
            return manageStraightTravel(a_gameCopy);
        }

        //The waypoint is behind an obstacle, select which is the best action to take.
        double minDistance = Float.MAX_VALUE;
        int bestAction = -1;
        double bestDot = -2;

        if(m_pathToClosest != null)  //We should have a path...
        {
            int startAction = Controller.ACTION_NO_FRONT;
            //For each possible action...
            for(int action = startAction; action < Controller.NUM_ACTIONS; ++action)
            {
                //Simulate that we execute the action and get my potential position and direction
                Game forThisAction = a_gameCopy.getCopy();
                forThisAction.getShip().update(action);
                Vector2d nextPosition = forThisAction.getShip().s;
                Vector2d potentialDirection = forThisAction.getShip().d;

                //Get the next node to go to, from the path to the closest waypoint
                controllers.heuristic.graph.Node nextNode = getNextNode();
                Vector2d nextNodeV = new Vector2d(nextNode.x(),nextNode.y());
                nextNodeV.subtract(nextPosition);
                nextNodeV.normalise();   //This is a unit vector from my position pointing towards the next node to go to.
                double dot = potentialDirection.dot(nextNodeV);  //Dot product between this vector and where the ship is facing to.

                //Get the distance to the next node in the tree and update the total distance until the closest waypoint
                double dist = nextNode.euclideanDistanceTo(nextPosition.x, nextPosition.y);
                double totalDistance = m_pathToClosest.m_cost + dist;

                //System.out.format("Action: %d, total distance: %.3f, distance to node: %.3f, dot: %.3f\n",action, totalDistance, dist, dot);

                //Keep the best action so far.
                if(totalDistance < minDistance)
                {
                    minDistance = totalDistance;
                    bestAction = action;
                    bestDot = dot;
                }
                //If the distance is the same, keep the action that faces the ship more towards the next node
                else if((int)totalDistance == (int)minDistance && dot > bestDot)
                {
                    minDistance = totalDistance;
                    bestAction = action;
                    bestDot = dot;
                }
            }

            //This is the best action to take.
            return bestAction;
        }

        //Default (something went wrong).
        return Controller.ACTION_NO_FRONT;
    }


    /**
     * Manages straight travelling.
     * @param a_gameCopy the game copy
     * @return the id of the best action to execute.
     */
    private int manageStraightTravel(Game a_gameCopy)
    {
        int bestAction = Controller.ACTION_NO_FRONT;
        Vector2d dirToWaypoint = m_closestWaypoint.s.copy();
        dirToWaypoint.subtract(a_gameCopy.getShip().s);
        double distance = dirToWaypoint.mag();
        dirToWaypoint.normalise();

        //Check if we are facing the waypoint we are going after.
        Vector2d dir = a_gameCopy.getShip().d;
        boolean notFacingWaypoint = dir.dot(dirToWaypoint) < 0.85;

        //Depending on the time left and the distance to the waypoint, we established the maximum speed.
        //(going full speed could make the ship to overshoot the waypoint... that's the reason of this method!).
        double maxSpeed = 0.4;
        if(distance>100 || a_gameCopy.getStepsLeft() < 50)
            maxSpeed = 0.8;
        else if(distance<30) maxSpeed = 0.25;


        if(notFacingWaypoint || a_gameCopy.getShip().v.mag() > maxSpeed)
        {
            //We should not risk to throttle. Let's rotate in place to face the waypoint better.
            Game forThisAction;
            double bestDot = -2;
            for(int i = Controller.ACTION_NO_FRONT; i <= Controller.ACTION_NO_RIGHT; ++i)
            {
                //Select the action that maximises my dot product with the target (aka. makes the ship face the target better).
                forThisAction = a_gameCopy.getCopy();
                forThisAction.getShip().update(i);
                Vector2d potentialDirection = forThisAction.getShip().d;
                double newDot = potentialDirection.dot(dirToWaypoint);
                if(newDot > bestDot)
                {
                    bestDot = newDot;
                    bestAction = i;
                }
            }
        } else //We can thrust
            return Controller.ACTION_THR_FRONT;

        //There we go!
        return bestAction;
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

    /**
     * Calculates the closest waypoint to the ship.
     * @param a_gameCopy the game copy.
     */
    private void calculateClosestWaypoint(Game a_gameCopy)
    {
        m_closestWaypoint = m_mcts.getNextWaypointInPath(a_gameCopy);
    }


    /**
     * This is a debug function that can be used to paint on the screen.
     * @param a_gr Graphics device to paint.
     */
    public void paint(Graphics2D a_gr)
    {
        //m_mcts.m_graph.draw(a_gr);
        a_gr.setColor(Color.yellow);
        controllers.heuristic.graph.Path pathToClosest = m_mcts.m_path;
        if(pathToClosest != null) for(int i = 0; i < pathToClosest.m_points.size()-1; ++i)
        {
            controllers.heuristic.graph.Node thisNode = m_mcts.m_graph.getNode(pathToClosest.m_points.get(i));
            controllers.heuristic.graph.Node nextNode = m_mcts.m_graph.getNode(pathToClosest.m_points.get(i+1));
            a_gr.drawLine(thisNode.x(), thisNode.y(), nextNode.x(),nextNode.y());
        }

        //PAINT ROUTE ORDER:
        /*a_gr.setColor(Color.yellow);
        int[] bestRoute = m_mcts.m_bestRoute;
        if(bestRoute != null)
        {
            for(int i = 0; i < bestRoute.length; ++i)
            {
                int waypointIndex = bestRoute[i];
                Vector2d thisWayPos = m_waypointLocations[waypointIndex];
                a_gr.drawString(""+i, (int)thisWayPos.x+5, (int)thisWayPos.y+5);
                if(i>0)
                {
                    int waypointLastIndex = bestRoute[i-1];
                    Node org = m_mcts.m_graph.getClosestNodeTo(m_waypointLocations[waypointLastIndex].x, m_waypointLocations[waypointLastIndex].y);
                    Node dest = m_mcts.m_graph.getClosestNodeTo(m_waypointLocations[waypointIndex].x, m_waypointLocations[waypointIndex].y);
                    Path p = m_mcts.m_graph.getPath(org.id(), dest.id());
                    if(p != null) for(int k = 0; k < p.m_points.size()-1; ++k)
                    {
                        Node thisNode = m_mcts.m_graph.getNode(p.m_points.get(k));
                        Node nextNode = m_mcts.m_graph.getNode(p.m_points.get(k+1));
                        a_gr.drawLine(thisNode.x(), thisNode.y(), nextNode.x(),nextNode.y());

                        a_gr.setColor(Color.green);
                        ArrayList<Integer> midPoints = m_mcts.m_tspGraph.m_distSight[waypointLastIndex][waypointIndex].getOrder();
                        for(int m = 0; m < midPoints.size(); ++m)
                        {
                            int thisPoint = midPoints.get(m);
                            Node n = m_mcts.m_graph.getNode(p.m_points.get(thisPoint));
                            Vector2d nodePos = new Vector2d(n.x(), n.y());
                            a_gr.fillOval((int) nodePos.x, (int) nodePos.y, 5, 5);
                        }
                        a_gr.setColor(Color.white);
                    }
                }
            }

            a_gr.setColor(Color.green);
            Node org = m_mcts.m_graph.getClosestNodeTo(m_waypointLocations[bestRoute[2]].x, m_waypointLocations[bestRoute[2]].y);
            Node dest = m_mcts.m_graph.getClosestNodeTo(m_waypointLocations[bestRoute[3]].x, m_waypointLocations[bestRoute[3]].y);
            Path p = m_mcts.m_graph.getPath(org.id(), dest.id());
            ArrayList<Integer> midPoints = m_mcts.m_tspGraph.m_distSight[bestRoute[2]][bestRoute[3]].getOrder();
            for(int m = 0; m < midPoints.size(); ++m)
            {
                int thisPoint = midPoints.get(m);
                Node n = m_mcts.m_graph.getNode(p.m_points.get(thisPoint));
                Vector2d nodePos = new Vector2d(n.x(), n.y());
                a_gr.fillOval((int) nodePos.x, (int) nodePos.y, 5, 5);
            }
        }
 */
        //PAINT TREE SEARCH:
       // paintHeightMap(a_gr);

        //Paint the best MCTS route
        /*if(m_mcts.m_bestRouteSoFar != null && m_mcts.m_bestRouteSoFar.size()>0)
        {
            a_gr.setColor(Color.red);
            for(int i = 0; i < m_mcts.m_bestRouteSoFar.size() - 1; ++i)
            {
                Vector2d a = m_mcts.m_bestRouteSoFar.get(i);
                Vector2d b = m_mcts.m_bestRouteSoFar.get(i+1);
                a_gr.drawLine((int)Math.round(a.x),(int)Math.round(a.y),(int)Math.round(b.x),(int)Math.round(b.y));
            }
            //System.out.println("BEST ROUTE: " + m_mcts.m_bestRouteSoFar.size() + " starting with: " + m_mcts.m_bestActions.get(0));
        } */

        //Simulate for actions:
       /* if(m_mcts.m_bestActions != null && m_mcts.m_bestActions.size()>0)
        {
            a_gr.setColor(Color.yellow);
            ArrayList<Vector2d> followed = new ArrayList<Vector2d>();
            followed.add(m_game.getShip().s.copy());
            for(int i = 0; i < m_mcts.m_bestActions.size(); ++i)
            {
                m_game.tick(m_mcts.m_bestActions.get(i));
                followed.add(m_game.getShip().s.copy());
            }

            for(int i = 0; i < followed.size() - 1; ++i)
            {
                Vector2d a = followed.get(i);
                Vector2d b = followed.get(i+1);
                a_gr.drawLine((int)Math.round(a.x),(int)Math.round(a.y),(int)Math.round(b.x),(int)Math.round(b.y));
            }
            //System.out.println("BEST ROUTE: " + m_mcts.m_bestRouteSoFar.size() + " starting with: " + m_mcts.m_bestActions.get(0));
        } */





    }

    private void paintHeightMap(Graphics2D a_gr)
    {
        for(int i = 0; i < m_mcts.m_heightMap.length; ++i)
        {
            for(int j = 0; j < m_mcts.m_heightMap[0].length; ++j)
            {
                int height = m_mcts.m_heightMap[i][j];

                if(height > 0)
                {
                    Color col = getColorByHeight(height);
                    a_gr.setColor(col);
                    a_gr.fillRect(i,j,1,1);
                    //System.out.print(height + " ");
                }

            }
        }
    }

    private Color getColorByHeight(int height)
    {
        if(false)
        {
            if(height < 4)
                return new Color(218,15,240);  //PINK
            else if(height < 10)
                return new Color(11,14,241);   //PURPLE
            else if(height < 20)
                return new Color(13,53,242);    //BLUE
            else if(height < 30)
                return new Color(12,220,243);   //CYAN
            else if(height < 40)
                return new Color(63,245,10);    //GREEN
            else if(height < 60)
                return new Color(234,245,10);   //YELLOW
            else if(height < 80)
                return new Color(245,104,10);   //ORANGE

            return Color.red;                   //RED         */
        }else{
            if(height < 4)
                return Color.black;
            else if(height < 10)
                return new Color(50,50,50);
            else if(height < 20)
                return new Color(90,90,90);
            else if(height < 30)
                return new Color(140,140,140);
            else if(height < 40)
                return new Color(190,190,190);
            else if(height < 60)
                return new Color(210,210,210);
            else if(height < 80)
                return new Color(225,225,225);

            return new Color(250,250,250); //almost Color.white
        }
    }
    
}

