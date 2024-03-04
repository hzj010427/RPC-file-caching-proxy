import java.io.Serializable;

/**
 * Represents a file in a remote procedure call (RPC) system.
 * This class stores the file's path and version, supporting basic operations
 * such as getting and setting the version.
 * 
 * @author Zijie Huang
 */
public class RPCFile implements Serializable {

    /**
     * File information.
     */
    private final String path;
    private int version;

    /**
     * Constructs an RPCFile with a specified path and version.
     *
     * @param path    The file path.
     * @param version The file version.
     */
    public RPCFile(String path, int version) {
        this.path = path;
        this.version = version;
    }

    /**
     * Constructs an RPCFile with a specified path. The version defaults to 0.
     *
     * @param path The file path.
     */
    public RPCFile(String path) {
        this.path = path;
    }

    /**
     * Returns the path of the file.
     *
     * @return The file path.
     */
    public String getPath() {
        return path;
    }

     /**
     * Returns the version of the file.
     *
     * @return The file version.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets the version of the file.
     *
     * @param version The new version.
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Increments the version of the file by one.
     */
    public void incrementVersion() {
        version++;
    }

    /**
     * Clones the version from another RPCFile instance to this one.
     *
     * @param file The RPCFile instance from which to clone the version.
     */
    public void clone(RPCFile file) {
        this.version = file.getVersion();
    }
}
