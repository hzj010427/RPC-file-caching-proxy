import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The Server class implements the remote interface {@link RMIInterface} to provide
 * file upload and download services over RMI. It extends {@link UnicastRemoteObject}
 * to allow objects of this class to be remotely accessible. The server handles client
 * requests to download or upload files, ensuring that operations are performed securely
 * and efficiently.
 * 
 * This class is designed to be part of a larger distributed file system or a similar
 * application where files need to be accessed remotely. It encapsulates the logic for
 * file operations, such as reading from or writing to the server's filesystem, and
 * implements all the methods defined in the {@link RMIInterface}.
 * 
 * Usage of this class requires it to be instantiated and bound to a registry where clients
 * can lookup and invoke its methods. Proper error handling and security measures should be
 * implemented to prevent unauthorized access and ensure data integrity.
 *
 * @author Zijie Huang
 * @see RMIInterface
 * @see UnicastRemoteObject
 */
public class Server extends UnicastRemoteObject implements RMIInterface {

    /**
     * The chunk size used for file transfer in bytes.
     */
    private static final int CHUNK_SIZE = 1024 * 300;

    /**
     * The mode for read and write.
     */
    private static final int MODE_R = 1;
    private static final int MODE_RW = 2;

    /**
     * The error code for permission denied.
     */
    private static final int EACCES = -13;

    /**
     * The root directory of the server.
     */
    private String rootdir;

    /**
     * The read-write lock used for synchronization.
     */
    private final ReentrantReadWriteLock rwLock;
    private final Lock readLock;
    private final Lock writeLock;

    /**
     * The key is the file path, and the value is the ServerFile object.
     */
    private Map<String, ServerFile> serverFileMap;

    /**
     * The path handlers for path manipulation.
     */
    private PathHandler pathHandlers;

    /**
     * Constructs a Server object with the given port and root directory.
     * 
     * @param port The port number of the server.
     * @param rootdir The root directory of the server.
     * @throws RemoteException If a remote communication error occurs.
     */
    public Server(int port, String rootdir) throws RemoteException {
        super(port);
        rwLock = new ReentrantReadWriteLock();
        readLock = rwLock.readLock();
        writeLock = rwLock.writeLock();
        serverFileMap = new HashMap<>();
        this.rootdir = rootdir;
        pathHandlers = new PathHandler(rootdir);
    }

