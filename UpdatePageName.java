package cn.iocoder.yudao;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static java.io.File.separator;

/**
 * 项目修改器，一键替换 Maven 的 groupId、artifactId，项目的 package 等
 * 支持多模块递归扫描，避免改动类名、文件名，只替换包名和pom文件中的信息
 *
 * @author 芋道源码（改写）
 */
@Slf4j
public class ProjectReactor {

    private static final String GROUP_ID = "cn.iocoder.boot";
    private static final String ARTIFACT_ID = "yudao";
    private static final String PACKAGE_NAME = "cn.iocoder.yudao";
    private static final String TITLE = "芋道管理系统";

    /**
     * 白名单文件，不进行重写，避免出问题
     */
    private static final Set<String> WHITE_FILE_TYPES = SetUtils.asSet(
            "gif", "jpg", "svg", "png", // 图片
            "eot", "woff2", "ttf", "woff",  // 字体
            "xdb" // IP 库
    );

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        String projectBaseDir = getProjectBaseDir();
        log.info("[main][原项目根目录：{}]", projectBaseDir);

        // 这里配置你要替换成的新内容
        String groupIdNew = "cn.star.gg";
        String artifactIdNew = "star";
        String packageNameNew = "cn.star.pp";
        String titleNew = "土豆管理系统";

        String projectBaseDirNew = "todou_xxxx" + "-new";// 自定义

        if (FileUtil.exist(projectBaseDirNew)) {
            log.error("[main][新项目目录已存在，请先删除或者改名后再执行：{}]", projectBaseDirNew);
            return;
        }

        if (StrUtil.containsAny(projectBaseDirNew, PACKAGE_NAME, ARTIFACT_ID, StrUtil.upperFirst(ARTIFACT_ID))) {
            log.error("[main][新项目目录 {} 包含冲突关键字（{} / {}），请更改！]", projectBaseDirNew, PACKAGE_NAME, ARTIFACT_ID);
            return;
        }

        Collection<File> files = listFiles(projectBaseDir);
        log.info("[main][找到文件数量：{}]", files.size());

        files.forEach(file -> {
            String fileType = getFileType(file);
            if (WHITE_FILE_TYPES.contains(fileType)) {
                copyFile(file, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
                return;
            }
            String content = replaceFileContent(file, groupIdNew, artifactIdNew, packageNameNew, titleNew);
            writeFile(file, content, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
        });

        log.info("[main][替换完成，耗时 {} 秒]", (System.currentTimeMillis() - start) / 1000);
    }

    private static String getProjectBaseDir() {
        String baseDir = System.getProperty("user.dir");
        if (StrUtil.isEmpty(baseDir)) {
            throw new NullPointerException("项目基础路径不存在");
        }
        return baseDir;
    }

    private static Collection<File> listFiles(String projectBaseDir) {
        Collection<File> files = FileUtil.loopFiles(projectBaseDir);
        return files.stream()
                .filter(file -> {
                    String path = file.getPath();
                    return !path.contains(separator + "target" + separator)
                            && !path.contains(separator + "node_modules" + separator)
                            && !path.contains(separator + ".idea" + separator)
                            && !path.contains(separator + ".git" + separator)
                            && !path.contains(separator + "dist" + separator)
                            && !path.endsWith(".iml")
                            && !path.endsWith(".html.gz");
                })
                .collect(Collectors.toList());
    }

    /**
     * 替换文件内容
     * - pom.xml 文件内全部替换 groupId/artifactId/package/title
     * - 其他文件只替换 package 和 import 中的包名，其他内容不变
     */
    private static String replaceFileContent(File file, String groupIdNew,
                                             String artifactIdNew, String packageNameNew,
                                             String titleNew) {
        String content = FileUtil.readString(file, StandardCharsets.UTF_8);
        String fileType = getFileType(file);
        if (WHITE_FILE_TYPES.contains(fileType)) {
            return content;
        }

        String path = file.getPath();

        if (path.endsWith("pom.xml")) {
            // pom.xml 全部替换
            return content.replaceAll(GROUP_ID, groupIdNew)
                    .replaceAll(ARTIFACT_ID, artifactIdNew)
                    .replaceAll(PACKAGE_NAME, packageNameNew)
                    .replaceAll(TITLE, titleNew);
        }

        // 只替换 package 和 import 语句中的包名，其他内容保持不变
        StringBuilder sb = new StringBuilder();
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")) {
                line = line.replace(PACKAGE_NAME, packageNameNew);
            } else if (trimmed.startsWith("import ")) {
                line = line.replace(PACKAGE_NAME, packageNameNew);
            }
            sb.append(line).append("\n");
        }

        return sb.toString();
    }

    private static void writeFile(File file, String fileContent, String projectBaseDir,
                                  String projectBaseDirNew, String packageNameNew, String artifactIdNew) {
        String newPath = buildNewFilePath(file, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
        File newFile = new File(newPath);
        FileUtil.mkParentDirs(newFile);
        FileUtil.writeUtf8String(fileContent, newFile);
        log.info("[writeFile] 写入：{}", newPath);
    }

    private static void copyFile(File file, String projectBaseDir,
                                 String projectBaseDirNew, String packageNameNew, String artifactIdNew) {
        String newPath = buildNewFilePath(file, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
        File newFile = new File(newPath);
        FileUtil.mkParentDirs(newFile);
        FileUtil.copyFile(file, newFile);
        log.info("[copyFile] 复制：{}", newPath);
    }

    /**
     * 替换路径中的包名和 artifactId，但只替换目录路径，不改文件名
     */
    private static String buildNewFilePath(File file, String projectBaseDir,
                                           String projectBaseDirNew, String packageNameNew, String artifactIdNew) {
        String oldPath = file.getPath();

        String newPath = oldPath.replace(projectBaseDir, projectBaseDirNew);

        int lastSepIndex = newPath.lastIndexOf(separator);
        if (lastSepIndex == -1) {
            return newPath;
        }
        String dir = newPath.substring(0, lastSepIndex);
        String fileName = newPath.substring(lastSepIndex + 1);

        dir = dir.replace(PACKAGE_NAME.replaceAll("\\.", Matcher.quoteReplacement(separator)),
                packageNameNew.replaceAll("\\.", Matcher.quoteReplacement(separator)));
        dir = dir.replace(ARTIFACT_ID, artifactIdNew)
                .replaceAll(StrUtil.upperFirst(ARTIFACT_ID), StrUtil.upperFirst(artifactIdNew));

        return dir + separator + fileName;
    }

    private static String getFileType(File file) {
        return file.length() > 0 ? FileTypeUtil.getType(file) : "";
    }
}
