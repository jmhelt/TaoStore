package TaoProxy;

import Configuration.TaoConfigs;
import Configuration.Utility;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @brief Class that represents the subtree component of the proxy
 */
public class TaoSubtree implements Subtree {
    // Map that maps a block ID to the bucket that contains that block
    private Map<Long, Bucket> mBlockMap;

    // The root of the tree
    private SubtreeBucket mRoot;

    // Lock for the root of the tree, so multiple trees aren't initialized at once
    private Object mRootLock;

    // The last level that needs to be saved to subtree
    // Every level greater than lastLevelToSave will be deleted when a path is deleted
    private int lastLevelToSave;

    /**
     * @brief Default constructor
     */
    public TaoSubtree() {
        mBlockMap = new ConcurrentHashMap<>();
        mRootLock = new Object();
    }

    @Override
    public void initRoot() {
        int numServers = TaoConfigs.PARTITION_SERVERS.size();

        // Check if we have more than one server, in which case we must initialize the subtree
        if (numServers > 1) {
            lastLevelToSave = (int) (Math.log(numServers) / Math.log(2)) - 1;
            TaoLogger.logInfo("The last level to save is " + lastLevelToSave);

            // Initialize the needed amount of top nodes
            mRoot = new TaoSubtreeBucket(0);

            // Keep track of current level and bucket
            int currentLevel = 1;
            SubtreeBucket b = mRoot;

            // If we need to save more than just the root, we do a recursive preorder
            if (currentLevel <= lastLevelToSave) {
                recursivePreorderInit(b, currentLevel);
            }
        } else {
            lastLevelToSave = -1;
        }
    }

    /**
     * @brief Private helper method to initialize the top of the tree in the case of storage partitioning
     * @param b
     * @param level
     */
    private void recursivePreorderInit(SubtreeBucket b, int level) {
        // Set the left and right child
        b.setRight(new TaoSubtreeBucket(), level);
        b.setLeft(new TaoSubtreeBucket(), level);

        // Increase level
        level++;

        // If we have already gone passed the last level to save, we stop
        if (level > lastLevelToSave) {
            return;
        }

        // Continue init
        recursivePreorderInit(b.getLeft(), level);
        recursivePreorderInit(b.getRight(), level);
    }

    @Override
    public Bucket getBucketWithBlock(long blockID) {
        return mBlockMap.getOrDefault(blockID, null);
    }

