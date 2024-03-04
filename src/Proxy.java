import java.io.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The Proxy class serves as an intermediary between clients and a remote server, handling
 * file operations and caching through RPC (Remote Procedure Call) requests. It aims to
 * optimize file access and manipulation by caching files locally, reducing the need for
 * repeated network calls to the server for frequently accessed files.
 * 
 * @author Zijie Huang
 */
class Proxy {

    /**
     * The Cache object that stores the file content in cachedir.
     */
    private static Cache cache;

    /**
     * The root directory of the cache.
     */
    private static String cachedir;

    /**
     * The server IP address and port number.
     */
    private static String serverip;
    private static int port;

    /**
     * The FileHandler class implements the FileHandling interface, providing a concrete
     * implementation for handling file operations such as open, read, write, and close. It
     * encapsulates the logic for interacting with both the local cache and the remote server
     * to perform file operations, ensuring data consistency and managing cache states.
     */
	private static class FileHandler implements FileHandling {

        /**
         * Error code for I/O error.
         */
        private static final int EIO = -100;

        /**
         * The mode for read and write.
         */
        private static final int MODE_R = 1;
        private static final int MODE_RW = 2;

        /**
         * The chunk size used for file transfer in bytes.
         */
        private static final int CHUNK_SIZE = 1024 * 300;
        
        /**
         * The file descriptor map that maps temporary file to file descriptor.
         */
        private final HashMap<Integer, TmpFile> fdMap
            = new HashMap<Integer, TmpFile>();

        /**
         * The path handlers for path manipulation.
         */
        private final PathHandler pathHandler 
            = new PathHandler(cachedir);

        /**
         * The RPC handler for RPC calls.
         */
        private final RPCHandler rpcHandler 
            = new RPCHandler(serverip, port);

        /**
         * File descriptor counter.
         */
		private int fdCounter = 0;

        /**
         * Flags for write.
         */
        private int writeFlag = 0;

        /**
         * Open a file with the given path and open option.
         * 
         * @param path the path of the file to open
         * @param o the open option
         * @return the file descriptor of the opened file, otherwise an error code
         */
		public int open(String path, OpenOption o) {
            System.err.println("open: " + path + " " + o);
			try {
                int fd; 
                synchronized (cache) {
                    cache.updateLRU();
                
                    /* get all the chunks of the file */
                    CacheFile cacheFile = fetch(serverip, port, path, o);

                    if (!cacheFile.isValid()) {
                        System.err.println("return status code: " + cacheFile.getStatusCode());
                        return cacheFile.getStatusCode();
                    }

                    fd = handleFd(cacheFile, path);
                }

                return fd;
			} catch (IOException e) {
				return EIO;
			} finally {
                System.err.println("open: " + path + " " + o + " done");
            }
		}

        /**
         * Close the file with the given file descriptor.
         * If the file is modified, upload it to the server.
         * 
         * @param fd the file descriptor of the file to close
         * @return 0 if the file is closed successfully, otherwise an error code
         */
		public int close(int fd) {
            log(fd, "close");
            TmpFile tmpFile = fdMap.get(fd);
			RandomAccessFile raf = tmpFile.getRaf();
            String permission = tmpFile.getPermission();
            String originPath = tmpFile.getPath();
            String tmpPath = tmpFile.getTmpPath();
            String cachePath = tmpFile.getCachePath();
            int latestVersion = Integer.MIN_VALUE;

			try {
                if (raf == null && permission == null) { // file does not exist
                    System.err.println("file does not exist");
                    return Errors.EBADF;
                }

                if (raf != null) { // file is not a directory
                    raf.close();
                }

                synchronized (cache) {
                    if (writeFlag == 1) { // write close
                        latestVersion = rpcHandler.getFileVersion(serverip, port, originPath) + 1;
                        String latestCachePath = pathHandler.getPathInCache(originPath, latestVersion);
                        Files.copy(Paths.get(tmpPath), Paths.get(latestCachePath));
                        int size = (int) Files.size(Paths.get(latestCachePath));
                        CacheFile latestCacheFile = new CacheFile(latestCachePath, latestVersion, size);
                        cache.put(latestCacheFile);

                        /* update the old cache file to stale */
                        if (cache.isFileExist(cachePath)) {
                            CacheFile oldCacheFile = cache.getCacheFile(cachePath);
                            oldCacheFile.setStale(true);
                        }
                        
                        /* delete the tmp file */
                        if (Files.exists(Paths.get(tmpPath))) {
                            System.err.println("deleting tmp file: " + tmpPath);
                            cache.decrementSize(Files.size(Paths.get(tmpPath)));
                            Files.delete(Paths.get(tmpPath));
                        }

                        rpcHandler.upload(serverip, port, originPath, latestCacheFile);
                        writeFlag = 0;
                    }

                    if (cache.isFileExist(cachePath)) {
                        CacheFile cacheFile = cache.getCacheFile(cachePath);
                        System.err.println("The ref count of " + cacheFile.getPath() + " is " + cacheFile.getRefCount() + " before decrement");
                        cache.decrementRefCount(cacheFile);
                        cache.resetLRU(cacheFile);
                        /* clean up stale cache files */
                        String pathWithOutVersion = pathHandler.extractOriginalFileName(cacheFile.getPath());
                        cache.clearStaleFiles(pathWithOutVersion);
                    }
                }

                return 0;
			} catch (IOException e) {
                System.err.println("I/O error when closing file: " + e.toString());
				return EIO;
			} finally {
                fdMap.remove(fd);
            }
		}

