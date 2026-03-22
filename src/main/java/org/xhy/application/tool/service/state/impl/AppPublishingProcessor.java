package org.xhy.application.tool.service.state.impl;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xhy.application.tool.service.state.AppToolStateProcessor;
import org.xhy.domain.tool.constant.ToolStatus;
import org.xhy.domain.tool.model.ToolEntity;
import org.xhy.domain.tool.model.dto.GitHubRepoInfo;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.github.GitHubService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** 应用层工具发布处理器
 * 
 * 职责： 1. 处理已通过审核的工具发布 2. 从源GitHub下载工具内容，并将其发布到目标GitHub仓库 3. 完成工具发布流程 */
public class AppPublishingProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppPublishingProcessor.class);

    private final GitHubService gitHubService;

    /** 构造函数，注入GitHubService
     * 
     * @param gitHubService GitHub服务 */
    public AppPublishingProcessor(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.APPROVED;
    }

    @Override
    public void process(ToolEntity tool) {
        // Internal-only mode: do not publish to any URL or repository.
        logger.info("工具ID: {} 进入APPROVED状态，发布已禁用。 (internal-only mode)", tool.getId());
    }

    @Override
    public ToolStatus getNextStatus() {
        // 发布完成后没有自动下一状态，工具保持在APPROVED状态
        return null;
    }

    /** 清理临时下载和解压的文件/目录 */
    private void cleanupTemporaryFiles(Path tempDownloadPath, Path tempUnzipPath) {
        try {
            if (tempDownloadPath != null && Files.exists(tempDownloadPath)) {
                Files.delete(tempDownloadPath);
                logger.info("已删除临时下载文件: {}", tempDownloadPath);
            }
            if (tempUnzipPath != null && Files.exists(tempUnzipPath)) {
                FileUtils.deleteDirectory(tempUnzipPath.toFile());
                logger.info("已删除临时解压目录: {}", tempUnzipPath);
            }
        } catch (IOException e) {
            logger.warn("清理发布过程中的临时文件失败: {}", e.getMessage());
        }
    }

    /** 查找解压后ZIP文件的实际内容根目录 GitHub下载的ZIP通常会有一个顶层目录，例如 repo-name-commitsha/ 或 repo-name-tag/
     * 
     * @param unzipDir 解压操作的根目录
     * @param repoNameHint 源仓库的名称，用于辅助查找
     * @return 实际内容所在的Path对象，如果无法确定则返回unzipDir本身
     * @throws IOException 如果列出目录内容时发生IO错误 */
    private Path findActualContentRoot(Path unzipDir, String repoNameHint) throws IOException {
        List<Path> subDirs;
        try (var stream = Files.list(unzipDir)) {
            subDirs = stream.filter(Files::isDirectory).toList();
        }

        if (subDirs.size() == 1) {
            // 如果解压后只有一个子目录，通常这就是内容根目录
            logger.info("找到唯一子目录作为内容根: {}", subDirs.get(0));
            return subDirs.get(0);
        }

        // 如果有多个子目录，尝试基于仓库名提示进行匹配
        if (repoNameHint != null && !repoNameHint.isEmpty()) {
            for (Path subDir : subDirs) {
                if (subDir.getFileName().toString().toLowerCase().contains(repoNameHint.toLowerCase())) {
                    logger.info("通过仓库名提示找到内容根: {}", subDir);
                    return subDir;
                }
            }
        }

        // 如果无法通过上述方式确定，记录警告并返回解压目录本身
        logger.warn("无法精确找到解压后的实际内容根目录于 {}，将使用该目录作为根。请检查ZIP结构。", unzipDir);
        return unzipDir;
    }
}