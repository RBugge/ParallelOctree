package ParallelOctreeV2;

import java.util.concurrent.locks.ReentrantLock;

public class OptimisticOctree extends Octree {
    volatile Octant root = new Octant(null, new double[] { 0, 0, 0 }, 0.5);

    // Initialize octree
    OptimisticOctree(int vertexLimit, double halfSize) {
        this.vertexLimit = vertexLimit;
        name = "Optimistic";

        root = new Octant(null, new double[] { 0, 0, 0 }, halfSize);
    }

    // Find octant which contains vertex v
    @Override
    protected Octant find(Vertex v) {
        Octant curr = root;
        while (!curr.isLeaf) {
            // Based on morton code, calculates correct octant based on position from center
            int nextOctant = 0;
            if (v.x >= curr.center[0])
                nextOctant += 4;
            if (v.y >= curr.center[1])
                nextOctant += 2;
            if (v.z >= curr.center[2])
                nextOctant += 1;

            curr = (Octant) curr.children[nextOctant];
        }
        return curr;
    }

    // Find and insert
    @Override
    public boolean insert(Vertex v) {

        while (true) {
            Octant o = find(v);
            o.lock.lock();
            try {
                // Validate
                if (o.isLeaf) {
                    return o.insert(v);
                }
            } finally {
                o.lock.unlock();
            }
        }
    }

    // Find and remove
    @Override
    public boolean remove(Vertex v) {
        while (true) {
            Octant o = find(v);
            o.lock.lock();
            try {
                // Validate
                if (o.isLeaf) {
                    return o.remove(v);
                }
            } finally {
                o.lock.unlock();
            }
        }
    }

    @Override
    public boolean contains(Vertex v) {

        return find(v).contains(v);

    }

    class Octant extends Octree.Octant {
        ReentrantLock lock = new ReentrantLock();

        Octant(Octant parent, double center[], double halfSize) {
            this.parent = parent;
            this.center = center;
            this.halfSize = halfSize;
        }

        @Override
        public boolean insert(Vertex v) {
            if (contains(v))
                return false;

            vertices.add(v);
            // If limit was reached and new vertex is not a duplicate
            if (vertices.size() > vertexLimit) {
                subdivide();
            }
            return true;
        }

        // Complete subdivision of octant into eight new octants
        @Override
        protected void subdivide() {

            double childHalfSize = halfSize / 2;

            Octant tempChildren[] = new Octant[8];

            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    for (int k = 0; k < 2; k++) {
                        tempChildren[i * 4 + j * 2 + k] = new Octant(this, new double[] {
                                center[0] - childHalfSize + i * halfSize,
                                center[1] - childHalfSize + j * halfSize,
                                center[2] - childHalfSize + k * halfSize,
                        }, childHalfSize);
                    }
                }
            }

            // Reinsert vertices to children
            for (var v : vertices) {
                int nextOctant = 0;
                if (v.x >= center[0])
                    nextOctant += 4;
                if (v.y >= center[1])
                    nextOctant += 2;
                if (v.z >= center[2])
                    nextOctant += 1;

                tempChildren[nextOctant].insert(v);
            }

            children = tempChildren;

            // Clear children
            vertices.clear();

            // linearization point?
            isLeaf = false;
        }

        @Override
        public boolean remove(Vertex v) {
            lock.lock();
            try {
                return vertices.remove(v);
            } finally {
                lock.unlock();
            }
        }

        // Check if vertex is a duplicate in the octant's vertices
        @Override
        public boolean contains(Vertex v) {
            for (var u : vertices)
                if (u.equals(v))
                    return true;

            return false;
        }

        public boolean inBounds(Vertex v) {
            for (int i = 0; i < 3; i++) {
                if (v.xyz[i] < (root.center[i] - root.halfSize) || v.xyz[i] > (root.center[i] + root.halfSize)) {
                    return false;
                }
            }
            return true;
        }
    }
}