    @Override
    public ChunkFile downloadChunk(String path, int chunkNum, FileHandling.OpenOption o, boolean isFirstFetch) throws RemoteException {
        readLock.lock();
        try {
            int status = processOpen(path, o);

            /* error after open */
            if (status < 0) {
                System.err.println("Error after open: " + status);
                ChunkFile res = new ChunkFile(path, null, 0, chunkNum, true);
                res.setValid(false);
                res.setStatusCode(status);
                return res;
            }

            /* manage server file */
            String serverPath = pathHandlers.getPathInServer(path);
            ServerFile serverFile = manageServerFile(serverPath);
            
            /* success open but file not exist */
            if (!Files.exists(Paths.get(serverPath))) {
                ChunkFile res = new ChunkFile(path, null, 0, chunkNum, true);
                res.setExsit(false);
                res.setStatusCode(status);
                return res;
            }

            /* read chunk data from file if not first fetch */
            long fileSize = Files.size(Paths.get(serverPath));
            System.err.println("Downloading file chunk: " + chunkNum + " from " + serverPath);
            long chunkStart = (long) chunkNum * CHUNK_SIZE;
            long chunkSize = Math.min(CHUNK_SIZE, fileSize - chunkStart);
            byte[] chunkData = isFirstFetch ? null : readChunkData(serverPath, chunkStart, chunkSize);

            boolean isLastChunk = chunkStart + CHUNK_SIZE >= fileSize && !isFirstFetch;
            if (isLastChunk) {
                System.err.println("All file chunks are downloaded at: " + path + " with size: " + fileSize);
            }
            ChunkFile chunkFile = new ChunkFile(path, chunkData, serverFile.getVersion(), chunkNum, isLastChunk);
            chunkFile.setTotalSize(fileSize);
            chunkFile.setStatusCode(status);
            return chunkFile;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when reading file from remote server");
            return null; // error
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void uploadChunk(ChunkFile chunkFile) throws RemoteException {
        writeLock.lock();
        try {
            String serverPath = pathHandlers.getPathInServer(chunkFile.getPath());
            byte[] data = chunkFile.getData();
            int version = chunkFile.getVersion();
            int chunkNum = chunkFile.getChunkNumber();
            System.err.println("Uploading file chunk: " + serverPath + " " + chunkNum);

            writeChunkData(serverPath, data, (long) chunkNum * CHUNK_SIZE);

            if (chunkFile.isLastChunk()) {
                serverFileMap.put(serverPath, new ServerFile(serverPath, version));
                System.err.println("File is uploaded at: " 
                    + serverPath + " with version: " 
                    + version + " and size: " + Files.size(Paths.get(serverPath)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when uploading file chunk to remote server");
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean delete(String path) throws RemoteException {
        writeLock.lock();
        try {
            path = pathHandlers.getPathInServer(path);
            System.err.println("Deleting file: " + path);
            if (Files.exists(Paths.get(path))) {
                Files.delete(Paths.get(path));
                serverFileMap.remove(path);
                System.err.println("File is deleted at: " + path);
                return true;
            }
            System.err.println("File not found in remote server");
            return false; // not found
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when deleting file from remote server");
            return false; // error
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean isFileExist(String path) throws RemoteException {
        readLock.lock();
        try {
            path = pathHandlers.getPathInServer(path);
            boolean res = Files.exists(Paths.get(path));
            System.err.println("Checking file existence: " + path + " " + res);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when checking file existence in remote server");
            return false;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isDirectory(String path) throws RemoteException {
        readLock.lock();
        try {
            path = pathHandlers.getPathInServer(path);
            boolean res = Files.isDirectory(Paths.get(path));
            System.err.println("Checking file type: " + path + " " + res);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when checking file type in remote server");
            return false;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int getFileVersion(String path) throws RemoteException {
        readLock.lock();
        try {
            path = pathHandlers.getPathInServer(path);
            if (Files.exists(Paths.get(path))) {
                if (serverFileMap.containsKey(path)) {
                    int res = serverFileMap.get(path).getVersion();
                    System.err.println("Getting file version: " + path + " " + res);
                    return res;
                } else {
                    return 0; // version 0
                }
            }
            System.err.println("File not found in remote server");
            return -1; // not found
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when getting file version from remote server");
            return -1; // error
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Reads a chunk of data from a file located on the server.
     * This method will read the chunk data based on the chunk start and chunk size.
     * 
     * @param serverPath    The path of the file on the server.
     * @param chunkStart    The start position of the chunk in the file.
     * @param chunkSize     The size of the chunk to read.
     * @return              The chunk data as a byte array.
     */
    private byte[] readChunkData(String serverPath, long chunkStart, long chunkSize) {
        byte[] chunkData = new byte[(int) chunkSize];
        try (RandomAccessFile file = new RandomAccessFile(Paths.get(serverPath).toFile(), "r")) {
            file.seek(chunkStart);
            file.read(chunkData, 0, (int) chunkSize);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when reading file chunk from remote server");
            return null; // error
        }
        return chunkData;
    }

    /**
     * Writes a chunk of data to a file located on the server.
     * This method will write the chunk data to the file based on the chunk start.
     * 
     * @param serverPath    The path of the file on the server.
     * @param data          The chunk data to write.
     * @param chunkStart    The start position of the chunk in the file.
     */
    private void writeChunkData(String serverPath, byte[] data, long chunkStart) {
        try (RandomAccessFile file = new RandomAccessFile(Paths.get(serverPath).toFile(), "rw")) {
            file.seek(chunkStart);
            file.write(data, 0, data.length);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when writing file chunk to remote server");
        }
    }

    /**
     * Manages the server file based on the server path.
     * This method will create a new ServerFile will version 0 
     * if the server path does not exist in the map.
     * 
     * @param serverPath   The path of the file on the server.
     * @return             The ServerFile object.
     */
    private ServerFile manageServerFile(String serverPath) {
        ServerFile serverFile;
        if (serverFileMap.containsKey(serverPath)) {
            serverFile = serverFileMap.get(serverPath);
        } else {
            serverFile = new ServerFile(serverPath, 0);
            serverFileMap.put(serverPath, serverFile);
        }
        return serverFile;
    }

    /**
     * Checks if the file is in the root directory of the server.
     * 
     * @param path                  The path of the file to check.
     * @return                      true if the file is in the root directory; false otherwise.
     * @throws RemoteException      If a remote or network exception occurs.
     */
    private boolean inRootDir(String path) throws RemoteException {
        readLock.lock();
        try {
            Path rootPath = Paths.get(rootdir).toAbsolutePath().normalize();
            Path inputPath = Paths.get(path).toAbsolutePath().normalize();
            return inputPath.startsWith(rootPath);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when checking file in root directory in remote server");
            return false;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Processes the open option for a file located on the server.
     * 
     * @param path      The path of the file on the server.
     * @param o         The open option indicating how the file should be accessed.
     * @return          The mode for read and write if the open is successful, or an error code if the open fails.
     */
    private int processOpen(String path, FileHandling.OpenOption o) {
        String serverPath = pathHandlers.getPathInServer(path);
        String mode = "r";
        File file = new File(serverPath);

        try {
            if (!inRootDir(serverPath)) {
                System.err.println("BLOCKED!!! user is trying to access file outside of root directory");
                return FileHandling.Errors.EPERM;
            }

            switch (o) {
            case READ:
                System.err.println("open option: read");
                if (!file.exists()) {
                    System.err.println("file does not exist");
                    return FileHandling.Errors.ENOENT;
                }
                if (!file.canRead()) {
                    System.err.println("permission denied");
                    return EACCES;
                }
                mode = "r";
                return MODE_R;
            case WRITE:
                System.err.println("open option: write");
                if (!file.exists()) {
                    return FileHandling.Errors.ENOENT;
                }
                if (!file.canWrite()) {
                    return FileHandling.Errors.EISDIR;
                }
                if (!file.canRead()) {
                    return EACCES;
                }
                mode = "rw";
                return MODE_RW;
            case CREATE:
                System.err.println("open option: create");
                if (!file.exists() && !file.createNewFile()) {
                    return FileHandling.Errors.EINVAL;
                }
                mode = "rw";
                return MODE_RW;
            case CREATE_NEW:
                System.err.println("open option: create new");
                if (file.exists()) {
                    return FileHandling.Errors.EEXIST;
                }
                mode = "rw";
                return MODE_RW;
            default:
                System.err.println("open default");
                return FileHandling.Errors.EINVAL;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error when processing open in remote server");
            return FileHandling.Errors.EINVAL;
        }
    }
    
    // Usage: java Server <port> <rootdir>
    public static void main(String args[]) {
        System.err.println("Starting server...");

        /* parse port and root directory */
        if (args.length != 2) {
            System.err.println("Usage: java Server <port> <rootdir>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        String rootdir = args[1];

        /* start server */
        try {
            Server server = new Server(port, rootdir);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.bind("RMIInterface", server);
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
