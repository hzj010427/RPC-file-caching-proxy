import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The Cache class provides a simple file caching mechanism that stores files in a specified cache directory.
 * It manages cache space based on a maximum size limit, caching new files, and accessing cached files efficiently.
 * This class uses a read-write lock to ensure thread safety during concurrent access and modifications.
 *
 * @author Zijie Huang
 */
public class Cache {

    /**
     * The maximum size of the cache in bytes.
     */
    private int maxSize;

    /**
     * The current size of the cache in bytes.
     */
    private int currentSize;

    /**
     * The priority queue used to store cache files based on their LRU count and ref count.
     */
    private PriorityQueue<CacheFile> cacheFiles;

    /**
     * The map used to store cache files based on their path.
     */
    private Map<String, CacheFile> cacheFileMap;

    /**
     * The read-write lock used for synchronization.
     */
    private final ReentrantReadWriteLock rwLock;
    private final Lock readLock;
    private final Lock writeLock;

    /**
     * Constructs a Cache object with a specified maximum size.
     *
     * @param size The maximum size of the cache in bytes.
     */
    public Cache(int size) {
        maxSize = size;
        currentSize = 0;
        cacheFiles = new PriorityQueue<CacheFile>();
        cacheFileMap = new HashMap<String, CacheFile>();
        rwLock = new ReentrantReadWriteLock();
        readLock = rwLock.readLock();
        writeLock = rwLock.writeLock();
    }

