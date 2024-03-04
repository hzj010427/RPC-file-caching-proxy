/**
 * Represents a file on the server in a remote procedure call (RPC) system.
 * This class extends {@link RPCFile} to include server-specific file properties
 * and behaviors, such as version management.
 * 
 * It is designed to encapsulate all the information related to a file on the server,
 * including its path and version number, if applicable.
 *
 * @author Zijie Huang
 */
public class ServerFile extends RPCFile {

    /**
     * Constructs a new ServerFile with a specified path and version.
     *
     * @param path    The file path.
     * @param version The file version.
     */
    public ServerFile(String path, int version) {
        super(path, version);
    }

    /**
     * Constructs a new ServerFile with a specified path. This constructor is used
     * when the file version is not known or not applicable.
     *
     * @param path The file path.
     */
    public ServerFile(String path) {
        super(path);
    }
}
