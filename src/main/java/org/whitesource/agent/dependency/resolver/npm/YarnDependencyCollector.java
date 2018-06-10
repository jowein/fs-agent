package org.whitesource.agent.dependency.resolver.npm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class YarnDependencyCollector extends NpmLsJsonDependencyCollector {
    private final Logger logger = LoggerFactory.getLogger(YarnDependencyCollector.class);
    private static final String YARN_COMMAND = isWindows() ? "yarn.cmd" : "yarn";
    private String fileSeparator = System.getProperty(Constants.FILE_SEPARATOR);


    public YarnDependencyCollector(boolean includeDevDependencies, long npmTimeoutDependenciesCollector, boolean ignoreNpmLsErrors) {
        super(includeDevDependencies, npmTimeoutDependenciesCollector, ignoreNpmLsErrors);
    }

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String folder) {
        File yarnLock = new File(folder + fileSeparator + "yarn.lock");
        boolean yarnLockFound = yarnLock.isFile();
        if (!yarnLockFound){
            try {
                yarnLockFound = installYarnLock(folder);
            } catch (IOException e) {
                npmLsFailureStatus = true;
                e.printStackTrace();
            }
        }
        List<DependencyInfo> dependencyInfos = null;
        if (yarnLockFound){
            dependencyInfos = parseYarnLock(yarnLock);

        } else {
            npmLsFailureStatus = true;
        }
        return getSingleProjectList(dependencyInfos);
    }

    protected String[] getInstallParams() {
        return new String[]{YARN_COMMAND, Constants.INSTALL};
    }

    private boolean installYarnLock(String folder) throws IOException {
        CommandLineProcess yarnInstallCommand = new CommandLineProcess(folder, getInstallParams());
        yarnInstallCommand.setTimeoutReadLineSeconds(this.npmTimeoutDependenciesCollector);
        List<String> linesOfYarnInstall = yarnInstallCommand.executeProcess();
        if (yarnInstallCommand.isErrorInProcess()) {
            for (String line : linesOfYarnInstall) {
                if (line.startsWith("success")) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<DependencyInfo> parseYarnLock(File yarnLock){
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        HashMap<String, DependencyInfo> parentsMap = new HashMap<>();
        HashMap<String, DependencyInfo> childrenMap = new HashMap<>();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(yarnLock.getPath());
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            boolean insideDependencies = false;
            DependencyInfo dependencyInfo = null;
            while ((currLine = bufferedReader.readLine()) != null){
                if (currLine.isEmpty() || currLine.startsWith("#") || currLine.trim().isEmpty()){
                    insideDependencies = false;
                    continue;
                }
                if (currLine.startsWith(Constants.WHITESPACE)) {
                   if (currLine.trim().startsWith(Constants.VERSION)){
                       String version = currLine.substring(currLine.indexOf("\"") + 1, currLine.lastIndexOf("\""));
                       dependencyInfo.setVersion(version);
                       dependencyInfo.setArtifactId(dependencyInfo.getGroupId() + Constants.DASH + version + ".tgz");
                   } else if (currLine.trim().startsWith("resolved")){
                       String sha1 = currLine.substring(currLine.indexOf(Constants.POUND) + 1, currLine.lastIndexOf("\""));
                       dependencyInfo.setSha1(sha1);
                   } else if (currLine.trim().startsWith(Constants.DEPENDENCIES) || currLine.trim().startsWith("optionalDependencies")) {
                       insideDependencies = true;
                   } else if (insideDependencies){
                        String name = currLine.trim().replaceFirst(Constants.WHITESPACE, "@");
                        name = name.replaceAll("\"", Constants.EMPTY_STRING);
                        childrenMap.put(name, dependencyInfo);
                    }
                } else {
                    String[] split = currLine.split(Constants.COMMA + Constants.WHITESPACE);
                    for (int i = 0; i < split.length; i++){
                        String name = split[i].substring(0, split[i].length() - (split[i].endsWith(Constants.COLON) ? 1 : 0));
                        name = name.replaceAll("\"",Constants.EMPTY_STRING);
                        String groupId = name.split("@")[0];
                        if (i==0) {
                            dependencyInfo = new DependencyInfo();
                            dependencyInfo.setGroupId(groupId);
                            // TODO - add YARN depdendency type
                            dependencyInfo.setDependencyType(DependencyType.NPM);
                            String pathToPackageJson = yarnLock.getParent() + fileSeparator + "node_modules" + fileSeparator + groupId + fileSeparator + "package.json";
                            dependencyInfo.setSystemPath(pathToPackageJson);
                            dependencyInfo.setFilename(pathToPackageJson);
                        }
                        if (parentsMap.get(name) == null){
                            parentsMap.put(name, dependencyInfo);
                        }
                    }
                }
            }
            for (String child : childrenMap.keySet()){
                childrenMap.get(child).getChildren().add(parentsMap.get(child));
            }
            for (String parent : parentsMap.keySet()){
                if (childrenMap.get(parent) == null){
                    dependencyInfos.add(parentsMap.get(parent));
                }
            }
        } catch (FileNotFoundException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileReader != null){
                try {
                    fileReader.close();
                } catch (IOException e) {
                    logger.error("can't close {}: {}", yarnLock.getPath(), e.getMessage());
                }
            }
        }
        return dependencyInfos;
    }
}