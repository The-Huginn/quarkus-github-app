package io.quarkiverse.githubapp.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.kohsuke.github.GHRepository;

import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.GitHubConfigFileProvider;
import io.quarkiverse.githubapp.GitHubEvent;
import io.quarkiverse.githubapp.runtime.config.CheckedConfigProvider;
import io.quarkiverse.githubapp.runtime.github.GitHubConfigFileProviderImpl;
import org.jboss.logging.Logger;
import org.kohsuke.github.ServiceDownException;

import java.io.IOException;

@RequestScoped
public class RequestScopeCachingGitHubConfigFileProvider {

    private static final Logger LOG = Logger.getLogger(GitHubEvent.class.getPackageName());

    @Inject
    CheckedConfigProvider checkedConfigProvider;

    @Inject
    GitHubConfigFileProvider gitHubConfigFileProvider;

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public Object getConfigObject(GHRepository ghRepository, String path, ConfigFile.Source source, Class<?> type) {
        String cacheKey = getCacheKey(ghRepository, path, source);

        Object cachedObject = cache.get(cacheKey);
        if (cachedObject != null) {
            return cachedObject;
        }

        return cache.computeIfAbsent(cacheKey, k -> {
            try {
                return gitHubConfigFileProvider.fetchConfigFile(ghRepository, path, source, type).orElse(null);
            } catch (IOException e) {
                if (e instanceof ServiceDownException) {
                    LOG.errorf(e, "GitHub service is down. Unable to retrieve \"%s\" file.", path);
                } else {
                    LOG.errorf(e, "Unknown Exception received. Unable to retrieve \"%s\" file.", path);
                }
                return null;
            }
        });
    }

    private String getCacheKey(GHRepository ghRepository, String path,
            ConfigFile.Source source) {
        String fullPath = GitHubConfigFileProviderImpl.getFilePath(path.trim());
        ConfigFile.Source effectiveSource = checkedConfigProvider.getEffectiveSource(source);
        // we should only handle the config files of one repository in a given ConfigFileReader
        // as it's request scoped but let's be on the safe side
        return ghRepository.getFullName() + ":" + effectiveSource.name() + ":" + fullPath;
    }

}
