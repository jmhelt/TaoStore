package TaoProxy;

/**
 * @brief Interface for a SubtreeBucket
 */
public interface SubtreeBucket extends Bucket {
    /**
     * @brief Set the right child of this SubTree bucket
     * @param b
     * @return if something was set or not
     */
    boolean setRight(Bucket b, int level);
    boolean setRight(Bucket b, int level, int caller);

    /**
     * @brief Set the left child of this Subtree bucket
     * @param b
     */
    boolean setLeft(Bucket b, int level);
    boolean setLeft(Bucket b, int level, int caller);

    /**
     * @brief Accessor method to get the right child of this bucket
     * @return the right child of this Subtree bucket
     */
    SubtreeBucket getRight();

    /**
     * @brief Accessor method to get the left child of this bucket
     * @return the left child of this Subtree bucket
     */
    SubtreeBucket getLeft();

    /**
     * @brief Used for debugging, prints contents of bucket to screen
     */
    void print();
}
