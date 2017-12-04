package org.whitesource.agent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.MemoryUsageHelper;
import org.whitesource.fs.FileSystemAgent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class does the actual directory scanning, creates {@link DependencyInfo}s.
 *
 * @author tom.shapira
 * @author anna.rozin
 */
public class FileSystemScanner {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);

    public static final int MAX_EXTRACTION_DEPTH = 7;
    private static String FSA_FILE = "**/*whitesource-fs-agent-*.*jar";
    private final boolean showProgressBar;

    private DependencyResolutionService dependencyResolutionService;

    /* --- Members --- */



    /* --- Constructors --- */

    public FileSystemScanner(boolean showProgressBar, DependencyResolutionService dependencyResolutionService) {
        this.showProgressBar = showProgressBar;
        this.dependencyResolutionService = dependencyResolutionService;
    }

    /* --- Public methods --- */

    public List<DependencyInfo> createDependencies(List<String> scannerBaseDirs, boolean scmConnector,
                                                   String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                   String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                   Collection<String> excludedCopyrights, boolean partialSha1Match) {
        Collection<AgentProjectInfo> projects = createDependencies(scannerBaseDirs, scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match, false, false);
        return projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
    }

    public Collection<AgentProjectInfo> createDependencies(List<String> scannerBaseDirs, boolean scmConnector,
                                                           String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                           String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                           Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5) {

        MemoryUsageHelper.SystemStats systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());

        // get canonical paths
        Set<String> pathsToScan = getCanonicalPaths(scannerBaseDirs);

        // validate parameters
        validateParams(archiveExtractionDepth, includes);

        // scan directories
        int totalFiles = 0;

        String unpackDirectory = null;
        // go over all base directories, look for archives
        Map<String, String> archiveToBaseDirMap = new HashMap<>();
        if (archiveExtractionDepth > 0) {
            ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes, excludes, archiveFastUnpack);
            logger.info("Starting Archive Extraction (may take a few minutes)");
            for (String scannerBaseDir : new LinkedHashSet<>(pathsToScan)) {
                unpackDirectory = archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth);
                if (unpackDirectory != null) {
                    archiveToBaseDirMap.put(unpackDirectory, new File(scannerBaseDir).getParent());
                    pathsToScan.add(unpackDirectory);
                }
            }
        }

        // create dependencies from files
        logger.info("Starting Analysis");
        Map<AgentProjectInfo, Path> allProjects = new HashMap<>();

        logger.info("Scanning Directory {} for Matching Files (may take a few minutes)", pathsToScan);
        Map<File, Collection<String>> fileMapBeforeResolve = fillFilesMap(pathsToScan, includes, excludes, followSymlinks, globCaseSensitive);
        Set<String> allFiles = fileMapBeforeResolve.entrySet().stream().flatMap(folder -> folder.getValue().stream()).collect(Collectors.toSet());

        boolean isDependenciesOnly = false;
        if (dependencyResolutionService != null && dependencyResolutionService.shouldResolveDependencies(allFiles)) {
            isDependenciesOnly = dependencyResolutionService.isDependenciesOnly();

            // get all resolution results
            Collection<ResolutionResult> resolutionResults = dependencyResolutionService.resolveDependencies(pathsToScan, excludes);

            // add all resolved dependencies
            final int[] totalDependencies = {0};
            resolutionResults.stream().map(result -> result.getResolvedProjects()).forEach(projects -> {
                projects.entrySet().stream().forEach(project -> {
                    Collection<DependencyInfo> dependencies = project.getKey().getDependencies();
                    allProjects.put(project.getKey(), project.getValue());
                    totalDependencies[0] += dependencies.size();
                    dependencies.forEach(dependency -> increaseCount(dependency, totalDependencies));
                });
            });
            logger.info(MessageFormat.format("Total dependencies Found: {0}", totalDependencies[0]));

            // merge additional excludes
            Set<String> allExcludes = resolutionResults.stream().flatMap(resolution -> resolution.getExcludes().stream()).collect(Collectors.toSet());
            allExcludes.addAll(Arrays.stream(excludes).collect(Collectors.toList()));

            // change the original excludes with the merged values
            excludes = new String[allExcludes.size()];
            excludes = allExcludes.toArray(excludes);
            dependencyResolutionService = null;
        }

        String[] excludesExtended = excludeFileSystemAgent(excludes);
        Map<File, Collection<String>> fileMap = fillFilesMap(pathsToScan, includes, excludesExtended, followSymlinks, globCaseSensitive);
        long filesCount = fileMap.entrySet().stream().flatMap(folder -> folder.getValue().stream()).count();
        totalFiles += filesCount;
        logger.info(MessageFormat.format("Total Files Found: {0}", totalFiles));
        DependencyCalculator dependencyCalculator = new DependencyCalculator(showProgressBar);
        final Collection<DependencyInfo> filesDependencies = new LinkedList<>();

        if (!isDependenciesOnly) {
            filesDependencies.addAll(dependencyCalculator.createDependencies(
                    scmConnector, totalFiles, fileMap, excludedCopyrights, partialSha1Match, calculateHints, calculateMd5));
        }

        if (allProjects.size() <= 1) {
            AgentProjectInfo project = null;
            if (allProjects.isEmpty()) {
                project = new AgentProjectInfo();
                allProjects.put(project, null);
            } else {
                project = allProjects.keySet().stream().findFirst().get();
            }
            project.getDependencies().addAll(filesDependencies);
        } else {
            // remove files from handled projects
            allProjects.entrySet().forEach(project -> {
                Collection<DependencyInfo> projectDependencies = filesDependencies.stream().filter(dependencyInfo -> dependencyInfo.getSystemPath().contains(project.getValue().toString())).collect(Collectors.toList());
                project.getKey().getDependencies().addAll(projectDependencies);
                filesDependencies.removeAll(projectDependencies);
            });

            // create new projects if necessary
            if (!isDependenciesOnly && filesDependencies.size() > 0) {
                scannerBaseDirs.stream().forEach(directory -> {
                    List<Path> subDirectories = FilesUtils.getSubDirectories(directory);
                    subDirectories.forEach(subFolder -> {
                        if (filesDependencies.size() > 0) {
                            List<DependencyInfo> projectDependencies = filesDependencies.stream().filter(dependencyInfo -> dependencyInfo.getSystemPath().contains(subFolder.toString())).collect(Collectors.toList());
                            if (!projectDependencies.isEmpty()) {
                                AgentProjectInfo subProject = new AgentProjectInfo();
                                subProject.setCoordinates(new Coordinates(null, subFolder.toFile().getName(), null));
                                subProject.setDependencies(projectDependencies);
                                allProjects.put(subProject, null);
                                filesDependencies.removeAll(projectDependencies);
                            }
                        }
                    });
                });
            }
        }

        if (filesDependencies.size() > 0) {
            logger.warn("Files not sent {}", System.lineSeparator() + String.join(System.lineSeparator(), filesDependencies.stream().map(file -> file.getSystemPath()).collect(Collectors.toList())));
        }

        for (AgentProjectInfo innerProject : allProjects.keySet()) {
            // replace temp folder name with base dir
            for (DependencyInfo dependencyInfo : innerProject.getDependencies()) {
                String systemPath = dependencyInfo.getSystemPath();
                if (systemPath == null) {
                    logger.debug("Dependency {} has no system path", dependencyInfo.getFilename());
                } else {
                    for (String key : archiveToBaseDirMap.keySet()) {
                        if (systemPath.contains(key) && unpackDirectory != null) {
                            String newSystemPath = systemPath.replace(key, archiveToBaseDirMap.get(key)).replaceAll(ArchiveExtractor.DEPTH_REGEX, "");
                            dependencyInfo.setSystemPath(newSystemPath);
                            break;
                        }
                    }
                }
            }
        }

        // delete all archive temp folders
        if (unpackDirectory != null) {
            File directory = new File(unpackDirectory);
            if (directory.exists()) {
                try {
                    FileUtils.deleteDirectory(directory);
                } catch (IOException e) {
                    logger.warn("Error deleting archive directory", e);
                }
            }
        }

        logger.info("Finished Analyzing Files");

        systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());

        return allProjects.keySet();
    }

    /* --- Private methods --- */

    private Set<String> getCanonicalPaths(List<String> scannerBaseDirs) {
        // use canonical paths to resolve '.' in path
        Set<String> pathsToScan = new HashSet<>();
        for (String path : scannerBaseDirs) {
            try {
                pathsToScan.add(new File(path).getCanonicalPath());
            } catch (IOException e) {
                // use the given path as-is
                logger.debug("Error finding the canonical path of {}", path);
                pathsToScan.add(path);
            }
        }
        return pathsToScan;
    }



    private Map<File, Collection<String>> fillFilesMap(Collection<String> pathsToScan, String[] includes, String[] excludesExtended, boolean followSymlinks, boolean globCaseSensitive) {
        Map<File, Collection<String>> fileMap = new HashMap<>();
        for (String scannerBaseDir : pathsToScan) {
            File file = new File(scannerBaseDir);
            if (file.exists()) {
                FilesScanner filesScanner = new FilesScanner();
                if (file.isDirectory()) {
                    File basedir = new File(scannerBaseDir);
                    String[] fileNames = filesScanner.getFileNames(scannerBaseDir, includes, excludesExtended, followSymlinks, globCaseSensitive);
                    // convert array to list (don't use Arrays.asList, might be added to later)
                    List<String> fileNameList = Arrays.stream(fileNames).collect(Collectors.toList());
                    fileMap.put(basedir, fileNameList);
                } else {
                    // handle single file
                    boolean included = filesScanner.isIncluded(file, includes, excludesExtended, followSymlinks, globCaseSensitive);
                    if (included) {
                        Collection<String> files = fileMap.get(file.getParentFile());
                        if (files == null) {
                            files = new ArrayList<>();
                        }
                        files.add(file.getName());
                        fileMap.put(file.getParentFile(), files);
                    }
                }
            } else {
                logger.info(MessageFormat.format("File {0} doesn\'t exist", scannerBaseDir));
            }
        }
        return fileMap;
    }

    private void increaseCount(DependencyInfo dependency, int[] totalDependencies) {
        totalDependencies[0] += dependency.getChildren().size();
        dependency.getChildren().forEach(dependencyInfo -> increaseCount(dependencyInfo, totalDependencies));
    }



    private void validateParams(int archiveExtractionDepth, String[] includes) {
        boolean isShutDown = false;
        if (archiveExtractionDepth < 0 || archiveExtractionDepth > MAX_EXTRACTION_DEPTH) {
            logger.warn("Error: archiveExtractionDepth value should be greater than 0 and less than 4");
            isShutDown = true;
        }
        if (includes.length < 1 || StringUtils.isBlank(includes[0])) {
            logger.warn("Error: includes parameter must have at list one scanning pattern");
            isShutDown = true;
        }
        if (isShutDown) {
            logger.warn("Exiting");
            System.exit(1);
        }
    }

    private String[] excludeFileSystemAgent(String[] excludes) {
        String[] excludesFSA = new String[excludes.length + 1];
        System.arraycopy(excludes, 0, excludesFSA, 0, excludes.length);
        excludesFSA[excludes.length] = FSA_FILE;
        return excludesFSA;
    }
}