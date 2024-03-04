import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface defines the methods for downloading and uploading files remotely.
 * 
 * @author Zijie Huang
 */
interface RMIInterface extends Remote {

    /**
     * Downloads a specific chunk of a file located on the server.
     *
     * @param path The path of the file on the server.
     * @param chunkNum The chunk number to download.
     * @param o The open option indicating how the file should be accessed.
     * @param isFirstFetch Whether this is the first time the chunk is being fetched.
     * @return A ChunkFile object containing the data of the requested file chunk.
     * @throws RemoteException If a remote or network exception occurs.
     */
    ChunkFile downloadChunk(String path, int chunkNum, FileHandling.OpenOption o, boolean isFirstFetch) throws RemoteException;

    /**
     * Uploads a chunk of a file to the server.
     *
     * @param chunkFile The ChunkFile object containing the file chunk data to upload.
     * @throws RemoteException If a remote or network exception occurs.
     */
    void uploadChunk(ChunkFile chunkFile) throws RemoteException;

    /**
     * Checks if a specific file exists on the server.
     *
     * @param path The path of the file to check.
     * @return true if the file exists; false otherwise.
     * @throws RemoteException If a remote or network exception occurs.
     */
    boolean isFileExist(String path) throws RemoteException;

    /**
     * Determines if a given path on the server is a directory.
     *
     * @param path The path to check.
     * @return true if the path is a directory; false otherwise.
     * @throws RemoteException If a remote or network exception occurs.
     */
    boolean isDirectory(String path) throws RemoteException;

    /**
     * Retrieves the version of a file stored on the server.
     *
     * @param path The path of the file whose version is to be retrieved.
     * @return The version number of the file.
     * @throws RemoteException If a remote or network exception occurs.
     */
    int getFileVersion(String path) throws RemoteException;

    /**
     * Deletes a file or directory from the server.
     *
     * @param path The path of the file or directory to be deleted.
     * @return true if the deletion was successful; false otherwise.
     * @throws RemoteException If a remote or network exception occurs.
     */
    boolean delete(String path) throws RemoteException;
}