        /**
         * Write the given buffer to the file with the given file descriptor.
         * @param fd the file descriptor of the file to write to
         * @param buf the buffer to write
         * @return the number of bytes written, otherwise an error code
         */
		public long write(int fd, byte[] buf) {
            TmpFile tmpFile = fdMap.get(fd);
			RandomAccessFile raf = tmpFile.getRaf();
            String permission = tmpFile.getPermission();
            int size = tmpFile.getSize();
            // log(fd, "write");
			try {
                /* file does not exist or no write permission */
                if (raf == null 
                    || !permission.equals("rw")) {
                    return Errors.EBADF;
                }

                synchronized (cache) {
                    if (size + buf.length > cache.getMaxSize()) {
                        System.err.println("Cache is full, evicting files...");
                        cache.evict(size + buf.length);
                    }
                }
                
				raf.write(buf);
                size += buf.length;
                tmpFile.setSize(size);
                writeFlag = 1;
				return buf.length;
			} catch (IOException e) {
				return EIO;
			}
		}

        /**
         * Read from the file with the given file descriptor to the given buffer.
         * @param fd the file descriptor of the file to read from
         * @param buf the buffer to read to
         * @return the number of bytes read, otherwise an error code
         */
		public long read(int fd, byte[] buf) {
            int bytesRead = 0;
			RandomAccessFile raf = fdMap.get(fd).getRaf();
            String permission = fdMap.get(fd).getPermission();
            // log(fd, "read");
			try {
                if (raf == null && permission == null) { // file does not exist
                    return Errors.EBADF;
                }
                if (raf == null && permission.equals("r")) { // file is a directory
                    return Errors.EISDIR;
                }

                bytesRead = raf.read(buf);
                if (bytesRead == -1) { // reach the end of the file
                    return 0;
                }
                return bytesRead;
			} catch (IOException e) {
				return EIO;
			}
		}

        /**
         * Move the file pointer of the file with the given file descriptor to the given position.
         * @param fd the file descriptor of the file to move the file pointer of
         * @param pos the position to move the file pointer to, relative to the given option
         * @param o it represents an option or mode that determines how the file pointer should be adjusted
         * @return the new position of the file pointer, otherwise an error code
         */
		public long lseek(int fd, long pos, LseekOption o) {
			RandomAccessFile raf = fdMap.get(fd).getRaf();
            log(fd, "lseek");
			try {
                if (raf == null) {
                    return Errors.EBADF;
                }
				switch (o) {
					case FROM_START:
                        if (pos < 0) {
                            return Errors.EINVAL;
                        }
						raf.seek(pos);
						break;
					case FROM_CURRENT:
						raf.seek(raf.getFilePointer() + pos);
						break;
					case FROM_END:
                        if (pos > 0) {
                            return Errors.EINVAL;
                        }
						raf.seek(raf.length() + pos);
						break;
				}
				return raf.getFilePointer();
			} catch (IOException e) {
				return EIO;
			}
		}

        /**
         * Remove the file with the given path.
         * @param path the path of the file to remove
         * @return 0 if the file is removed successfully, or an error code
         */
		public int unlink(String path) {

            if (!rpcHandler.isFileExist(serverip, port, path)) {
                System.err.println("file does not exist");
                return Errors.ENOENT;
            }

            if (rpcHandler.isDirectory(serverip, port, path)) {
                System.err.println("file is a directory");
                return Errors.EISDIR;
            }

            if (rpcHandler.delete(serverip, port, path)) {
                System.err.println("unlink: " + path + " from server");
                return 0;
            } else {
                System.err.println("permission denied");
                return Errors.EPERM;
            }
		}

