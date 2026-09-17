package io.github.jevonsnotes.jevokit.configuration.security.pathtraversal;

import lombok.Data;

@Data
public class PathTraversalMeta {
    boolean enabled;
    PathTraversalProperties.Mode mode;
}