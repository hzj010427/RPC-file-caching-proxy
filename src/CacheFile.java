/**
 * Extends the {@link RPCFile} class to include caching-specific properties such as
 * reference count, LRU (Least Recently Used) count, size, stale, valid, and status code.
 * This class is used to manage cache files in a cache system, supporting operations
 * like incrementing reference count, LRU count, and managing stale and valid states.
 * 
 * @author Zijie Huang
 */
public class CacheFile extends RPCFile implements Comparable<CacheFile> {

    /**
     * Cache file properties.
     */
    private int refCount;
    private int lruCount;
    private int size;
    private boolean isStale = false;
    private boolean isValid = true;
    private int statusCode;

    /**
     * Constructs a CacheFile with a specified path, version, and size.
     *
     * @param path    The file path.
     * @param version The file version.
     * @param size    The file size.
     */
    public CacheFile(String path, int version, int size) {
        super(path, version);
        refCount = 0;
        lruCount = 0;
        this.size = size;
    }

    /**
     * Constructs a CacheFile with a specified path. Version defaults to 0.
     *
     * @param path The file path.
     */
    public CacheFile(String path) {
        super(path);
    }

    /* Getters and setters for cache file properties. */
    public boolean isStale() {
        return isStale;
    }

    public void setStale(boolean stale) {
        isStale = stale;
    }

    public boolean isValid() {
        return isValid;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public int getRefCount() {
        return refCount;
    }

    public int getLruCount() {
        return lruCount;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setLruCount(int lruCount) {
        this.lruCount = lruCount;
    }

    public void incrementRefCount() {
        refCount++;
    }

    public void decrementRefCount() {
        if (refCount > 0) {
            refCount--;
        } else {
            System.err.println("Error: refCount is already 0");
        }
    }

    public void incrementLruCount() {
        lruCount++;
    }

    @Override
    public int compareTo(CacheFile other) {
        if (this.refCount == 0 && other.refCount != 0) {
            return -1;
        } else if (this.refCount != 0 && other.refCount == 0) {
            return 1;
        } else {
            return Integer.compare(other.lruCount, this.lruCount);
        }
    }
}
