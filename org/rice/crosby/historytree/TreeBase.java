package org.rice.crosby.historytree;

import org.rice.crosby.historytree.generated.Serialization;

public abstract class TreeBase<A,V> {

  /** Make an history at a given timestamp (used as a template for building a pruned trees or parsing trees.)
   */
  public TreeBase<A, V> updateTime(int time) {
  	this.time = time;
  	datastore.updateTime(time);
  	return this;
  }

  protected void reparent(int time) {
  	while (!(time <= (1<<root.layer)-1))
  		this.root = root.reparent();
  }

  public AggregationInterface<A,V> getAggObj() {
  	return aggobj.clone();
  }

  public int version() {
  	return time;
  }

  public String toString(String prefix) {
  	StringBuilder b = new StringBuilder();
  	b.append(prefix);
  	b.append("  version = ");
  	b.append(time);
  	b.append("\n");
  	debugString(b,prefix,root);
  	return new String(b);
  }

  public String toString() {
  	return toString("");
  }

  public void debugString(StringBuilder b, String prefix, NodeCursor<A,V> node) {
  	b.append(prefix);
  	b.append("\t"+toString(node));
  	b.append("\n");
  
  	if (node.isLeaf())
  		return;
  		
  	NodeCursor<A,V> left=node.left(), right=node.right();
  	
  	if (left != null)
  		debugString(b,prefix+"L",left);
  	if (right != null) 
  		debugString(b,prefix+"R",right);
  }

  /** Return this as a longer tab-delimited string */
  public String toString(NodeCursor<A,V> node) {
  	StringBuilder b=new StringBuilder();
  	b.append(String.format("<%d,%d>",node.layer,node.index));
  	b.append("\t");
  	// Print out the value (if any)
  	
  	if (node.isLeaf() && node.hasVal())
  		b.append(valToString(node.getVal()));
  		else
  			b.append("<>");
  			
  	b.append("\t");	
  
  	if (node.isAggValid()) {
  		A agg = node.getAgg();
  		if (agg == null)
  			b.append("Null");
  		else
  			b.append(aggToString(agg));
  	} else
  		b.append("<>");
  	//b.append(":");
  	//b.append(datastore.hashCode());
  	return b.toString();
  }

  protected String aggToString(A a) {
  	return aggobj.serializeAgg(a).toStringUtf8();
  }

  protected String valToString(V v) {
  	return aggobj.serializeVal(v).toStringUtf8();
  }

  /** Serialize a pruned tree to a protocol buffer */
  public void serializeTree(Serialization.HistTree.Builder out) {
  	out.setVersion(time);
  	if (root != null) {
  		Serialization.HistNode.Builder builder = Serialization.HistNode.newBuilder();
  		serializeNode(builder,root);
  		out.setRoot(builder.build());
  	}
  }

  public byte[] serializeTree() {
  	Serialization.HistTree.Builder builder= Serialization.HistTree.newBuilder();
  	serializeTree(builder);
  	return builder.build().toByteArray();
  }

  /** Helper function for recursively serializing a history tree */
  private void serializeNode(Serialization.HistNode.Builder out, NodeCursor<A,V> node) {
  	if (node.isLeaf()) {
  		//System.out.println("SN:"+node);
  		if (node.hasVal())
  			out.setVal(aggobj.serializeVal(node.getVal()));
  		else
  			out.setAgg(aggobj.serializeAgg(node.getAgg()));
  		return;
  	}
  	if (node.left() == null && node.right() == null) {
  		// Either a stub or a leaf. 
  		// Gotta include the agg for this node.
  		out.setAgg(aggobj.serializeAgg(node.getAgg()));
  		return;
  	}
  	// Ok, recurse both sides. Don't forget, we need to make a builder.
  	if (node.left() != null) {
  		Serialization.HistNode.Builder b = Serialization.HistNode.newBuilder();
  		serializeNode(b,node.left());
  		out.setLeft(b.build());
  	}
  	if (node.right() != null) {
  		Serialization.HistNode.Builder b = Serialization.HistNode.newBuilder();
  		serializeNode(b,node.right());
  		out.setRight(b.build());
  	}
  }

  /** Make a cursor pointing to the given leaf, if possible */
  protected NodeCursor<A,V> leaf(int version) {
  	if (time == 0)
  		return root;
  	NodeCursor<A,V> node=root,child;
  	for (int layer = log2(time) ; layer >= 0 ; layer--) {
  		//System.out.println("leaf"+node);
  		int mask = 1 << (layer-1);
  		if ((mask & version) == mask)
  			child = node.right();
  		else
  			child = node.left();
  		if (layer == 1)
  			return child;
  		node = child;
  	}
  	assert false;
  	return null;
  }

  /** Make a cursor pointing to the given leaf, forcibly creating the path if possible */
  protected NodeCursor<A,V> forceLeaf(int version) {
  	if (time == 0)
  		return root.markValid();
  	NodeCursor<A,V> node=root,child;
  	for (int layer = log2(time) ; layer >= 0 ; layer--) {
  		//System.out.println("forceleaf"+node);
  		int mask = 1 << (layer-1);
  		if ((mask & version) == mask)
  			child = node.forceRight();
  		else
  			child = node.forceLeft();
  		if (layer == 1)
  			return child;
  		node = child;
  	}
  	assert false;
  	return null;
  }

  /** Add an event to the log */
  public void append(V val) {
  	NodeCursor<A,V> leaf;
  	if (time < 0) {
  		time = 0;
  		datastore.updateTime(time);
  		root = leaf = datastore.makeRoot(0);
  	} else {
  		time = time+1;
  		datastore.updateTime(time);
  		reparent(time);
  		leaf = forceLeaf(time);
  	}
  	leaf.setVal(val);
  	computefrozenaggs(leaf);
  }

  /** Recurse from the leaf upwards, computing the agg for all frozen nodes */
  private void computefrozenaggs(NodeCursor<A,V> leaf) {
  	// First, set the leaf agg from the stored event (if it exists
  	if (leaf.hasVal() && leaf.getAgg() == null) {
  		leaf.markValid();
  		leaf.setAgg(aggobj.aggVal(leaf.getVal()));
  	}
  	NodeCursor<A,V> node=leaf.getParent(root);
  	//System.out.println("Adding leaf "+leaf+" ------------------------ " );
  	while (node != null && node.isFrozen(time) && node.getAgg() == null) {
      	//System.out.println("Adding leaf "+leaf+" visit node" +node);
  		node.setAgg(aggobj.aggChildren(node.left().getAgg(),node.right().getAgg()));
  		node = node.getParent(root);
  	}
  }

  protected int time;
  protected NodeCursor<A,V> root;
  protected HistoryDataStoreInterface<A,V> datastore;
  protected AggregationInterface<A,V> aggobj;

  public static int log2(int x) {
  	int i = 0, pow = 1;
  	while (pow <= x) {
  		pow = pow*2;
  		i=i+1;
  	}
  	return i;
  }

}
