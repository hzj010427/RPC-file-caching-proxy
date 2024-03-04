import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Handles RPC (Remote Procedure Call) operations for file management.
 * 
 * The RPCHandler class is designed to facilitate remote file operations such as uploading,
 * downloading, checking file existence, and managing file versions through RMI (Remote Method Invocation).
 * It encapsulates the complexity of RMI communication, providing a simplified interface for
 * performing file operations on a remote server.
 *
 * This class uses an RMI stub for invoking methods on the remote server, which must implement
 * the {@link RMIInterface}. It supports operations on files in chunks to optimize network usage
 * and handle large files efficiently.
 *
 * Instances of this class are initialized with the server's IP address and port number,
 * setting up the RMI connection to the remote server for subsequent file operations.
 * 
 * @author Zijie Huang
 */
public class RPCHandler {

    /**
     * The chunk size used for file transfer in bytes.
     */
    private static final int CHUNK_SIZE = 1024 * 300;

    /**
     * RMI stub for remote method invocation.
     */
    private RMIInterface stub;

    /**
     * Server IP address.
     */
    private String serverip;

    /**
     * Server port.
     */
    private int port;

    /**
     * Constructor that initializes RMI connection.
     */
    public RPCHandler(String serverip, int port) {
        this.serverip = serverip;
        this.port = port;
        initializeRMIConnection();
    }

    /**
     * Initializes RMI connection.
     */
    private void initializeRMIConnection() {
        try {
            Registry registry = LocateRegistry.getRegistry(serverip, port);
            stub = (RMIInterface) registry.lookup("RMIInterface");
            System.err.println("RMI connection initialized successfully.");
        } catch (RemoteException e) {
            System.err.println("RemoteException during RMI initialization: " + e.toString());
            e.printStackTrace();
            throw new RuntimeException("Error initializing RMI connection", e);
        } catch (NotBoundException e) {
            System.err.println("NotBoundException during RMI initialization: " + e.toString());
            e.printStackTrace();
            throw new RuntimeException("RMI service not bound", e);
        }
    }

    /**
     * Downloads a specific chunk of a file from the server.
     *
     * @param serverip The IP address of the server from which to download the file.
     * @param port The port number on which the server is listening.
     * @param path The path of the file to download.
     * @param chunkNum The specific chunk number of the file to download.
     * @param o The open option indicating how the file is to be opened.
     * @param isFirstFetch Whether this is the first time the chunk is being fetched.
     * @return A ChunkFile object containing the downloaded file chunk, or null if an error occurs.
     */
    public ChunkFile download(
        String serverip, int port, String path, int chunkNum, FileHandling.OpenOption o, boolean isFirstFetch) {
        
        try {
            System.err.println("RPC CALL Downloading file from server: " + path);
            return stub.downloadChunk(path, chunkNum, o, isFirstFetch);
        } catch (RemoteException e) {
            System.err.println("RemoteException: " + e.toString());
            e.printStackTrace();
            return null; // error
        }
    }

    /**
     * Uploads a file to the server in chunks.
     *
     * @param serverip The IP address of the server to which the file is uploaded.
     * @param port The port number on which the server is listening.
     * @param originPath The original path of the file on the client side.
     * @param cacheFile The {@link CacheFile} object representing the file to be uploaded.
     */
    public void upload(String serverip, int port, String originPath, CacheFile cacheFile) {
        try {
            System.err.println("RPC CALL Uploading file to server: " + originPath);
            
            int chunkNum = 0;
            int chunkStart = 0;
            int totalSize = cacheFile.getSize();
            int version = cacheFile.getVersion();
            String cachePath = cacheFile.getPath();

            while (chunkStart < totalSize) {
                int chunkSize = Math.min(CHUNK_SIZE, totalSize - chunkStart);
                byte[] data = new byte[chunkSize];

                RandomAccessFile raf = new RandomAccessFile(Paths.get(cachePath).toFile(), "r");
                raf.seek(chunkStart);
                raf.read(data, 0, chunkSize);

                boolean lastChunk = chunkStart + chunkSize >= totalSize;
                ChunkFile chunkFile = new ChunkFile(originPath, data, version, chunkNum++, lastChunk);
                stub.uploadChunk(chunkFile);
                chunkStart += chunkSize;
            }
        } catch (RemoteException e) {
            System.err.println("RemoteException: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IOException: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Checks if a file exists on the server.
     *
     * @param serverip The IP address of the server.
     * @param port The port number on which the server is listening.
     * @param path The path of the file to check.
     * @return true if the file exists; false otherwise or if an error occurs.
     */
    public boolean isFileExist(String serverip, int port, String path) {
        try {
            System.err.println("RPC CALL Checking file existence: " + path);
            return stub.isFileExist(path);
        } catch (RemoteException e) {
            System.err.println("RemoteException: " + e.toString());
            e.printStackTrace();
            return false; // error
        }
    }

    /**
     * Checks if a path on the server is a directory.
     *
     * @param serverip The IP address of the server.
     * @param port The port number on which the server is listening.
     * @param path The path to check.
     * @return true if the path is a directory; false otherwise or if an error occurs.
     */
    public  boolean isDirectory(String serverip, int port, String path) {
        try {
            System.err.println("RPC CALL Checking directory existence: " + path);
            return stub.isDirectory(path);
        } catch (RemoteException e) {
            System.err.println("RemoteException: " + e.toString());
            e.printStackTrace();
            return false; // error
        }
    }

    /**
     * Retrieves the version of a file stored on the server.
     *
     * @param serverip The IP address of the server.
     * @param port The port number on which the server is listening.
     * @param path The path of the file whose version is to be retrieved.
     * @return The version of the file, or -1 if an error occurs.
     */
    public int getFileVersion(String serverip, int port, String path) {
        try {
            System.err.println("RPC CALL Getting file version: " + path);
            return stub.getFileVersion(path);
        } catch (RemoteException e) {
            System.err.println("RemoteException: " + e.toString());
            e.printStackTrace();
            return -1; // error
        }
    }

    /**
     * Deletes a file from the server.
     *
     * @param serverip The IP address of the server.
     * @param port The port number on which the server is listening.
     * @param path The path of the file to be deleted.
     * @return true if the file was successfully deleted; false otherwise or if an error occurs.
     */
    public boolean delete(String serverip, int port, String path) {
        try {
            System.err.println("RPC CALL Deleting file: " + path);
            return stub.delete(path);
        } catch (RemoteException e) {
            System.err.println("RemoteException: " + e.toString());
            e.printStackTrace();
            return false; // error
        }
    }
}