        /**
         * Clean up all the opened random access files in the map.
         */
		public void clientdone() {
            try {
                for (TmpFile tmpFile : fdMap.values()) {
                    try {
                        RandomAccessFile raf = tmpFile.getRaf();
                        if (raf != null) { // file is not a directory
                            raf.close();
                        }
                    } catch (IOException e) {
                        System.err.println("I/O error when closing file: " + e.toString());
                    }
                }

                fdMap.clear();
            } catch (Exception e) {
                System.err.println("Error when cleaning up client");
            }
        }

        /**
         * Fetches the specified file from a remote server and caches it locally. This method handles
         * the retrieval of file chunks sequentially from the server, combining them into a single
         * local file in the cache. If the file already exists in the cache and is up to date, it
         * increases its reference count instead of fetching it again. This method ensures that the
         * cache does not exceed its size limit by evicting LRU files if necessary.
         *
         * @param serverip The IP address of the server from which to fetch the file.
         * @param port The port number on the server to connect to.
         * @param path The path of the file on the server to be fetched.
         * @param o The open option indicating how the file should be opened.
         * @return A CacheFile object representing the cached file, or null if the file does not exist
         *         on the server or could not be fetched for some other reason. The CacheFile object
         *         contains metadata about the file, including its path in the cache, its version, and
         *         its total size.
         * @throws IOException If an I/O error occurs while fetching the file, creating directories in
         *         the cache, or writing the file data to the cache.
         */
        private CacheFile fetch(String serverip, int port, String path, OpenOption o) {
            int chunkNum = 0;
            int offset = 0;
            boolean fileOpened = false;
            RandomAccessFile raf = null;
            CacheFile cacheFile = null;
            ChunkFile chunkFile = null;
            boolean isFirstFetch = true; // The first chunk will not contain any file data

            try {
                do {
                    System.err.println("isFirstFetch: " + isFirstFetch);
                    System.err.println("Fetching chunk " + chunkNum + "...");
                    chunkFile = rpcHandler.download(serverip, port, path, chunkNum, o, isFirstFetch);

                    if (chunkFile.isValid() && chunkFile.isExsit()) {
                        
                        if (!fileOpened) {
                            /* file exists in cache */
                            String cachePath = pathHandler.getPathInCache(path, chunkFile.getVersion());
                            if (cache.isFileExist(cachePath)) {
                                System.err.println("file: " + cachePath + " exists in cache CACHE HIT");
                                cacheFile = cache.getCacheFile(cachePath);
                                cache.incrementRefCount(cacheFile);
                                break;
                            }

                            if (!isFirstFetch) {
                                long totalSize = chunkFile.getTotalSize();
                                cacheFile = new CacheFile(cachePath, chunkFile.getVersion(), (int) totalSize);

                                /* evict if cache is full */
                                if (cache.isFull(totalSize)) {
                                    System.err.println("Cache is full, evicting files...");
                                    cache.evict(totalSize);
                                }
                                
                                /* clear the stale files in cache and put the new file to cache */
                                String pathWithOutVersion = pathHandler.extractOriginalFileName(cacheFile.getPath());
                                cache.setStaleFiles(pathWithOutVersion);
                                cache.clearStaleFiles(pathWithOutVersion);
                                cache.put(cacheFile);
                                cache.incrementRefCount(cacheFile);

                                Files.createDirectories(Paths.get(cachePath).getParent());
                                raf = new RandomAccessFile(Paths.get(cachePath).toFile(), "rw");
                                fileOpened = true;
                            }
                        }

                        if (!isFirstFetch) {
                            raf.seek(offset);
                            raf.write(chunkFile.getData());
                            chunkNum++;
                            offset = chunkNum * CHUNK_SIZE;
                        }

                        isFirstFetch = false;
                    } else if (!chunkFile.isValid()) {
                        System.err.println("file is not valid");
                        cacheFile = new CacheFile(null);
                        cacheFile.setValid(false);
                    } else if (!chunkFile.isExsit()) {
                        System.err.println("file does not exist in server");
                        String cachePath = pathHandler.getPathInCache(path, 0);
                        cacheFile = new CacheFile(cachePath);
                    } else {
                        System.err.println("Error: unknown error");
                        break;
                    }
                } while (!chunkFile.isLastChunk());
            } catch (IOException e) {
                System.err.println("I/O error when writing chunk data: " + e.toString());
            } finally {
                cacheFile.setStatusCode(chunkFile.getStatusCode());
                return cacheFile;
            }
        }

