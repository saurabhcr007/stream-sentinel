package com.streamsentinel.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads and validates {@link RulesConfig} from a YAML source.
 *
 * <h3>Resolution Order</h3>
 * <ol>
 * <li>Environment variable {@value #ENV_RULES_PATH} (file system path)</li>
 * <li>Explicit file system path passed to {@link #fromFile(String)}</li>
 * <li>Classpath resource via {@link #fromClasspath(String)}</li>
 * </ol>
 *
 * <h3>Validation</h3>
 * <p>
 * All {@code load*} methods call {@link RulesConfig#validate()} after parsing
 * so that the application <strong>fails fast</strong> on invalid rules rather
 * than producing undefined runtime behaviour.
 * </p>
 *
 * @since 1.0.0
 */
public final class RulesLoader {

    private static final Logger LOG = LoggerFactory.getLogger(RulesLoader.class);

    /** Environment variable that can override the default config location. */
    public static final String ENV_RULES_PATH = "RULES_CONFIG_PATH";

    private RulesLoader() {
        // utility class — not instantiable
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Load rules using automatic resolution.
     *
     * <ol>
     * <li>If {@code RULES_CONFIG_PATH} is set and the file exists, load from
     * there.</li>
     * <li>Otherwise, fall back to {@code rules.yml} on the classpath.</li>
     * </ol>
     *
     * @return parsed and validated rules configuration
     * @throws IllegalStateException if rule validation fails
     */
    public static RulesConfig load() {
        String envPath = System.getenv(ENV_RULES_PATH);
        if (envPath != null && !envPath.isBlank() && Files.exists(Path.of(envPath))) {
            LOG.info("Loading rules from environment path: {}", envPath);
            return fromFile(envPath);
        }
        LOG.info("Loading rules from classpath: rules.yml");
        return fromClasspath("rules.yml");
    }

    /**
     * Load rules from a file system path.
     *
     * @param path absolute or relative path to the YAML file; must not be
     *             {@code null}
     * @return parsed and validated rules configuration
     * @throws NullPointerException     if {@code path} is {@code null}
     * @throws IllegalArgumentException if the file does not exist
     * @throws IllegalStateException    if reading fails or rule validation fails
     */
    public static RulesConfig fromFile(String path) {
        Objects.requireNonNull(path, "Rules file path must not be null");
        try (InputStream is = new FileInputStream(path)) {
            return parseAndValidate(is);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Rules file not found: " + path, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read rules file: " + path, e);
        }
    }

    /**
     * Load rules from a classpath resource.
     *
     * @param resource classpath resource name; must not be {@code null}
     * @return parsed and validated rules configuration
     * @throws NullPointerException     if {@code resource} is {@code null}
     * @throws IllegalArgumentException if the resource does not exist
     * @throws IllegalStateException    if reading fails or rule validation fails
     */
    public static RulesConfig fromClasspath(String resource) {
        Objects.requireNonNull(resource, "Classpath resource name must not be null");
        InputStream is = RulesLoader.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + resource);
        }
        try (is) {
            return parseAndValidate(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + resource, e);
        }
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private static RulesConfig parseAndValidate(InputStream is) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new Constructor(RulesConfig.class, options));
        RulesConfig config = yaml.load(is);

        if (config == null || config.getRules() == null || config.getRules().isEmpty()) {
            LOG.warn("No detection rules defined in configuration");
            config = new RulesConfig();
        } else {
            // Fail fast if any rule is misconfigured
            config.validate();
        }

        LOG.info("Loaded {} detection rule(s)", config.getRules().size());
        return config;
    }
}