    @Override
    public void addPath(Path path, long timestamp) {
        TaoLogger.logDebug("Going to add pathid " + path.getPathID() + " to subtree");

        // Boolean to keep track of if a bucket was added to the current level
        boolean added = false;

        // Check if subtree is empty
        if (mRoot == null) {
            TaoLogger.logDebug("Going to add pathid " + path.getPathID() + " to subtree " + " i think root is null");
            // If empty, initialize root with root of given path

            // First we acquire the root lock
            synchronized (mRootLock) {
                // Once we acquire the lock, we make sure that another thread hasn't already initialized the root
                if (mRoot == null) {
                    mRoot = new TaoSubtreeBucket(path.getBucket(0));
                    added = true;
                }
            }
        }

        // If we just added the root, we need to add the blocks to the block map
        if (added) {
            for (Block b : mRoot.getFilledBlocks()) {
                mBlockMap.put(b.getBlockID(), mRoot);
                TaoLogger.logBlock(b.getBlockID(), "subtree add");
            }
        }

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(path.getPathID(), TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        // Update the timestamp of this bucket
        currentBucket.setUpdateTime(timestamp);

        // Keep track of where on the path we are
        int bucketLevel = 1;
        for (Boolean right : pathDirection) {
            // Determine whether the path is turning left or right from current bucket
            if (right) {
                // Attempt to initialize right bucket
                added = currentBucket.setRight(path.getBucket(bucketLevel), bucketLevel);
                currentBucket.getRight().setUpdateTime(timestamp);

                // If we initialized the child, we should add the blocks to the block map
                if (added) {
                    for (Block b : currentBucket.getRight().getFilledBlocks()) {
                        mBlockMap.put(b.getBlockID(), currentBucket.getRight());
                        TaoLogger.logBlock(b.getBlockID(), "subtree add");
                    }
                }

                // Move to next bucket
                currentBucket = currentBucket.getRight();
            } else {
                // Attempt to initialize left bucket
                added = currentBucket.setLeft(path.getBucket(bucketLevel), bucketLevel);
                currentBucket.getLeft().setUpdateTime(timestamp);

                // If we initialized the child, we should add the blocks to the block map
                if (added) {
                    for (Block b : currentBucket.getLeft().getFilledBlocks()) {
                        mBlockMap.put(b.getBlockID(), currentBucket.getLeft());
                        TaoLogger.logBlock(b.getBlockID(), "subtree add");
                    }
                }

                // Move to next bucket
                currentBucket = currentBucket.getLeft();
            }

            // Increment the level of the path
            bucketLevel++;
        }
    }

    @Override
    public void addPath(Path path) {
        System.out.println("Going to add pathid " + path.getPathID());
        TaoLogger.logDebug("Going to add pathid " + path.getPathID() + " to subtree");

        // Boolean to keep track of if a bucket was added to the current level
        boolean added = false;

        // Check if subtree is empty
        if (mRoot == null) {
            // If empty, initialize root with root of given path
            mRoot = new TaoSubtreeBucket(path.getBucket(0));
            added = true;
        }

        // If we just added the root, we need to add the blocks to the block map
        if (added) {
            for (Block b : mRoot.getFilledBlocks()) {
                TaoLogger.logDebug("Adding blockID " + b.getBlockID());
                mBlockMap.put(b.getBlockID(), mRoot);
            }
        }

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(path.getPathID(), TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        // Keep track of where on the path we are
        int bucketLevel = 1;
        for (Boolean right : pathDirection) {
            // Determine whether the path is turning left or right from current bucket
            if (right) {
                // Attempt to initialize right bucket
                added = currentBucket.setRight(path.getBucket(bucketLevel), bucketLevel);

                // If we initialized the child, we should add the blocks to the block map
                if (added) {
                    for (Block b : currentBucket.getRight().getFilledBlocks()) {
                        mBlockMap.put(b.getBlockID(), currentBucket.getRight());
                    }
                }

                // Move to next bucket
                currentBucket = currentBucket.getRight();
            } else {
                // Attempt to initialize left bucket
                added = currentBucket.setLeft(path.getBucket(bucketLevel), bucketLevel);

                // If we initialized the child, we should add the blocks to the block map
                if (added) {
                    for (Block b : currentBucket.getLeft().getFilledBlocks()) {
                        mBlockMap.put(b.getBlockID(), currentBucket.getLeft());
                    }
                }

                // Move to next bucket
                currentBucket = currentBucket.getLeft();
            }

            // Increment the level of the path
            bucketLevel++;
        }
    }

    @Override
    public Path getPath(long pathID) {
        TaoLogger.logDebug("TaoSubtree getPath was called for pathID " + pathID);
        // Create path and insert the root of tree
        Path returnPath = new TaoPath(pathID);
        returnPath.addBucket(mRoot);

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;
        SubtreeBucket previousBucket = null;

        if (mRoot == null) {
            System.out.println("root is null!");
            return null;
        }

        // Visit each level of path
        for (Boolean right : pathDirection) {
            // Get either the right or left child depending on the path
            previousBucket = currentBucket;
            currentBucket = right ? currentBucket.getRight() : currentBucket.getLeft();

            // If the path is null at some level, we return null
            if (currentBucket == null) {
                if (right) {
                    System.out.println("right child of bucket " + previousBucket.getID() + " is null: " + pathID);
                } else {
                    System.out.println("left child of bucket " + previousBucket.getID() + " is null: " + pathID);
                }
                return null;
            }

            // Add bucket to path
            returnPath.addBucket(currentBucket);
        }

        // Return path
        return returnPath;
    }

    @Override
    public Path getCopyOfPath(long pathID) {
        TaoLogger.logDebug("TaoSubtree getPath was called for pathID " + pathID);

        // Create path and insert the root of tree
        Path returnPath = new TaoPath(pathID);
        returnPath.addBucket(mRoot);

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        int l = 0;
        TaoLogger.logDebug("Got level " + l);
        for (Boolean right : pathDirection) {
            l++;
            TaoLogger.logDebug("Getting level " + l);
            // Get either the right or left child depending on the path
            currentBucket = right ? currentBucket.getRight() : currentBucket.getLeft();
            if (currentBucket == null) {
                return null;
            }

            // Add bucket to path
            returnPath.addBucket(currentBucket);
        }

        // Return path
        return returnPath;
    }

    /**
     * @brief Helper method to remove all the blocks in the bucket b from the block map
     * @param b
     */
    private void removeBucketMapping(SubtreeBucket b) {
        // Remove mappings for blockIDs in this bucket, as they will be reassigned
        for (Block block : b.getFilledBlocks()) {
            mBlockMap.remove(block.getBlockID());
        }
    }


    /**
     * @brief Recursive helper method to delete buckets if their timestamp is less than minTime and the path is not in
     * the pathReqMultiSet
     * @param bucket
     * @param pathID
     * @param directions
     * @param level
     * @param minTime
     * @param pathReqMultiSet
     * @return timestamp of bucket
     */
    public long deleteChild(SubtreeBucket bucket, long pathID, boolean[] directions, int level, long minTime, Set<Long> pathReqMultiSet) {
        // Check if we are at a leaf node
        if (level >= directions.length) {
            TaoLogger.logDebug("Returning because level >= directions.length, and level is " + level);
            return bucket.getUpdateTime();
        }

        // Check if we want to get the right or left child of this bucket
        SubtreeBucket child = directions[level] ? bucket.getRight() : bucket.getLeft();

        // If this is a leaf node, return
        if (child == null) {
            TaoLogger.logDebug("Returning because child is null");
            return bucket.getUpdateTime();
        }

        // Increment level
        level++;

        // Save current level
        int currentLevel = level;
        int parentLevel = currentLevel - 1;

        // Delete descendants and get the timestamp of child
        long timestamp = deleteChild(child, pathID, directions, level, minTime, pathReqMultiSet);

        // Check if we should delete the child
        TaoLogger.logDebug("The current parent level is " + parentLevel + " and the lastLevelToSave is " + lastLevelToSave);

        // Print the child bucket for debugging purposes
        child.print();

        // Check if we should delete child
        if (timestamp < minTime && ! isBucketInSet(pathID, currentLevel, pathReqMultiSet) && currentLevel > lastLevelToSave) {
            TaoLogger.logDebug("Deleting because " + timestamp + " < " + minTime);
            // We should delete child, check if it was the right or left child
            if (directions[parentLevel]) {
                TaoLogger.logDebug("Going to delete the right child for path " + pathID + " at level " + parentLevel);
                for (Block b : child.getFilledBlocks()) {
                    TaoLogger.logBlock(b.getBlockID(), "subtree remove");
                }
                removeBucketMapping(child);
                bucket.setRight(null, currentLevel);
            } else {
                TaoLogger.logDebug("Going to delete the left child for path " + pathID + " at level " + parentLevel);
                for (Block b : child.getFilledBlocks()) {
                    TaoLogger.logBlock(b.getBlockID(), "subtree remove");
                }
                removeBucketMapping(child);
                bucket.setLeft(null, currentLevel);
            }
        } else {
            TaoLogger.logDebug("Not going to delete the node at level " + currentLevel + " because...");
            if (timestamp > minTime) {
                TaoLogger.logDebug("Timestamp is greater than " + minTime);
            }
            if (isBucketInSet(pathID, currentLevel, pathReqMultiSet)) {
                TaoLogger.logDebug("Bucket is in set");
            }
        }

        // Return timestamp of this bucket
        return bucket.getUpdateTime();
    }

    /**
     * @brief Check if there is any level at which pathID intersects with a path in the pathReqMultiSet
     * @param pathID
     * @param level
     * @param pathReqMultiSet
     * @return true or false depending on if there is an intersection
     */
    private boolean isBucketInSet(long pathID, int level, Set<Long> pathReqMultiSet) {
        for (Long checkPathID : pathReqMultiSet) {
            if (Utility.getGreatestCommonLevel(pathID, checkPathID) >= level) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void deleteNodes(long pathID, long minTime, Set<Long> pathReqMultiSet) {
        System.out.println("Doing a delete on pathID: " + pathID);
        TaoLogger.logInfo("Doing a delete on pathID: " + pathID + " and min time " + minTime);

        // Check if subtree is empty
        if (mRoot == null) {
            // If mRoot is null, the tree will be reinitialized on the next write
            return;
        }

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Try to delete all descendants
        deleteChild(mRoot, pathID, pathDirection, 0, minTime, pathReqMultiSet);

        // Check if we can delete root
        // NOTE: If root has a timestamp less than minTime, the the entire subtree should be able to be deleted, and
        // thus it should be okay to set mRoot to null
        if (mRoot.getUpdateTime() <= minTime && !pathReqMultiSet.contains(pathID) && 0 > lastLevelToSave) {
            TaoLogger.logDebug("** Deleting the root node too");
            mRoot = null;
        } else {
            TaoLogger.logDebug("** Not deleting root node");
        }
    }

    @Override
    public void mapBlockToBucket(long blockID, Bucket bucket) {
        mBlockMap.put(blockID, bucket);
    }

    @Override
    public void clearPath(long pathID) {
        System.out.println("clearing path: " + pathID);
        if (mRoot == null) {
            return;
        }
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        for (Boolean right : pathDirection) {
            // Remove all block mappings to this bucket and clear the bucket
            removeBucketMapping(currentBucket);
            currentBucket.clearBucket();

            // Determine whether the path is turning left or right from current bucket
            if (right) {
                // Move to next bucket
                currentBucket = currentBucket.getRight();
            } else {
                // Move to next bucket
                currentBucket = currentBucket.getLeft();
            }
        }

        // Remove all block mappings to this bucket and clear the bucket
        removeBucketMapping(currentBucket);

        for (Block b : currentBucket.getFilledBlocks()) {
            TaoLogger.logBlock(b.getBlockID(), "subtree remove");
        }

        // Clear bucket of blocks
        currentBucket.clearBucket();
    }

    @Override
    public void printSubtree() {
        // Print tree for debugging
        Queue<SubtreeBucket> q = new ConcurrentLinkedQueue<>();

        if (mRoot != null) {
            q.add(mRoot);
        }

        // Do a post order traversal of the tree and print out buckets
        while (! q.isEmpty()) {
            SubtreeBucket b = q.poll();

            if (b.getLeft() != null) {
                q.add(b.getLeft());
            }

            if (b.getRight() != null) {
                q.add(b.getRight());
            }

            b.print();
        }
    }
}