        /**
         * Assigns a file descriptor (fd) to the given CacheFile and manages the creation of a temporary file if necessary.
         * This method abstracts the process of opening a file, whether for read-only or read-write access, and tracks the
         * file descriptor associated with the file's temporary or direct access path.
         * 
         * @param cacheFile The CacheFile object to be accessed.
         * @param path The original path of the file.
         * @return An integer representing the file descriptor associated with the opened file.
         * @throws IOException If an error occurs while handling the file descriptor or creating the temporary file.
         */
        private int handleFd(CacheFile cacheFile, String path) throws IOException {
            try {
                int statusCode = cacheFile.getStatusCode();
                String mode = statusCode == MODE_R ? "r" : "rw";
                int fd = fdCounter++;
                TmpFile tmpFile = generateTmpFile(cacheFile, path, mode);
                fdMap.put(fd, tmpFile);
                return fd;
            } catch (IOException e) {
                System.err.println("I/O error when handling file descriptor: " + e.toString());
                throw e;
            }
        }

        /**
         * Generates a temporary file for a given cache file, facilitating read or write operations.
         * This method handles the creation and setup of a temporary file within the cache, 
         * based on the specified mode of operation. For write operations, a new temporary file 
         * is created to store changes before they are committed. For read operations, 
         * no temporary file is created, and operations are performed directly on the cached file.
         *
         * @param cacheFile The cache file for which the temporary file is to be generated.
         * @param originPath The original path of the file before caching.
         * @param mode The mode of operation, indicating whether the file is to be opened for 
         *             reading ("r") or writing ("rw"). A temporary file is only created for write operations.
         * @return A TmpFile object containing information about the temporary file, including 
         *         its path, cache path, permissions, and a RandomAccessFile instance for I/O operations.
         *         For read-only operations, the temporary path will be null.
         * @throws IOException If an I/O error occurs while creating the temporary file or performing file operations.
         */
        private TmpFile generateTmpFile(CacheFile cacheFile, String originPath, String mode) throws IOException {
            try {
                String cachePath = cacheFile.getPath();
                String tmpPath = null; // only writer will set tmpPath
                int copySize = 0;
                RandomAccessFile raf;

                if (mode.equals("r")) {
                    raf = new RandomAccessFile(new File(cachePath), mode);
                } else {
                    tmpPath = pathHandler.getTmpPathInCache(originPath, cacheFile.getVersion());

                    if (cache.isFileExist(cachePath)) {
                        copySize = (int) Files.size(Paths.get(cachePath));
                        if (cache.isFull(copySize)) {
                            System.err.println("Cache is full when creating tmp file, evicting files...");
                            cache.evict(copySize);
                        }
                        Files.copy(Paths.get(cachePath), Paths.get(tmpPath));
                        cache.incrementSize(copySize);
                        System.err.println("tmp file created at: " + tmpPath);
                    }
                    
                    File tmp = new File(tmpPath);
                    raf = new RandomAccessFile(tmp, mode);
                }

                /* record all the info of the tmp file */
                TmpFile tmpFile = new TmpFile(originPath, tmpPath, cachePath, mode, raf, copySize);

                return tmpFile;
            } catch (IOException e) {
                System.err.println("I/O error when creating tmp file: " + e.toString());
                throw e;
            }
        }

        /* logger for debugging */
        private void log(int fd, String op) {
            String permission = fdMap.get(fd).getPermission();
            String path = fdMap.get(fd).getPath();
            System.err.println(op + ": fd = " + fd + ", path = " + path + ", permission = " + permission);
        }
	}

    /**
     * Factory class for creating instances of FileHandler.
     * Each instance is designed to handle file operations in its own thread,
     * allowing for concurrent file processing across multiple clients.
     */
	private static class FileHandlingFactory implements FileHandlingMaking {

        /**
         * Creates a new FileHandler instance.
         * This method is intended to be used for initializing a new client
         * with a dedicated thread for handling file operations, enabling
         * isolated and concurrent processing of file requests.
         *
         * @return A new instance of FileHandler for managing file operations.
         */
		public FileHandling newclient() {
			return new FileHandler();
		}
	}

    // Usage: java Proxy <serverip> <port> <cachedir> <cachesize>
	public static void main(String[] args) throws IOException {
        System.err.println("Starting proxy...");
        if (args.length != 4) {
            System.err.println("Usage: java Proxy <serverip> <port> <cachedir> <cachesize>");
            System.exit(1);
        }

        /* parse arguments and init cache */
        serverip = args[0];
        port = Integer.parseInt(args[1]);
        cachedir = args[2];
        cache = new Cache(Integer.parseInt(args[3]));
        System.err.println("The cache size is " + cache.getMaxSize());

        /* init RPCreceiver */
        FileHandlingFactory factory = new FileHandlingFactory();
        RPCreceiver receiver = new RPCreceiver(factory);
        receiver.run();
	}
}
