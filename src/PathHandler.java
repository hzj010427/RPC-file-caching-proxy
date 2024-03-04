import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * PathHandler is responsible for managing and transforming file paths.
 * It provides methods to convert between server and cache paths, generate temporary file paths
 * in the cache, and extract original file names from versioned paths. This class ensures that
 * paths are correctly handled across different parts of the application, taking into account the
 * root directory specified at instantiation.
 * 
 * @author Zijie Huang
 */
public class PathHandler {

    /**
     * The root directory.
     */
    private String rootDir;

    /**
     * Constructs a PathHandlers instance with a specified root directory.
     * 
     * @param rootDir The root directory to be used for path calculations.
     */
    public PathHandler(String rootDir) {
        this.rootDir = rootDir;
    }

    /**
     * Converts a given path to its corresponding server-side path by appending the root directory.
     * 
     * @param path The original path to be transformed.
     * @return A string representing the server-side path.
     */
    public String getPathInServer(String path) {
        return concat(path, rootDir);
    }

    /**
     * Generates a path in the cache for a given file and version number by appending the root directory
     * and a version suffix.
     * 
     * @param path The original file path.
     * @param version The version number of the file.
     * @return A string representing the versioned path in the cache.
     */
    public String getPathInCache(String path, int version) {
        return concat(path, rootDir) + "_v" + version;
    }

    /**
     * Generates a unique temporary path in the cache for a given file and version number, ensuring
     * that the path does not collide with existing files.
     * 
     * @param path The original file path.
     * @param version The version number of the file.
     * @return A string representing a unique temporary path in the cache.
     */
    public String getTmpPathInCache(String path, int version) {
        String baseName = getPathInCache(path, version);
        String suffix = "_tmp";
        String finalPath = baseName + suffix;

        int cnt = 1;
        while (new File(finalPath).exists()) {
            finalPath = baseName + suffix.repeat(++cnt);
        }

        return finalPath;
    }

    /**
     * Extracts the original file name from a versioned path by removing the version and temporary
     * file suffixes.
     * 
     * @param pathWithVersion A string representing the versioned path.
     * @return The original file name without versioning information.
     */
    public String extractOriginalFileName(String pathWithVersion) {
        String patternV = "(_v\\d+)";
        String patternT = "(_tmp)+$";
        return pathWithVersion.replaceAll(patternV, "").replaceAll(patternT, "");
    }

    /**
     * Concatenates two paths, taking into account whether they start with "../" and normalizing
     * the result.
     * 
     * @param path The original file path.
     * @param rootdir The root directory to be appended to the path.
     * @return A normalized string representing the concatenated path.
     */
    private String concat(String path, String rootdir) {
        boolean rootStartsWithParentDir = rootdir.startsWith("../");
        boolean pathStartsWithParentDir = path.startsWith("../");
        String prefix = rootStartsWithParentDir || pathStartsWithParentDir ? "../" : "";

        if (pathStartsWithParentDir) {
            path = path.substring(3);
        }

        if (rootStartsWithParentDir) {
            rootdir = rootdir.substring(3);
        }

        Path originPath = Paths.get(path).normalize();
        Path res = Paths.get(rootdir).resolve(originPath);

        return prefix + res.normalize().toString();
    }
}