    /**
     * Adds a CacheFile to the cache. If the cache exceeds its maximum size, older files are evicted based on
     * the LRU (Least Recently Used) policy.
     *
     * @param cacheFile The CacheFile to add to the cache.
     * @return true if the file was successfully cached; false otherwise.
     */
    public boolean put(CacheFile cacheFile) {
        writeLock.lock();
        try {
            String path = cacheFile.getPath();
            currentSize += cacheFile.getSize();
            cacheFiles.add(cacheFile);
            cacheFileMap.put(path, cacheFile);
            System.err.println("File is cached at: " + path + " with size: " + cacheFile.getSize());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when caching file");
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Retrieves a CacheFile from the cache based on its path.
     *
     * @param path The path of the file to retrieve.
     * @return The CacheFile if found; null otherwise.
     */
    public CacheFile getCacheFile(String path) {
        readLock.lock();
        try {
            return cacheFileMap.get(path);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when getting file from cache");
            return null;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the maximum size of the cache.
     *
     * @return The maximum size of the cache in bytes.
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Checks if a file exists in the cache.
     *
     * @param path The path of the file to check.
     * @return true if the file exists in the cache; false otherwise.
     */
    public boolean isFileExist(String path) {
        readLock.lock();
        try {
            return cacheFileMap.containsKey(path);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when checking file existence in cache");
        } finally {
            readLock.unlock();
        }

        return false;
    }

        
    /**
     * Updates the LRU count for all cached files. This method is typically called after accessing a file
     * to ensure the LRU policy is correctly applied.
     */
    public void updateLRU() {
        writeLock.lock();
        try {
            for (CacheFile cacheFile : cacheFiles) {
                cacheFile.incrementLruCount();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when updating LRU count of files in cache");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Deletes a specified CacheFile from the cache, freeing up its space.
     *
     * @param cacheFile The CacheFile to delete.
     */
    public void delete(CacheFile cacheFile) {
        writeLock.lock();
        try {
            if (cacheFile != null) {
                String path = cacheFile.getPath();
                cacheFiles.remove(cacheFile);
                cacheFileMap.remove(path);
                currentSize -= Files.size(Paths.get(path));
                System.err.println("File" + path + " is deleted from cache with size: " + Files.size(Paths.get(path)));
                Files.delete(Paths.get(path));
            } else {
                System.err.println("File not found in cache");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when deleting file from cache");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Increments the reference count of the specified cache file.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param cacheFile The cache file whose reference count is to be incremented.
     */
    public void incrementRefCount(CacheFile cacheFile) {
        modifyCacheFile(cacheFile, ModificationType.INCREMENT_REF_COUNT);
    }
    
    /**
     * Decrements the reference count of the specified cache file.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param cacheFile The cache file whose reference count is to be decremented.
     */
    public void decrementRefCount(CacheFile cacheFile) {
        modifyCacheFile(cacheFile, ModificationType.DECREMENT_REF_COUNT);
    }
    
    /**
     * Resets the Least Recently Used (LRU) count of the specified cache file to zero.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param cacheFile The cache file whose LRU count is to be reset.
     */
    public void resetLRU(CacheFile cacheFile) {
        modifyCacheFile(cacheFile, ModificationType.RESET_LRU_COUNT);
    }

    /**
     * Increments the current size of the cache by the specified size.
     * If the cache becomes full after the increment, eviction is triggered to free up space.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param size The size to be added to the current cache size.
     */
    public void incrementSize(long size) {
        writeLock.lock();
        try {
            if (isFull(size)) {
                evict(size);
            }
            currentSize += size;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when incrementing cache size");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Decrements the current size of the cache by the specified size.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param size The size to be subtracted from the current cache size.
     */
    public void decrementSize(long size) {
        writeLock.lock();
        try {
            currentSize -= size;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when decrementing cache size");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Clears files marked as stale and matching the specified path prefix.
     * Stale files are those that are not currently being referenced and are eligible for deletion.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param pathWithNoVersion The path prefix to match against cache files for determining which files are to be cleared.
     */
    public void clearStaleFiles(String pathWithNoVersion) {
        writeLock.lock();
        try {
            /* collect all the files to delete */
            List<CacheFile> filesToDelete = new ArrayList<>();
            for (CacheFile cacheFile : cacheFiles) {
                if (cacheFile.getPath().startsWith(pathWithNoVersion) 
                && cacheFile.getRefCount() == 0
                && cacheFile.isStale()) {
                    filesToDelete.add(cacheFile);
                }
            }

            for (CacheFile cacheFile : filesToDelete) {
                System.err.println("Clearing stale file: " + cacheFile.getPath());
                delete(cacheFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when clearing stale files from cache");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Marks files matching the specified path prefix as stale.
     * Stale files are those that are considered outdated or no longer needed and are eligible for deletion.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param pathWithNoVersion The path prefix to match against cache files for marking them as stale.
     */
    public void setStaleFiles(String pathWithNoVersion) {
        writeLock.lock();
        try {
            for (CacheFile cacheFile : cacheFiles) {
                if (cacheFile.getPath().startsWith(pathWithNoVersion)) {
                    cacheFile.setStale(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when setting stale files in cache");
        } finally {
            writeLock.unlock();
        }
    } 

    /**
     * Checks if adding a specified size to the cache would exceed its maximum capacity.
     * This method is thread-safe and can be called without acquiring a lock.
     * 
     * @param size The size to check against the remaining capacity of the cache.
     * @return true if adding the size would exceed the cache's maximum capacity, false otherwise.
     */
    public boolean isFull(long size) {
        return currentSize + size > maxSize;
    }

    /**
     * Evicts files from the cache to make space for new entries.
     * The method uses an eviction policy based on the reference count and LRU count of cache files.
     * This method is thread-safe and acquires a write lock to ensure exclusive access.
     * 
     * @param size The size needed to be freed up in the cache.
     */
    public void evict(long size) {
        writeLock.lock();
        try {
            System.err.println("currentSize: " + currentSize + " size: " + size + " maxSize: " + maxSize);
            while (currentSize + size > maxSize) {
                CacheFile cacheFile = cacheFiles.poll();
                if (cacheFile.getRefCount() == 0) {
                    delete(cacheFile);
                } else {
                    cacheFiles.add(cacheFile);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when evicting file from cache");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Gets the current size of the cache.
     * This method is thread-safe and can be called without acquiring a lock.
     * 
     * @return The current size of the cache.
     */
    public int getCurrentSize() {
        return currentSize;
    }

    /**
     * Modifies a cache file's properties based on the specified operation.
     * This private method is used internally to increment or decrement the reference count,
     * or to reset the Least Recently Used (LRU) count of a cache file.
     * It ensures thread safety by acquiring a write lock before performing any modifications.
     *
     * @param cacheFile The cache file to be modified.
     * @param operation The modification operation to perform on the cache file. The operation
     *                  is defined by the {@link ModificationType} enum and can include incrementing
     *                  or decrementing the reference count, or resetting the LRU count.
     * @throws IllegalArgumentException if an unknown operation is passed.
     */
    private void modifyCacheFile(CacheFile cacheFile, ModificationType operation) {
        writeLock.lock();
        try {
            if (cacheFile != null) {
                switch (operation) {
                    case INCREMENT_REF_COUNT:
                        cacheFile.incrementRefCount();
                        break;
                    case DECREMENT_REF_COUNT:
                        cacheFile.decrementRefCount();
                        break;
                    case RESET_LRU_COUNT:
                        cacheFile.setLruCount(0);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown operation: " + operation);
                }
                cacheFiles.remove(cacheFile);
                cacheFiles.add(cacheFile);
            } else {
                System.err.println("File not found in cache");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when modifying file in cache");
        } finally {
            writeLock.unlock();
        }
    }   
}
