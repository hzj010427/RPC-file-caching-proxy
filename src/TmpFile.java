import java.io.RandomAccessFile;

/**
 * Represents a remote procedure call (RPC) file with a path and version.
 * This class serves as a base class for various types of files used in an RPC mechanism,
 * including caching and temporary file handling.
 */
public class TmpFile extends RPCFile {

    /**
     * Temporary file properties.
     */
    private String tmpPath;
    private String cachePath;
    private String permission;
    private RandomAccessFile raf;
    private int size;
    
    /**
     * Constructs an TmpFile with a specified path, version, and size.
     * 
     * @param path    The file path.
     * @param version The file version.
     * @param size    The file size.
     */
    public TmpFile(String path, int version, int size) {
        super(path, version);
    }

    /**
     * Constructs a TmpFile with specified path, temporary path, cache path,
     * access permission, a RandomAccessFile handle, and size.
     *
     * @param path      The original path of the file.
     * @param tmpPath   The temporary file path.
     * @param cachePath The cache path for the file.
     * @param mode      The access mode for the file.
     * @param raf       A RandomAccessFile handle for file operations.
     * @param size      The size of the file in bytes.
     */
    public TmpFile(String path, String tmpPath, String cachePath, String mode, RandomAccessFile raf, int size) {
        super(path);
        this.tmpPath = tmpPath;
        this.cachePath = cachePath;
        this.permission = mode;
        this.raf = raf;
        this.size = size;
    }

    /* Getters and setters for temporary file properties. */
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
    
    public String getTmpPath() {
        return tmpPath;
    }

    public void setTmpPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }

    public String getCachePath() {
        return cachePath;
    }

    public void setCachePath(String cachePath) {
        this.cachePath = cachePath;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public RandomAccessFile getRaf() {
        return raf;
    }

    public void setRaf(RandomAccessFile raf) {
        this.raf = raf;
    }
}
