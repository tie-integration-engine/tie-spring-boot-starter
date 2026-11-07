package org.tie.spring;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties of TIE.
 *
 * @param workingDirectory working directory of the executions whose context does not set one; when
 *                         unset, {@code java.io.tmpdir}
 */
@ConfigurationProperties("tie")
public record TieProperties(Path workingDirectory) {
}
