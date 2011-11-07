package de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.query;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2011
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.lmu.ifi.dbs.elki.data.spatial.SpatialComparable;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.DistanceResultPair;
import de.lmu.ifi.dbs.elki.database.query.DoubleDistanceResultPair;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.AbstractDistanceKNNQuery;
import de.lmu.ifi.dbs.elki.distance.distancefunction.SpatialPrimitiveDoubleDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.index.tree.DirectoryEntry;
import de.lmu.ifi.dbs.elki.index.tree.LeafEntry;
import de.lmu.ifi.dbs.elki.index.tree.query.DoubleDistanceSearchCandidate;
import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialEntry;
import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.AbstractRStarTree;
import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.AbstractRStarTreeNode;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.Heap;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.KNNHeap;

/**
 * Instance of a KNN query for a particular spatial index.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses AbstractRStarTree
 * @apiviz.uses SpatialPrimitiveDoubleDistanceFunction
 */
public class DoubleDistanceRStarTreeKNNQuery<O extends SpatialComparable> extends AbstractDistanceKNNQuery<O, DoubleDistance> {
  /**
   * The index to use
   */
  protected final AbstractRStarTree<?, ?> tree;

  /**
   * Spatial primitive distance function
   */
  protected final SpatialPrimitiveDoubleDistanceFunction<? super O> distanceFunction;

  /**
   * Constructor.
   * 
   * @param tree Index to use
   * @param distanceQuery Distance query to use
   * @param distanceFunction Distance function
   */
  public DoubleDistanceRStarTreeKNNQuery(AbstractRStarTree<?, ?> tree, DistanceQuery<O, DoubleDistance> distanceQuery, SpatialPrimitiveDoubleDistanceFunction<? super O> distanceFunction) {
    super(distanceQuery);
    this.tree = tree;
    this.distanceFunction = distanceFunction;
  }

  /**
   * Performs a k-nearest neighbor query for the given NumberVector with the
   * given parameter k and the according distance function. The query result is
   * in ascending order to the distance to the query object.
   * 
   * @param object the query object
   * @param knnList the knn list containing the result
   */
  protected void doKNNQuery(O object, KNNHeap<DoubleDistance> knnList) {
    final Heap<DoubleDistanceSearchCandidate> pq = new Heap<DoubleDistanceSearchCandidate>(Math.min(knnList.getK() * 2, 20));

    // push root
    pq.add(new DoubleDistanceSearchCandidate(0.0, tree.getRootID()));
    double maxDist = Double.MAX_VALUE;

    // search in tree
    while(!pq.isEmpty()) {
      DoubleDistanceSearchCandidate pqNode = pq.poll();

      if(pqNode.mindist > maxDist) {
        return;
      }
      maxDist = expandNode(object, knnList, pq, maxDist, pqNode.nodeID);
    }
  }

  private double expandNode(O object, KNNHeap<DoubleDistance> knnList, final Heap<DoubleDistanceSearchCandidate> pq, double maxDist, final Integer nodeID) {
    AbstractRStarTreeNode<?, ?> node = tree.getNode(nodeID);
    // data node
    if(node.isLeaf()) {
      for(int i = 0; i < node.getNumEntries(); i++) {
        SpatialEntry entry = node.getEntry(i);
        double distance = distanceFunction.doubleMinDist(entry, object);
        tree.distanceCalcs++;
        if(distance <= maxDist) {
          knnList.add(new DoubleDistanceResultPair(distance, ((LeafEntry) entry).getDBID()));
          maxDist = knnList.getKNNDistance().doubleValue();
        }
      }
    }
    // directory node
    else {
      for(int i = 0; i < node.getNumEntries(); i++) {
        SpatialEntry entry = node.getEntry(i);
        double distance = distanceFunction.doubleMinDist(entry, object);
        tree.distanceCalcs++;
        // Greedy expand, bypassing the queue
        if(distance <= 0) {
          expandNode(object, knnList, pq, maxDist, ((DirectoryEntry) entry).getPageID());
        }
        else {
          if(distance <= maxDist) {
            pq.add(new DoubleDistanceSearchCandidate(distance, ((DirectoryEntry) entry).getPageID()));
          }
        }
      }
    }
    return maxDist;
  }

