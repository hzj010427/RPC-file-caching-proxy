/**
 * Represents a chunk of a file in a remote procedure call (RPC) system, extending {@link RPCFile}.
 * This class is designed to handle data segmentation for large file transfers, supporting
 * efficient and partial file operations.
 * 
 * Each chunk includes metadata such as its sequence number, whether it is the last chunk
 * of the file, and the total size of the original file, along with the actual data bytes.
 *
 * @author Zijie Huang
 */
public class ChunkFile extends RPCFile {

    /**
     * Chunk file properties.
     */
    private int chunkNumber;
    private boolean lastChunk;
    private long totalSize;
    private byte[] data;
    private boolean isValid = true;
    private boolean isExsit = true;
    private int statusCode;

    /**
     * Constructs a new ChunkFile with specified properties.
     *
     * @param path       The file path.
     * @param data       The chunk's data bytes.
     * @param version    The file version.
     * @param chunkNumber The sequence number of this chunk.
     * @param lastChunk  Whether this chunk is the last in the series.
     */
    public ChunkFile(String path, byte[] data, int version, int chunkNumber, boolean lastChunk) {
        super(path, version);
        this.chunkNumber = chunkNumber;
        this.lastChunk = lastChunk;
        this.data = data;
    }

    /**
     * Constructs a new ChunkFile for a specified path. This constructor is used
     * when only the path is known or relevant.
     *
     * @param path The file path.
     */
    public ChunkFile(String path) {
        super(path);
    }

    /* Getters and setters for chunk properties. */
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

    public boolean isExsit() {
        return isExsit;
    }

    public void setExsit(boolean exsit) {
        isExsit = exsit;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public boolean isLastChunk() {
        return lastChunk;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