  /**
   * Performs a batch knn query.
   * 
   * @param node the node for which the query should be performed
   * @param knnLists a map containing the knn lists for each query objects
   */
  protected void batchNN(AbstractRStarTreeNode<?, ?> node, Map<DBID, KNNHeap<DoubleDistance>> knnLists) {
    if(node.isLeaf()) {
      for(int i = 0; i < node.getNumEntries(); i++) {
        SpatialEntry p = node.getEntry(i);
        for(Entry<DBID, KNNHeap<DoubleDistance>> ent : knnLists.entrySet()) {
          final DBID q = ent.getKey();
          final KNNHeap<DoubleDistance> knns_q = ent.getValue();
          DoubleDistance knn_q_maxDist = knns_q.getKNNDistance();

          DBID pid = ((LeafEntry) p).getDBID();
          // FIXME: objects are NOT accessible by DBID in a plain rtree context!
          DoubleDistance dist_pq = distanceFunction.distance(relation.get(pid), relation.get(q));
          tree.distanceCalcs++;
          if(dist_pq.compareTo(knn_q_maxDist) <= 0) {
            knns_q.add(dist_pq, pid);
          }
        }
      }
    }
    else {
      ModifiableDBIDs ids = DBIDUtil.newArray(knnLists.size());
      ids.addAll(knnLists.keySet());
      List<DoubleDistanceEntry> entries = getSortedEntries(node, ids);
      for(DoubleDistanceEntry distEntry : entries) {
        double minDist = distEntry.distance;
        for(Entry<DBID, KNNHeap<DoubleDistance>> ent : knnLists.entrySet()) {
          final KNNHeap<DoubleDistance> knns_q = ent.getValue();
          double knn_q_maxDist = knns_q.getKNNDistance().doubleValue();

          if(minDist <= knn_q_maxDist) {
            SpatialEntry entry = distEntry.entry;
            AbstractRStarTreeNode<?, ?> child = tree.getNode(((DirectoryEntry) entry).getPageID());
            batchNN(child, knnLists);
            break;
          }
        }
      }
    }
  }

  /**
   * Sorts the entries of the specified node according to their minimum distance
   * to the specified objects.
   * 
   * @param node the node
   * @param ids the id of the objects
   * @return a list of the sorted entries
   */
  protected List<DoubleDistanceEntry> getSortedEntries(AbstractRStarTreeNode<?, ?> node, DBIDs ids) {
    List<DoubleDistanceEntry> result = new ArrayList<DoubleDistanceEntry>();

    for(int i = 0; i < node.getNumEntries(); i++) {
      SpatialEntry entry = node.getEntry(i);
      double minMinDist = Double.MAX_VALUE;
      for(DBID id : ids) {
        double minDist = distanceFunction.doubleMinDist(entry, relation.get(id));
        tree.distanceCalcs++;
        minMinDist = Math.min(minDist, minMinDist);
      }
      result.add(new DoubleDistanceEntry(entry, minMinDist));
    }

    Collections.sort(result);
    return result;
  }

  /**
   * Optimized double distance entry implementation.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.hidden
   */
  class DoubleDistanceEntry implements Comparable<DoubleDistanceEntry> {
    /**
     * Referenced entry
     */
    SpatialEntry entry;

    /**
     * Distance value
     */
    double distance;

    /**
     * Constructor.
     * 
     * @param entry Entry
     * @param distance Distance
     */
    public DoubleDistanceEntry(SpatialEntry entry, double distance) {
      this.entry = entry;
      this.distance = distance;
    }

    @Override
    public int compareTo(DoubleDistanceEntry o) {
      return Double.compare(this.distance, o.distance);
    }
  }

  @Override
  public List<DistanceResultPair<DoubleDistance>> getKNNForObject(O obj, int k) {
    if(k < 1) {
      throw new IllegalArgumentException("At least one enumeration has to be requested!");
    }

    final KNNHeap<DoubleDistance> knnList = new KNNHeap<DoubleDistance>(k, distanceFunction.getDistanceFactory().infiniteDistance());
    doKNNQuery(obj, knnList);
    return knnList.toSortedArrayList();
  }

  @Override
  public List<DistanceResultPair<DoubleDistance>> getKNNForDBID(DBID id, int k) {
    return getKNNForObject(relation.get(id), k);
  }

  @Override
  public List<List<DistanceResultPair<DoubleDistance>>> getKNNForBulkDBIDs(ArrayDBIDs ids, int k) {
    if(k < 1) {
      throw new IllegalArgumentException("At least one enumeration has to be requested!");
    }

    // While this works, it seems to be slow at least for large sets!
    final Map<DBID, KNNHeap<DoubleDistance>> knnLists = new HashMap<DBID, KNNHeap<DoubleDistance>>(ids.size());
    for(DBID id : ids) {
      knnLists.put(id, new KNNHeap<DoubleDistance>(k, distanceFunction.getDistanceFactory().infiniteDistance()));
    }

    batchNN(tree.getRoot(), knnLists);

    List<List<DistanceResultPair<DoubleDistance>>> result = new ArrayList<List<DistanceResultPair<DoubleDistance>>>();
    for(DBID id : ids) {
      result.add(knnLists.get(id).toSortedArrayList());
    }
    return result;
  }

  @Override
  public void getKNNForBulkHeaps(Map<DBID, KNNHeap<DoubleDistance>> heaps) {
    AbstractRStarTreeNode<?, ?> root = tree.getRoot();
    batchNN(root, heaps);
  }

  @Override
  public DoubleDistance getDistanceFactory() {
    return distanceQuery.getDistanceFactory();
  }
}